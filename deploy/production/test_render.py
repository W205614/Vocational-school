import copy
from pathlib import Path
import re
import tempfile
import unittest
from urllib.parse import urlsplit

import yaml

import render


def fixture():
    # Synthetic identities only. No account, Secret, running endpoint or capacity
    # claim is represented by this renderer fixture.
    return {
        "namespace": "tianji-production", "commit": "a" * 40,
        "images": {name: "registry.acme.test/tianji/" + name + "@sha256:" + "b" * 64
                   for name in render.WORKLOADS + ["student", "admin", "media-worker", "java-agent"]},
        "replicas": 2,
        "mesh": {"trust_domain": "cluster.local", "ingress_namespace": "istio-system", "ingress_service_account": "istio-ingressgateway"},
        "public": {"student_host": "learn.acme.test", "admin_host": "admin.acme.test", "tls_secret": "tianji-public-tls",
                   "trusted_proxy_addresses": ["ingress-peers.istio-system.svc.cluster.local"]},
        "managed": {kind: {"host": kind + ".managed.acme.test", "cidrs": ["10.80.0.0/24"]}
                    for kind in ["mysql", "redis", "rabbitmq", "elasticsearch"]},
        "providers": {"region": "ap-shanghai", "cos_bucket": "private-courses-1234567890"},
        "provider_hosts": ["openapi.alipay.com", "private-courses-1234567890.cos.ap-shanghai.myqcloud.com", "dysmsapi.aliyuncs.com"],
        "privacy": {"policy_version": "2026-10-26", "policy_url": "https://learn.acme.test/privacy"},
        "media_worker": {"signatures_claim": "maintained-clamav-signatures"},
        "observability": {"namespace": "observability", "service_account": "prometheus", "collector_configmap": "otel-collector", "otlp_endpoint": "https://otlp.acme.test",
                          "alert_webhook": "https://alerts.acme.test/tianji"},
    }


class ProductionRenderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.resources, cls.secrets = render.render(fixture())

    def resources_of(self, kind):
        return [v for v in self.resources if v["kind"] == kind]

    def config(self, alias):
        key = alias + ".yml"
        return next(yaml.safe_load(v["data"][key]) for v in self.resources_of("ConfigMap") if key in v["data"])

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
            self.assertTrue(any(v["topologyKey"] == "topology.kubernetes.io/zone" and v["minDomains"] == 2
                                and v["whenUnsatisfiable"] == "DoNotSchedule" for v in pod["topologySpreadConstraints"]))
            self.assertTrue(all("hostPath" not in volume for volume in pod["volumes"]))
            for container in pod["containers"]:
                self.assertTrue(container["securityContext"]["readOnlyRootFilesystem"])
                self.assertEqual(["ALL"], container["securityContext"]["capabilities"]["drop"])

    def test_private_signing_material_is_only_mounted_by_identity(self):
        holders = [v["metadata"]["name"] for v in self.resources_of("Deployment")
                   if any(volume.get("secret", {}).get("secretName") == "identity-signing"
                          for volume in v["spec"]["template"]["spec"]["volumes"])]
        self.assertEqual(["app-identity"], holders)
        self.assertEqual(["signing.jks"], self.secrets["identity-signing"])

    def test_no_global_token_or_secret_values_are_rendered(self):
        serialized = yaml.safe_dump_all(self.resources)
        self.assertNotIn("TJ_INTERNAL_TOKEN", serialized)
        self.assertFalse(self.resources_of("Secret"))
        for deployment in self.resources_of("Deployment"):
            name = deployment["metadata"]["name"]
            if name.startswith("web-"):
                continue
            pod = deployment["spec"]["template"]["spec"]
            refs = {v["valueFrom"]["secretKeyRef"]["name"] for v in pod["containers"][0]["env"] if "valueFrom" in v}
            self.assertEqual({name.removeprefix("app-") + "-credentials"}, refs)

    def test_managed_upstreams_use_hostname_verified_tls(self):
        auth = self.config("auth")
        spring = auth["spring"]
        self.assertIn("sslMode=VERIFY_IDENTITY", spring["datasource"]["url"])
        self.assertTrue(spring["data"]["redis"]["ssl"]["enabled"])
        self.assertTrue(spring["rabbitmq"]["ssl"]["verify-hostname"])
        self.assertTrue(spring["rabbitmq"]["ssl"]["validate-server-certificate"])
        self.assertTrue(self.config("search")["spring"]["elasticsearch"]["uris"].startswith("https://"))
        self.assertEqual("db,redis", auth["tj"]["compact"]["readiness"]["required"])

    def test_static_workloads_disable_registration_health_probe_without_disabling_discovery(self):
        for alias in [*render.OWNERS, "gateway", "media-worker"]:
            discovery = self.config(alias)["spring"]["cloud"]["discovery"]
            self.assertFalse(discovery["client"]["health-indicator"]["enabled"])
            self.assertNotIn("enabled", discovery)
        for group in render.GROUPS:
            discovery = self.config(group)["spring"]["cloud"]["discovery"]
            self.assertFalse(discovery["client"]["health-indicator"]["enabled"])

    def test_real_channels_and_mfa_are_explicit(self):
        self.assertFalse(self.config("pay")["tj"]["pay"]["simulated"])
        self.assertFalse(self.config("message")["tj"]["sms"]["simulated"])
        self.assertTrue(self.config("auth")["tj"]["auth"]["cookie"]["require-secure"])
        self.assertTrue(self.config("auth")["tj"]["auth"]["mfa"]["required"])
        self.assertTrue(self.config("user")["tj"]["accounts"]["public"]["enabled"])
        self.assertEqual("provider", self.config("user")["tj"]["accounts"]["public"]["sms-delivery-mode"])

    def test_key_rotation_is_explicit_and_does_not_publish_secret_values(self):
        settings = fixture()
        settings["signing"] = {"active_alias": "next", "verification_aliases": ["previous"]}
        settings["mfa_rotation"] = True
        resources, secrets = render.render(settings)
        auth = next(yaml.safe_load(v["data"]["auth.yml"]) for v in resources
                    if v["kind"] == "ConfigMap" and "auth.yml" in v["data"])
        self.assertEqual("next", auth["encrypt"]["key-store"]["alias"])
        self.assertEqual("previous", auth["tj"]["auth"]["jwt"]["verification-aliases"])
        self.assertIn("TJ_AUTH_MFA_DECRYPTION_KEYS", secrets["identity-credentials"])
        self.assertIn("TJ_AUTH_MFA_LEGACY_KEY_ID", secrets["identity-credentials"])
        self.assertNotIn("TJ_AUTH_MFA_DECRYPTION_KEYS", self.secrets["identity-credentials"])
        for aliases in (["next"], ["previous", "previous"], ["bad,alias"]):
            with self.subTest(aliases=aliases):
                settings["signing"]["verification_aliases"] = aliases
                with self.assertRaises(render.ConfigurationError): render.render(settings)

    def test_cloud_media_worker_has_separate_permissions_and_bounded_storage(self):
        self.assertEqual("CLOUD_COS", self.config("media")["tj"]["platform"]["media"])
        exclusions = self.config("media")["tj"]["auth"]["resource"]["excludeLoginPaths"]
        self.assertIn("/cloud-content/**", exclusions)
        self.assertIn("/course-covers/*", exclusions)
        worker = self.config("media-worker")
        self.assertEqual("worker", worker["tj"]["cloud-media"]["role"])
        self.assertNotIn("signing-key", worker["tj"]["cloud-media"])
        self.assertNotIn("CLOUD_MEDIA_SIGNING_KEY", self.secrets["media-worker-credentials"])
        pod = next(v for v in self.resources_of("Deployment") if v["metadata"]["name"] == "media-worker")["spec"]["template"]["spec"]
        self.assertEqual("4Gi", next(v for v in pod["volumes"] if v["name"] == "work")["emptyDir"]["sizeLimit"])
        self.assertTrue(next(v for v in pod["volumes"] if v["name"] == "signatures")["persistentVolumeClaim"]["readOnly"])

    def test_cloud_media_gets_streaming_timeout_before_the_general_api_route(self):
        portals = self.resources_of("VirtualService")
        self.assertEqual({"student", "admin"}, {v["metadata"]["name"] for v in portals})
        for portal in portals:
            with self.subTest(portal=portal["metadata"]["name"]):
                self.assertEqual(3, len(portal["spec"]["http"]))
                stream, api, web = portal["spec"]["http"]
                self.assertEqual("cloud-media-stream", stream["name"])
                self.assertEqual("0s", stream["timeout"])
                self.assertEqual([{"method": {"exact": "GET"}, "uri": {
                    "regex": r"^/api/v2/services/media/cloud-content/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"}}], stream["match"])
                expected = [{"destination": {"host": "app-gateway", "port": {"number": 24310}}}]
                self.assertEqual(expected, stream["route"])
                self.assertEqual(expected, api["route"])
                self.assertEqual("30s", api["timeout"])
                self.assertEqual([{"uri": {"prefix": "/api/"}}], api["match"])
                self.assertEqual("web-" + portal["metadata"]["name"], web["route"][0]["destination"]["host"])
                # idleTimeout is not an HTTPRoute field. Stream idleness remains
                # bounded by the ingress HTTP connection manager, not an invalid
                # VirtualService field or a broad timeout exception.
                self.assertNotIn("idleTimeout", stream)
        policy = self.resources_of("DestinationRule")[0]["spec"]["trafficPolicy"]
        self.assertEqual(128, policy["connectionPool"]["tcp"]["maxConnections"])
        self.assertEqual(64, policy["connectionPool"]["http"]["http1MaxPendingRequests"])

    def test_only_exact_cloud_media_gets_receive_the_timeout_exception(self):
        media = "/api/v2/services/media/cloud-content/01234567-89ab-cdef-0123-456789abcdef"

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
            for target in [media, media + "?owner=1&course=2&expires=123&signature=synthetic"]:
                with self.subTest(portal=portal["metadata"]["name"], target=target):
                    self.assertEqual("0s", select(routes, "GET", target)["timeout"])
            for method, target in [("POST", media), ("PUT", media), ("HEAD", media),
                                   ("GET", media + "/extra"), ("GET", media + ".mp4"),
                                   ("GET", "/api/v2/services/media/cloud-content/"),
                                   ("GET", "/api/v2/services/media/cloud-content/not-an-id"),
                                   ("GET", "/api/v2/services/media/cloud-content/" + "-" * 36),
                                   ("GET", "/api/v2/services/media/cloud-content/" + "a" * 36),
                                   ("GET", "/api/v2/services/media/cloud-content/0123456-789ab-cdef-0123-456789abcdef"),
                                   ("GET", "/api/v2/services/media/cloud-upload"),
                                   ("GET", "/api/v2/services/media/cloud-content-elsewhere/" + "a" * 36),
                                   ("GET", "/api/v2/services/trade/orders")]:
                with self.subTest(portal=portal["metadata"]["name"], method=method, target=target):
                    self.assertEqual("30s", select(routes, method, target)["timeout"])

    def test_unconfigured_deployments_fail_closed(self):
        mutations = [lambda s: s["images"].update(identity="registry.acme.test/identity:latest"),
                     lambda s: s.update(replicas=1), lambda s: s["public"].update(student_host="learn.example.com"),
                     lambda s: s["managed"]["mysql"].update(cidrs=["0.0.0.0/0"]),
                     lambda s: s.update(provider_hosts=["*.myqcloud.com"]),
                     lambda s: s["public"].update(trusted_proxy_addresses=[])]
        for mutate in mutations:
            settings = fixture()
            mutate(settings)
            with self.assertRaises(render.ConfigurationError):
                render.render(settings)

    def test_mesh_authentication_and_telemetry_are_real_resource_requirements(self):
        self.assertEqual("STRICT", self.resources_of("PeerAuthentication")[0]["spec"]["mtls"]["mode"])
        self.assertEqual("REGISTRY_ONLY", self.resources_of("Sidecar")[0]["spec"]["outboundTrafficPolicy"]["mode"])
        self.assertEqual(8, len(self.resources_of("AuthorizationPolicy")))
        self.assertEqual(1, len(self.resources_of("Instrumentation")))
        self.assertEqual(8, len(self.resources_of("PodDisruptionBudget")))


if __name__ == "__main__":
    unittest.main()
