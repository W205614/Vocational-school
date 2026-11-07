import copy
import json
from pathlib import Path
import re
import tempfile
import unittest
from urllib.parse import urlsplit

import yaml

import render


class StorageCompatibilityTest(unittest.TestCase):
    def test_new_brand_keeps_original_database_names(self):
        settings = render.validate(fixture())
        for alias in render.MODULES:
            config = render.module_config(alias, settings)
            self.assertNotIn("tj", config)
            self.assertIn("zxt", config)
            if alias not in {"data", "gateway"}:
                self.assertIn(
                    "/tj_" + alias + "?", config["spring"]["datasource"]["url"]
                )


def fixture():
    # Synthetic identities only. No account, Secret, running endpoint or capacity
    # claim is represented by this renderer fixture.
    return {
        "namespace": "zhixuetang-production",
        "commit": "a" * 40,
        "images": {
            name: "registry.acme.test/zhixuetang/" + name + "@sha256:" + "b" * 64
            for name in render.WORKLOADS
            + ["student", "admin", "media-worker", "java-agent"]
        },
        "replicas": 2,
        "mesh": {
            "trust_domain": "cluster.local",
            "ingress_namespace": "istio-system",
            "ingress_service_account": "istio-ingressgateway",
        },
        "public": {
            "student_host": "learn.acme.test",
            "admin_host": "admin.acme.test",
            "tls_secret": "zhixuetang-public-tls",
            "trusted_proxy_addresses": ["ingress-peers.istio-system.svc.cluster.local"],
        },
        "managed": {
            kind: {"host": kind + ".managed.acme.test", "cidrs": ["10.80.0.0/24"]}
            for kind in ["mysql", "redis", "rabbitmq", "elasticsearch"]
        },
        "providers": {
            "region": "ap-shanghai",
            "cos_bucket": "private-courses-1234567890",
        },
        "provider_hosts": [
            "openapi.alipay.com",
            "private-courses-1234567890.cos.ap-shanghai.myqcloud.com",
            "dysmsapi.aliyuncs.com",
        ],
        "privacy": {
            "policy_version": "2026-10-26",
            "policy_url": "https://learn.acme.test/privacy",
        },
        "media_worker": {"signatures_claim": "maintained-clamav-signatures"},
        "observability": {
            "namespace": "observability",
            "service_account": "prometheus",
            "collector_configmap": "otel-collector",
            "otlp_endpoint": "https://otlp.acme.test",
            "alert_webhook": "https://alerts.acme.test/zhixuetang",
        },
    }


class ProductionRenderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.resources, cls.secrets = render.render(fixture())

    def resources_of(self, kind):
        return [v for v in self.resources if v["kind"] == kind]

    def config(self, alias):
        key = alias + ".yml"
        return next(
            yaml.safe_load(v["data"][key])
            for v in self.resources_of("ConfigMap")
            if key in v["data"]
        )

    def test_cross_zone_rolling_workloads_have_bounded_security_and_no_host_data(self):
        deployments = self.resources_of("Deployment")
        self.assertEqual(8, len(deployments))
        for deployment in deployments:
            spec = deployment["spec"]
            self.assertGreaterEqual(spec["replicas"], 2)
            self.assertEqual(0, spec["strategy"]["rollingUpdate"]["maxUnavailable"])
            pod = spec["template"]["spec"]
            self.assertTrue(pod["securityContext"]["runAsNonRoot"])
            self.assertFalse(pod["automountServiceAccountToken"])
            self.assertTrue(
                any(
                    v["topologyKey"] == "topology.kubernetes.io/zone"
                    and v["minDomains"] == 2
                    and v["whenUnsatisfiable"] == "DoNotSchedule"
                    for v in pod["topologySpreadConstraints"]
                )
            )
            self.assertTrue(all("hostPath" not in volume for volume in pod["volumes"]))
            for container in pod["containers"]:
                self.assertTrue(container["securityContext"]["readOnlyRootFilesystem"])
                self.assertEqual(
                    ["ALL"], container["securityContext"]["capabilities"]["drop"]
                )

    def test_private_signing_material_is_only_mounted_by_identity(self):
        holders = [
            v["metadata"]["name"]
            for v in self.resources_of("Deployment")
            if any(
                volume.get("secret", {}).get("secretName") == "identity-signing"
                for volume in v["spec"]["template"]["spec"]["volumes"]
            )
        ]
        self.assertEqual(["app-identity"], holders)
        self.assertEqual(["signing.jks"], self.secrets["identity-signing"])

    def test_no_global_token_or_secret_values_are_rendered(self):
        serialized = yaml.safe_dump_all(self.resources)
        self.assertNotIn("ZXT_INTERNAL_TOKEN", serialized)
        self.assertFalse(self.resources_of("Secret"))
        for deployment in self.resources_of("Deployment"):
            name = deployment["metadata"]["name"]
            if name.startswith("web-"):
                continue
            pod = deployment["spec"]["template"]["spec"]
            refs = {
                v["valueFrom"]["secretKeyRef"]["name"]
                for v in pod["containers"][0]["env"]
                if "valueFrom" in v
            }
            self.assertEqual({name.removeprefix("app-") + "-credentials"}, refs)

    def test_managed_upstreams_use_hostname_verified_tls(self):
        auth = self.config("auth")
        spring = auth["spring"]
        self.assertIn("sslMode=VERIFY_IDENTITY", spring["datasource"]["url"])
        self.assertTrue(spring["data"]["redis"]["ssl"]["enabled"])
        self.assertTrue(spring["rabbitmq"]["ssl"]["verify-hostname"])
        self.assertTrue(spring["rabbitmq"]["ssl"]["validate-server-certificate"])
        self.assertTrue(
            self.config("search")["spring"]["elasticsearch"]["uris"].startswith(
                "https://"
            )
        )
        self.assertEqual("db,redis", auth["zxt"]["compact"]["readiness"]["required"])

    def test_static_workloads_disable_registration_health_probe_without_disabling_discovery(
        self,
    ):
        for alias in [*render.OWNERS, "gateway", "media-worker"]:
            discovery = self.config(alias)["spring"]["cloud"]["discovery"]
            self.assertFalse(discovery["client"]["health-indicator"]["enabled"])
            self.assertNotIn("enabled", discovery)
        for group in render.GROUPS:
            discovery = self.config(group)["spring"]["cloud"]["discovery"]
            self.assertFalse(discovery["client"]["health-indicator"]["enabled"])

    def test_real_channels_and_mfa_are_explicit(self):
        self.assertFalse(self.config("pay")["zxt"]["pay"]["simulated"])
        self.assertFalse(self.config("message")["zxt"]["sms"]["simulated"])
        self.assertTrue(self.config("auth")["zxt"]["auth"]["cookie"]["require-secure"])
        self.assertTrue(self.config("auth")["zxt"]["auth"]["mfa"]["required"])
        self.assertTrue(self.config("user")["zxt"]["accounts"]["public"]["enabled"])
        self.assertEqual(
            "provider",
            self.config("user")["zxt"]["accounts"]["public"]["sms-delivery-mode"],
        )

    def test_key_rotation_is_explicit_and_does_not_publish_secret_values(self):
        settings = fixture()
        settings["signing"] = {
            "active_alias": "next",
            "verification_aliases": ["previous"],
        }
        settings["mfa_rotation"] = True
        resources, secrets = render.render(settings)
        auth = next(
            yaml.safe_load(v["data"]["auth.yml"])
            for v in resources
            if v["kind"] == "ConfigMap" and "auth.yml" in v["data"]
        )
        self.assertEqual("next", auth["encrypt"]["key-store"]["alias"])
        self.assertEqual("previous", auth["zxt"]["auth"]["jwt"]["verification-aliases"])
        self.assertIn("ZXT_AUTH_MFA_DECRYPTION_KEYS", secrets["identity-credentials"])
        self.assertIn("ZXT_AUTH_MFA_LEGACY_KEY_ID", secrets["identity-credentials"])
        self.assertNotIn(
            "ZXT_AUTH_MFA_DECRYPTION_KEYS", self.secrets["identity-credentials"]
        )
        for aliases in (["next"], ["previous", "previous"], ["bad,alias"]):
            with self.subTest(aliases=aliases):
                settings["signing"]["verification_aliases"] = aliases
                with self.assertRaises(render.ConfigurationError):
                    render.render(settings)

    def test_cloud_media_worker_has_separate_permissions_and_bounded_storage(self):
        self.assertEqual("CLOUD_COS", self.config("media")["zxt"]["platform"]["media"])
        exclusions = self.config("media")["zxt"]["auth"]["resource"][
            "excludeLoginPaths"
        ]
        self.assertIn("/cloud-content/**", exclusions)
        self.assertIn("/course-covers/*", exclusions)
        worker = self.config("media-worker")
        self.assertEqual("worker", worker["zxt"]["cloud-media"]["role"])
        self.assertNotIn("signing-key", worker["zxt"]["cloud-media"])
        self.assertNotIn(
            "CLOUD_MEDIA_SIGNING_KEY", self.secrets["media-worker-credentials"]
        )
        pod = next(
            v
            for v in self.resources_of("Deployment")
            if v["metadata"]["name"] == "media-worker"
        )["spec"]["template"]["spec"]
        self.assertEqual(
            "4Gi",
            next(v for v in pod["volumes"] if v["name"] == "work")["emptyDir"][
                "sizeLimit"
            ],
        )
        self.assertTrue(
            next(v for v in pod["volumes"] if v["name"] == "signatures")[
                "persistentVolumeClaim"
            ]["readOnly"]
        )

    def test_cloud_media_gets_streaming_timeout_before_the_general_api_route(self):
        portals = self.resources_of("VirtualService")
        self.assertEqual({"student", "admin"}, {v["metadata"]["name"] for v in portals})
        for portal in portals:
            with self.subTest(portal=portal["metadata"]["name"]):
                self.assertEqual(5, len(portal["spec"]["http"]))
                stream, trial, export, api, web = portal["spec"]["http"]
                self.assertEqual("cloud-media-stream", stream["name"])
                self.assertEqual("0s", stream["timeout"])
                self.assertEqual(
                    [
                        {
                            "method": {"exact": "GET"},
                            "uri": {
                                "regex": r"^/api/v2/services/media/cloud-content/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
                            },
                        }
                    ],
                    stream["match"],
                )
                expected = [
                    {"destination": {"host": "app-gateway", "port": {"number": 24310}}}
                ]
                self.assertEqual(expected, stream["route"])
                self.assertEqual("trial-media-stream", trial["name"])
                self.assertEqual("private-export-stream", export["name"])
                for precise in (trial, export):
                    self.assertEqual("0s", precise["timeout"])
                    self.assertEqual(expected, precise["route"])
                    self.assertNotIn("idleTimeout", precise)
                self.assertEqual(expected, api["route"])
                self.assertEqual("30s", api["timeout"])
                self.assertEqual([{"uri": {"prefix": "/api/"}}], api["match"])
                self.assertEqual(
                    "web-" + portal["metadata"]["name"],
                    web["route"][0]["destination"]["host"],
                )
                # idleTimeout is not an HTTPRoute field. Stream idleness remains
                # bounded by the ingress HTTP connection manager, not an invalid
                # VirtualService field or a broad timeout exception.
                self.assertNotIn("idleTimeout", stream)
        policy = self.resources_of("DestinationRule")[0]["spec"]["trafficPolicy"]
        self.assertEqual(128, policy["connectionPool"]["tcp"]["maxConnections"])
        self.assertEqual(
            64, policy["connectionPool"]["http"]["http1MaxPendingRequests"]
        )

    def test_only_precise_stream_endpoints_receive_the_timeout_exception(self):
        media = (
            "/api/v2/services/media/cloud-content/01234567-89ab-cdef-0123-456789abcdef"
        )

        def select(routes, method, target):
            # URI matching is over the path; signed query parameters do not
            # change which Istio route is selected.
            path = urlsplit(target).path
            for route in routes:
                for match in route.get("match", [{}]):
                    if "method" in match and match["method"].get("exact") != method:
                        continue
                    uri = match.get("uri", {})
                    if "regex" in uri and re.fullmatch(uri["regex"], path) is None:
                        continue
                    if "prefix" in uri and not path.startswith(uri["prefix"]):
                        continue
                    return route
            self.fail("No route selected")

        for portal in self.resources_of("VirtualService"):
            routes = portal["spec"]["http"]
            for target in [
                media,
                media + "?owner=1&course=2&expires=123&signature=synthetic",
            ]:
                with self.subTest(portal=portal["metadata"]["name"], target=target):
                    self.assertEqual("0s", select(routes, "GET", target)["timeout"])
            for method, target in [
                ("POST", media),
                ("PUT", media),
                ("HEAD", media),
                ("GET", media + "/extra"),
                ("GET", media + ".mp4"),
                ("GET", "/api/v2/services/media/cloud-content/"),
                ("GET", "/api/v2/services/media/cloud-content/not-an-id"),
                ("GET", "/api/v2/services/media/cloud-content/" + "-" * 36),
                ("GET", "/api/v2/services/media/cloud-content/" + "a" * 36),
                (
                    "GET",
                    "/api/v2/services/media/cloud-content/0123456-789ab-cdef-0123-456789abcdef",
                ),
                ("GET", "/api/v2/services/media/cloud-upload"),
                ("GET", "/api/v2/services/media/cloud-content-elsewhere/" + "a" * 36),
                ("GET", "/api/v2/services/trade/orders"),
            ]:
                with self.subTest(
                    portal=portal["metadata"]["name"], method=method, target=target
                ):
                    self.assertEqual("30s", select(routes, method, target)["timeout"])
            trial = "/api/v2/services/media/trial-content/" + "b" * 64
            export_id = "01234567-89ab-cdef-0123-456789abcdef"
            for method, target, route_name in [
                ("GET", trial, "trial-media-stream"),
                (
                    "GET",
                    trial + "?section=7&expires=123&signature=synthetic",
                    "trial-media-stream",
                ),
                (
                    "POST",
                    "/api/v2/services/user/account-privacy/tasks/"
                    + export_id
                    + "/download",
                    "private-export-stream",
                ),
                (
                    "POST",
                    "/api/v2/admin/user/account-privacy/tasks/"
                    + export_id
                    + "/download",
                    "private-export-stream",
                ),
            ]:
                with self.subTest(
                    portal=portal["metadata"]["name"], method=method, target=target
                ):
                    selected = select(routes, method, target)
                    self.assertEqual(route_name, selected["name"])
                    self.assertEqual("0s", selected["timeout"])
            for method, target in [
                ("POST", trial),
                ("HEAD", trial),
                ("PUT", trial),
                ("GET", trial + "/extra"),
                ("GET", trial + ".mp4"),
                ("GET", "/api/v2/services/media/trial-content/" + "b" * 63),
                ("GET", "/api/v2/services/media/trial-content/" + "B" * 64),
                ("GET", "/api/v2/services/media/trial-content/"),
                (
                    "GET",
                    "/api/v2/services/user/account-privacy/tasks/"
                    + export_id
                    + "/download",
                ),
                (
                    "PUT",
                    "/api/v2/services/user/account-privacy/tasks/"
                    + export_id
                    + "/download",
                ),
                (
                    "POST",
                    "/api/v2/services/user/account-privacy/tasks/"
                    + "a" * 36
                    + "/download",
                ),
                (
                    "POST",
                    "/api/v2/services/user/account-privacy/tasks/"
                    + export_id
                    + "/download/extra",
                ),
                (
                    "POST",
                    "/api/v2/services/user/account-privacy/tasks/"
                    + export_id
                    + "/approve",
                ),
                ("POST", "/api/v2/services/user/account-privacy/tasks"),
            ]:
                with self.subTest(
                    portal=portal["metadata"]["name"], method=method, target=target
                ):
                    self.assertEqual("30s", select(routes, method, target)["timeout"])

    def test_unconfigured_deployments_fail_closed(self):
        mutations = [
            lambda s: s["images"].update(identity="registry.acme.test/identity:latest"),
            lambda s: s.update(replicas=1),
            lambda s: s["public"].update(student_host="learn.example.com"),
            lambda s: s["managed"]["mysql"].update(cidrs=["0.0.0.0/0"]),
            lambda s: s.update(provider_hosts=["*.myqcloud.com"]),
            lambda s: s["public"].update(trusted_proxy_addresses=[]),
        ]
        for mutate in mutations:
            settings = fixture()
            mutate(settings)
            with self.assertRaises(render.ConfigurationError):
                render.render(settings)

    def test_mesh_authentication_and_telemetry_are_real_resource_requirements(self):
        self.assertEqual(
            "STRICT", self.resources_of("PeerAuthentication")[0]["spec"]["mtls"]["mode"]
        )
        self.assertEqual(
            "REGISTRY_ONLY",
            self.resources_of("Sidecar")[0]["spec"]["outboundTrafficPolicy"]["mode"],
        )
        self.assertEqual(8, len(self.resources_of("AuthorizationPolicy")))
        self.assertEqual(1, len(self.resources_of("Instrumentation")))
        self.assertEqual(8, len(self.resources_of("PodDisruptionBudget")))

    def test_privacy_defaults_retain_data_and_mount_private_identity_configuration(
        self,
    ):
        configmap = next(
            value
            for value in self.resources_of("ConfigMap")
            if "user.yml" in value.get("data", {})
        )
        retention = json.loads(configmap["data"]["privacy-retention.json"])
        self.assertEqual({"version": "UNCONFIGURED", "rules": {}}, retention)
        privacy = self.config("user")["zxt"]["privacy"]
        self.assertEqual("COS", privacy["archive"]["provider"])
        self.assertEqual(
            fixture()["providers"]["cos_bucket"], privacy["archive"]["bucket"]
        )
        self.assertEqual("${PRIVACY_COS_SECRET_ID}", privacy["archive"]["secret-id"])
        self.assertEqual("${PRIVACY_COS_SECRET_KEY}", privacy["archive"]["secret-key"])
        self.assertEqual(
            "/run/compact/configs/privacy-retention.json",
            privacy["retention-policy-file"],
        )
        self.assertTrue(
            {"PRIVACY_COS_SECRET_ID", "PRIVACY_COS_SECRET_KEY"}
            <= set(self.secrets["identity-credentials"])
        )
        for group in ("commerce", "education", "support", "gateway"):
            self.assertNotIn(
                "PRIVACY_COS_SECRET_KEY", self.secrets[group + "-credentials"]
            )
        identity = next(
            value
            for value in self.resources_of("Deployment")
            if value["metadata"]["name"] == "app-identity"
        )
        pod = identity["spec"]["template"]["spec"]
        mounts = pod["containers"][0]["volumeMounts"]
        self.assertTrue(
            any(
                value["name"] == "config"
                and value["readOnly"]
                and value["mountPath"] == "/run/compact/configs"
                for value in mounts
            )
        )
        self.assertTrue(
            any(
                value.get("configMap", {}).get("name") == configmap["metadata"]["name"]
                for value in pod["volumes"]
            )
        )
        self.assertTrue(
            any(
                value["name"] == "tmp" and value["emptyDir"].get("sizeLimit")
                for value in pod["volumes"]
            )
        )

    def test_privacy_cleanup_requires_an_executable_dataset_explicit_days_and_version(
        self,
    ):
        settings = fixture()
        settings["privacy"]["retention"] = {
            "version": "reviewed-v1",
            "rules": {"user.details": {"days": 30, "action": "ANONYMIZE"}},
        }
        resources, _ = render.render(settings)
        configmap = next(
            value
            for value in resources
            if value["kind"] == "ConfigMap"
            and "privacy-retention.json" in value.get("data", {})
        )
        self.assertEqual(
            settings["privacy"]["retention"],
            json.loads(configmap["data"]["privacy-retention.json"]),
        )
        for name, rule in (
            ("learnng.notes", {"days": 30, "action": "ANONYMIZE"}),
            ("course.teaching", {"days": 30, "action": "ANONYMIZE"}),
            ("learning.entitlements", {"days": 30, "action": "ANONYMIZE"}),
            ("data.aggregate", {"days": 30, "action": "ANONYMIZE"}),
            ("user.details", {"days": True, "action": "ANONYMIZE"}),
            ("user.details", {"days": -1, "action": "ANONYMIZE"}),
            ("user.details", {"days": 30, "action": "DELETE"}),
        ):
            value = fixture()
            value["privacy"]["retention"] = {
                "version": "reviewed-v1",
                "rules": {name: rule},
            }
            with self.subTest(dataset=name, rule=rule), self.assertRaises(
                render.ConfigurationError
            ):
                render.render(value)

    def test_backup_proof_alerts_are_scoped_to_this_namespace(self):
        settings = fixture()
        settings["namespace"] = "zhixuetang-candidate-two"
        rules = render.observability_config(settings)["alerts.yml"]["groups"][0][
            "rules"
        ]
        by_name = {rule["alert"]: rule for rule in rules if "alert" in rule}
        selector = 'zhixuetang_backup_last_verified_timestamp_seconds{namespace="zhixuetang-candidate-two"}'
        self.assertEqual(
            "absent(" + selector + ")", by_name["ZhixuetangBackupProofMissing"]["expr"]
        )
        self.assertEqual(
            "time() - " + selector + " > 86400",
            by_name["ZhixuetangBackupProofExpired"]["expr"],
        )


if __name__ == "__main__":
    unittest.main()
