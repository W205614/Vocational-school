import copy
from pathlib import Path
import tempfile
import unittest

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
