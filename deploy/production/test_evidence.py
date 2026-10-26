import copy
import datetime as dt
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import acceptance
import preflight
import render
import security_scan
from test_render import fixture


class EvidenceGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.evidence = {"schema": "tianji-production-acceptance-v1", "commit": "a" * 40, "manifest_sha256": "b" * 64,
            "environment": "isolated-production-candidate", "observed_at": dt.datetime.now(dt.timezone.utc).isoformat(), "suites": {}}
        for name in acceptance.REQUIRED:
            self.evidence["suites"][name] = {"status": "PASSED", "artifacts": [],
                "recovery_seconds": 90, "data_loss_seconds": 0, "duplicate_business_effects": 0, "invariant_checks_passed": True,
                "objects_verified": True, "delivered_to_external_receiver": True, "active_users": 20,
                "duration_seconds": 1800, "p95_ms": 300, "p99_ms": 800, "error_ratio": 0, "admitted_active_users": 10,
                "workload_model_sha256": "c" * 64, "budget_passed": True,
                "workflow_counts": {kind: 20 for kind in ["purchase", "learning_heartbeat", "video", "exam_submit"]}}
            measurements = {key: value for key, value in self.evidence["suites"][name].items() if key not in {"status", "artifacts"}}
            moment = self.evidence["observed_at"]
            report = {"schema": "tianji-production-measurement-v1", "suite": name, "commit": "a" * 40, "manifest_sha256": "b" * 64,
                "execution": {"kind": "runtime-experiment", "environment": "isolated-production-candidate", "exit_code": 0,
                    "required_skips": 0, "started_at": moment, "completed_at": moment}, "measurements": measurements,
                "observations": [{"kind": kind, "value": {"synthetic_unit_fixture": True}} for kind in acceptance.OBSERVATIONS[name]]}
            artifact = self.root / (name + ".json")
            artifact.write_text(json.dumps(report), encoding="utf8")
            self.evidence["suites"][name]["artifacts"] = [{"path": artifact.name, "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest()}]

    def evaluate(self, value=None):
        return acceptance.validate(value or self.evidence, self.root, "a" * 40, "b" * 64)

    def test_source_bound_complete_evidence_is_consistent_without_claiming_slo(self):
        result = self.evaluate()
        self.assertEqual("PASSED", result["status"])
        self.assertFalse(result["monthly_slo_proven"])
        self.assertFalse(result["region_disaster_rto_committed"])

    def test_missing_skipped_or_wrong_source_evidence_never_passes(self):
        for change in [lambda e: e.update(commit="d" * 40), lambda e: e["suites"].pop("restore"),
                       lambda e: e["suites"]["external_alert"].update(required_skips=1),
                       lambda e: e.update(observed_at="2020-01-01T00:00:00+00:00")]:
            evidence = copy.deepcopy(self.evidence)
            change(evidence)
            self.assertEqual("BLOCKED", self.evaluate(evidence)["status"])

    def test_changed_attachments_recovery_budget_and_invalid_numbers_block(self):
        for field, value in [("recovery_seconds", 1801), ("data_loss_seconds", True), ("duplicate_business_effects", 1)]:
            evidence = copy.deepcopy(self.evidence)
            evidence["suites"]["restore"][field] = value
            self.assertEqual("BLOCKED", self.evaluate(evidence)["status"])
        evidence = copy.deepcopy(self.evidence)
        evidence["suites"]["capacity"]["p99_ms"] = float("nan")
        self.assertEqual("BLOCKED", self.evaluate(evidence)["status"])
        (self.root / "restore.json").write_text("changed", encoding="utf8")
        self.assertEqual("BLOCKED", self.evaluate()["status"])

    def test_arbitrary_attachment_with_pass_booleans_is_not_runtime_evidence(self):
        artifact = self.root / "arbitrary.json"
        artifact.write_text('{"status":"PASSED"}', encoding="utf8")
        self.evidence["suites"]["restore"]["artifacts"] = [{"path": artifact.name, "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest()}]
        self.assertEqual("BLOCKED", self.evaluate()["status"])

    def test_scanner_redaction_does_not_emit_match_or_source_text(self):
        report = {"SchemaVersion": 2, "Results": [{"Target": "src/config.txt", "Secrets": [{
            "RuleID": "test-rule", "Severity": "HIGH", "StartLine": 7, "Match": "never-publish-this-value",
            "Code": {"Lines": [{"Content": "also-never-publish"}]}}]}]}
        summary = json.dumps(security_scan.summarize(report))
        self.assertNotIn("never-publish", summary)
        self.assertIn("test-rule", summary)
        with self.assertRaises(ValueError):
            security_scan.summarize({"Error": "scanner unavailable"})


class LivePreflightContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.resources, cls.secrets = render.render(fixture())

    def reader(self, with_sidecar=True, two_zones=True):
        deployments = [copy.deepcopy(v) for v in self.resources if v["kind"] == "Deployment"]
        pods = []
        for deployment in deployments:
            deployment["metadata"]["generation"] = 1
            deployment["status"] = {"observedGeneration": 1, "availableReplicas": 2, "updatedReplicas": 2}
            for index in range(2):
                name = deployment["metadata"]["name"]
                containers = [{"name": name}] + ([{"name": "istio-proxy"}] if with_sidecar else [])
                pods.append({"metadata": {"labels": {"app": name}}, "spec": {"containers": containers,
                    "initContainers": [{"name": "opentelemetry-auto-instrumentation-java"}], "nodeName": "node-" + str(index if two_zones else 0)},
                    "status": {"conditions": [{"type": "Ready", "status": "True"}]}})
        def read(context, *args):
            self.assertEqual("candidate-context", context)
            if args[1] == "deployments":
                return {"items": deployments}
            if args[1] == "nodes":
                return {"items": [{"metadata": {"name": "node-" + str(i), "labels": {"topology.kubernetes.io/zone": "zone-" + str(i)}}} for i in range(2)]}
            if args[1] == "pods":
                return {"items": pods}
            if args[1] == "peerauthentication":
                return {"spec": {"mtls": {"mode": "STRICT"}}}
            if args[1] == "secret":
                return {"data": {key: "not-a-real-credential" for key in self.secrets[args[2]]}}
            if args[1] == "configmap":
                config = render.observability_config(fixture())["otel-sanitize.yml"]
                config["service"] = {"pipelines": {"traces": {"receivers": ["otlp"], "processors": ["transform/tianji_redaction", "batch"], "exporters": ["otlphttp/backend"]}}}
                config["exporters"] = {"otlphttp/backend": {"endpoint": "https://backend.acme.test"}}
                import yaml
                return {"data": {"collector.yaml": yaml.safe_dump(config)}}
            self.fail("Unexpected API operation")
        return read

    def test_live_preflight_observes_actual_injection_and_zone_distribution(self):
        valid = preflight.inspect("candidate-context", "tianji-production", self.resources, self.secrets, self.reader())
        self.assertEqual("PASSED", valid["status"])
        self.assertFalse(valid["availability_proven"])
        for reader in [self.reader(with_sidecar=False), self.reader(two_zones=False)]:
            result = preflight.inspect("candidate-context", "tianji-production", self.resources, self.secrets, reader)
            self.assertEqual("FAILED", result["status"])


if __name__ == "__main__":
    unittest.main()
