import copy
import datetime as dt
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading
from urllib import request

import backup_proof
import check_integrations
import integrations
import runtime_checks
import search
import collect_evidence
from test_render import fixture


def spec():
    return {
        "namespace": "zhixuetang-production",
        "operator_id": 123,
        "alipay": {"name": "支付宝", "priority": 1, "icon": "/alipay.svg"},
        "sms": {
            "sign_name": "测试学堂",
            "template_code": "SMS_12345",
            "content": "验证码${code}，请勿泄露。",
        },
    }


class IntegrationConfigurationTest(unittest.TestCase):
    def test_template_matches_the_real_verification_parameter_contract(self):
        self.assertEqual(spec(), integrations.validate(spec()))
        for invalid in (
            "没有参数",
            "${password}",
            "${code}${code}",
            "${code}${unused}",
        ):
            value = spec()
            value["sms"]["content"] = invalid
            with self.assertRaises(integrations.IntegrationError):
                integrations.validate(value)

    def test_placeholders_non_bmp_and_non_admin_identity_are_rejected(self):
        for change in (
            lambda v: v.update(operator_id=True),
            lambda v: v["sms"].update(sign_name="REPLACE_WITH_SIGN"),
            lambda v: v["sms"].update(sign_name="\U0001f600"),
        ):
            value = spec()
            change(value)
            with self.assertRaises(integrations.IntegrationError):
                integrations.validate(value)

    def test_read_only_connectivity_failure_cannot_become_supplier_acceptance(self):
        settings = fixture()
        binding = (settings["commit"], "b" * 64, settings["namespace"], set())
        result = check_integrations.inspect(settings, spec(), binding)
        self.assertEqual("CONFIGURATION_VALID", result["status"])
        self.assertEqual("NOT_RUN", result["supplier_acceptance"])
        observed = []

        def connector(host, port):
            observed.append((host, port))
            if host == "dysmsapi.aliyuncs.com":
                raise OSError("synthetic connection failure")

        result = check_integrations.inspect(
            settings, spec(), binding, connect=True, connector=connector
        )
        self.assertEqual("FAILED", result["status"])
        self.assertEqual("NOT_RUN", result["supplier_acceptance"])
        self.assertTrue(observed)

    def test_search_existing_mapping_is_checked_without_silent_field_downgrade(self):
        actual = search.mapping()
        search.check_properties(actual)
        actual["metadataVersion"] = {"type": "integer"}
        with self.assertRaises(ValueError):
            search.check_properties(actual)


class RuntimeChecksTest(unittest.TestCase):
    def scenario(self):
        return {
            "schema": "zhixuetang-runtime-checks-v1",
            "suite": "notifications",
            "namespace": "zhixuetang-production",
            "environment": "isolated-production-candidate",
            "test_identity": "isolated_student_01",
            "test_recipient": "13900000000",
            "base_url": "https://learn.acme.test",
            "steps": [
                {
                    "name": "receipt",
                    "check": "supplier_delivered",
                    "path": "/api/v2/admin/deliveries/sms?taskId=fixture",
                    "method": "GET",
                    "auth": "admin",
                    "expected_status": 200,
                    "attempts": 2,
                    "observation": "provider_receipt",
                    "assertions": [
                        {"path": "/0/recipients/0/state", "equals": "DELIVERED"}
                    ],
                }
            ],
        }

    def test_actual_receipt_state_drives_the_result_and_private_body_is_not_persisted(
        self,
    ):
        scenario = self.scenario()
        runtime_checks.validate(scenario, "zhixuetang-production", {"learn.acme.test"})
        statuses = iter(("ACCEPTED", "DELIVERED"))

        def response(url, method, headers, body):
            self.assertEqual("Bearer private-token", headers["Authorization"])
            return (
                200,
                json.dumps(
                    [
                        {
                            "phone": "private-phone",
                            "recipients": [
                                {"state": next(statuses), "secret": "do-not-export"}
                            ],
                        }
                    ]
                ).encode(),
            )

        report = runtime_checks.execute(
            scenario,
            {"admin": "private-token"},
            ("a" * 40, "b" * 64),
            caller=response,
            pause=lambda seconds: None,
        )
        self.assertEqual(0, report["execution"]["exit_code"])
        self.assertEqual(2, len(report["observations"]))
        exported = json.dumps(report)
        for secret in (
            "private-token",
            "private-phone",
            "do-not-export",
            "13900000000",
        ):
            self.assertNotIn(secret, exported)

    def test_submission_simulation_and_error_status_never_prove_delivery(self):
        for state in ("ACCEPTED", "SIMULATED", "FAILED", "UNKNOWN"):
            report = runtime_checks.execute(
                self.scenario(),
                {"admin": "token"},
                ("a" * 40, "b" * 64),
                caller=lambda *args: (
                    200,
                    json.dumps([{"recipients": [{"state": state}]}]).encode(),
                ),
                pause=lambda seconds: None,
            )
            self.assertEqual(1, report["execution"]["exit_code"])

    def test_writes_cross_origin_incomplete_suite_and_retrying_writes_are_rejected(
        self,
    ):
        for change in (
            lambda v: v.update(base_url="https://another.acme.test"),
            lambda v: v["steps"][0].update(method="POST"),
            lambda v: v["steps"][0].update(check="invented_success"),
            lambda v: v.update(test_recipient=""),
        ):
            value = self.scenario()
            change(value)
            with self.assertRaises(ValueError):
                runtime_checks.validate(value, "zhixuetang-production", {"learn.acme.test"})

    def test_missing_response_field_and_network_failure_are_failed_not_skipped(self):
        for caller in (
            lambda *args: (200, b"{}"),
            lambda *args: (_ for _ in ()).throw(OSError("connection lost")),
        ):
            report = runtime_checks.execute(
                self.scenario(),
                {"admin": "token"},
                ("a" * 40, "b" * 64),
                caller=caller,
                pause=lambda seconds: None,
            )
            self.assertEqual(1, report["execution"]["exit_code"])
            self.assertEqual(0, report["execution"]["required_skips"])

    def test_collection_keeps_missing_suites_blocked_and_failure_cannot_be_promoted(
        self,
    ):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)
            report = runtime_checks.execute(
                self.scenario(),
                {"admin": "token"},
                ("a" * 40, "b" * 64),
                caller=lambda *args: (200, b'[{"recipients":[{"state":"DELIVERED"}]}]'),
                pause=lambda seconds: None,
            )
            (path / "notifications.measurement.json").write_text(json.dumps(report))
            evidence = collect_evidence.collect(path, ("a" * 40, "b" * 64))
            self.assertEqual("PASSED", evidence["suites"]["notifications"]["status"])
            import acceptance

            self.assertEqual(
                "BLOCKED",
                acceptance.validate(evidence, path, "a" * 40, "b" * 64)["status"],
            )
            report["execution"]["exit_code"] = 1
            (path / "notifications.measurement.json").write_text(json.dumps(report))
            evidence = collect_evidence.collect(path, ("a" * 40, "b" * 64))
            self.assertNotEqual("PASSED", evidence["suites"]["notifications"]["status"])

    def test_read_polling_has_an_explicit_upper_bound_and_writes_never_retry(self):
        scenario = self.scenario()
        scenario["steps"][0]["attempts"] = 300
        runtime_checks.validate(scenario, "zhixuetang-production", {"learn.acme.test"})
        scenario["steps"][0]["attempts"] = 301
        with self.assertRaises(ValueError):
            runtime_checks.validate(scenario, "zhixuetang-production", {"learn.acme.test"})
        scenario["steps"][0].update(method="POST", attempts=2)
        with self.assertRaises(ValueError):
            runtime_checks.validate(
                scenario, "zhixuetang-production", {"learn.acme.test"}, allow_writes=True
            )


class RuntimeBinaryChecksTest(unittest.TestCase):
    def test_binary_download_stream_hash_and_protected_body_bindings(self):
        scenario = {
            "schema": "zhixuetang-runtime-checks-v1",
            "suite": "media_revocation",
            "namespace": "zhixuetang-production",
            "environment": "isolated-production-candidate",
            "test_identity": "synthetic media owner",
            "base_url": "https://learn.acme.test",
            "steps": [
                {
                    "name": "download",
                    "check": "revoked",
                    "method": "POST",
                    "path": "/api/v2/services/user/account-privacy/tasks/synthetic/download",
                    "auth": "student",
                    "expected_status": 200,
                    "body": {"password": "${credentials.password}"},
                    "expected_sha256": "${credentials.expected_hash}",
                }
            ],
        }
        raw = b"synthetic private binary artifact"
        expected = hashlib.sha256(raw).hexdigest()
        runtime_checks.validate(
            scenario, "zhixuetang-production", {"learn.acme.test"}, allow_writes=True
        )

        def caller(url, method, headers, body):
            self.assertEqual({"password": "private-body-password"}, body)
            self.assertEqual("application/octet-stream", headers["Accept"])
            return runtime_checks.HttpResult(200, b"", expected, len(raw))

        credentials = {
            "student": "private-token",
            "password": "private-body-password",
            "expected_hash": expected,
        }
        report = runtime_checks.execute(
            scenario, credentials, ("a" * 40, "b" * 64), caller=caller
        )
        self.assertEqual(0, report["execution"]["exit_code"])
        self.assertNotIn("private-body-password", json.dumps(report))
        self.assertNotIn("private-token", json.dumps(report))
        bad = dict(credentials, expected_hash="0" * 64)
        self.assertEqual(
            1,
            runtime_checks.execute(scenario, bad, ("a" * 40, "b" * 64), caller=caller)[
                "execution"
            ]["exit_code"],
        )

    def test_real_http_binary_stream_and_redirect_refusal(self):
        payload = b"synthetic" * 400000
        paths = []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                paths.append(self.path)
                if self.path.endswith("redirect"):
                    self.send_response(302)
                    self.send_header("Location", "/api/v2/stolen")
                    self.end_headers()
                    return
                self.send_response(200)
                self.send_header("Content-Type", "application/zip")
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, *args):
                pass

        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                base = "http://127.0.0.1:" + str(server.server_port)
                opener = request.build_opener(runtime_checks.NoRedirect())
                binary = runtime_checks.http(
                    opener,
                    base + "/api/v2/binary",
                    "GET",
                    {"Accept": "application/octet-stream"},
                    None,
                )
                self.assertEqual(len(payload), binary.size)
                self.assertEqual(b"", binary.body)
                self.assertEqual(hashlib.sha256(payload).hexdigest(), binary.sha256)
                redirect = runtime_checks.http(
                    opener,
                    base + "/api/v2/redirect",
                    "GET",
                    {"Accept": "application/json"},
                    None,
                )
                self.assertEqual(302, redirect.status)
                self.assertNotIn("/api/v2/stolen", paths)
            finally:
                server.shutdown()
                worker.join()

    def test_all_supported_suites_have_configurable_actual_api_templates(self):
        base = Path(__file__).parent
        for name in (
            "payments",
            "privacy",
            "media-revocation",
            "identity-mfa",
            "notifications",
        ):
            scenario = json.loads(
                (base / ("runtime-" + name + ".example.json")).read_text(
                    encoding="utf8"
                )
            )
            scenario["base_url"] = "https://learn.acme.test"
            if name == "notifications":
                scenario["test_recipient"] = "13900000000"
            runtime_checks.validate(
                scenario, "zhixuetang-production", {"learn.acme.test"}, allow_writes=True
            )

    def test_expired_original_ticket_cannot_prove_revocation(self):
        scenario = {
            "suite": "media_revocation",
            "base_url": "https://learn.acme.test",
            "steps": [
                {
                    "name": "old_ticket",
                    "check": "revoked",
                    "path": "/api/v2/services/media/cloud-content/synthetic",
                    "expected_status": 403,
                    "require_unexpired": 1,
                }
            ],
        }
        report = runtime_checks.execute(
            scenario,
            {},
            ("a" * 40, "b" * 64),
            caller=lambda *args: self.fail(
                "Expired ticket must not issue a proof request"
            ),
        )
        self.assertEqual(1, report["execution"]["exit_code"])


class BackupProofTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.folder = Path(self.temporary.name)
        self.attachment = self.folder / "provider.json"
        self.attachment.write_text('{"synthetic_provider_record":true}')
        self.binding = ("a" * 40, "b" * 64, "zhixuetang-production")
        self.proof = {
            "schema": "zhixuetang-managed-backup-proof-v1",
            "status": "VERIFIED",
            "commit": "a" * 40,
            "manifest_sha256": "b" * 64,
            "namespace": "zhixuetang-production",
            "provider": "synthetic-test",
            "resource_id": "synthetic-db",
            "backup_id": "synthetic-backup",
            "completed_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "verified_components": ["database", "objects", "recovery_keys"],
            "artifacts": [
                {
                    "path": "provider.json",
                    "sha256": hashlib.sha256(self.attachment.read_bytes()).hexdigest(),
                }
            ],
        }

    def test_valid_evidence_produces_the_scoped_metric_without_exposing_resource_details(
        self,
    ):
        timestamp = backup_proof.validate(self.proof, self.folder, self.binding)
        target = self.folder / "zhixuetang.prom"
        backup_proof.publish(target, timestamp, self.proof["namespace"])
        content = target.read_text()
        self.assertIn(
            'zhixuetang_backup_last_verified_timestamp_seconds{namespace="zhixuetang-production"}',
            content,
        )
        self.assertNotIn("synthetic-db", content)

    def test_expired_failed_wrong_source_or_changed_evidence_does_not_refresh_metric(
        self,
    ):
        for change in (
            lambda v: v.update(completed_at="2020-01-01T00:00:00+00:00"),
            lambda v: v.update(status="FAILED"),
            lambda v: v.update(commit="c" * 40),
            lambda v: v.update(verified_components=["database"]),
            lambda v: v["artifacts"][0].update(path="../outside.json"),
        ):
            value = copy.deepcopy(self.proof)
            change(value)
            with self.assertRaises(ValueError):
                backup_proof.validate(value, self.folder, self.binding)
        self.attachment.write_text("changed")
        with self.assertRaises(ValueError):
            backup_proof.validate(self.proof, self.folder, self.binding)


if __name__ == "__main__":
    unittest.main()
