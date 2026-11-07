"""Source contracts and optional real promtool checks in an isolated container."""

import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

import yaml
import render
from test_render import fixture


class MonitoringRulesTest(unittest.TestCase):
    def test_gateway_histogram_buckets_and_recordings_use_real_http_metrics(self):
        settings = render.validate(fixture())
        metrics = render.module_config("gateway", settings)["management"]["metrics"][
            "distribution"
        ]
        self.assertIs(True, metrics["percentiles-histogram"]["http.server.requests"])
        self.assertEqual(
            "100ms,500ms,1s,3s,10s", metrics["slo"]["http.server.requests"]
        )
        rules = render.observability_config(settings)["alerts.yml"]["groups"][0][
            "rules"
        ]
        recordings = {
            value["record"]: value["expr"] for value in rules if "record" in value
        }
        requests = recordings["zhixuetang:gateway_http_requests:rate5m"]
        self.assertIn("http_server_requests_seconds_count", requests)
        self.assertIn('job="gateway"', requests)
        self.assertIn('namespace="zhixuetang-production"', requests)
        self.assertIn('uri!~"/actuator.*|/readyz"', requests)
        self.assertIn(
            "http_server_requests_seconds_bucket",
            recordings["zhixuetang:gateway_http_latency_p95:seconds"],
        )
        ratio = recordings["zhixuetang:gateway_http_success:ratio5m"]
        self.assertIn('status=~"2..|3..|5..|429"', ratio)
        self.assertIn("or on(namespace)", ratio)

    def test_sms_failure_unknown_and_staleness_alerts_are_namespace_scoped(self):
        settings = fixture()
        settings["namespace"] = "zhixuetang-monitoring-candidate"
        rules = render.observability_config(settings)["alerts.yml"]["groups"][0][
            "rules"
        ]
        alerts = {value["alert"]: value for value in rules if "alert" in value}
        for name, metric, duration in (
            ("ZhixuetangSmsOutcomeUnknown", "zxt_sms_unknown", "5m"),
            ("ZhixuetangSmsFailed", "zxt_sms_failed", "5m"),
            (
                "ZhixuetangSmsMetricsStale",
                "zxt_sms_metrics_last_success_timestamp_seconds",
                "2m",
            ),
            ("ZhixuetangPrivacyTaskFailed", "zxt_privacy_tasks_failed", "5m"),
            (
                "ZhixuetangPrivacyMetricsStale",
                "zxt_privacy_metrics_last_success_timestamp_seconds",
                "2m",
            ),
        ):
            with self.subTest(alert=name):
                self.assertIn(
                    metric + '{namespace="zhixuetang-monitoring-candidate"}',
                    alerts[name]["expr"],
                )
                self.assertEqual(duration, alerts[name]["for"])

    @unittest.skipUnless(
        os.environ.get("ZXT_PROMTOOL_IMAGE"),
        "requires an explicitly selected local immutable Prometheus image",
    )
    def test_native_promtool_parses_and_evaluates_actual_rendered_rules(self):
        image = os.environ["ZXT_PROMTOOL_IMAGE"]
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", image):
            raise ValueError(
                "Select an existing immutable local image ID; no pull or daily container is allowed"
            )
        rules = render.observability_config(fixture())["alerts.yml"]
        with tempfile.TemporaryDirectory(prefix="zhixuetang-promtool-") as temporary:
            directory = Path(temporary)
            (directory / "alerts.yml").write_text(
                yaml.safe_dump(rules, sort_keys=False), encoding="utf8"
            )
            (directory / "rule-tests.yml").write_text(
                (Path(__file__).parent / "monitoring-rule-tests.yml").read_text(
                    encoding="utf8"
                ),
                encoding="utf8",
            )
            base = [
                "docker",
                "run",
                "--rm",
                "--pull",
                "never",
                "--network",
                "none",
                "--read-only",
                "--tmpfs",
                "/tmp:rw,noexec,nosuid,size=64m",
                "--cap-drop",
                "ALL",
                "--security-opt",
                "no-new-privileges",
                "--mount",
                "type=bind,source=" + str(directory) + ",target=/checks,readonly",
                "--entrypoint",
                "/bin/promtool",
                image,
            ]
            for command in (
                ["check", "rules", "/checks/alerts.yml"],
                ["test", "rules", "/checks/rule-tests.yml"],
            ):
                result = subprocess.run(
                    base + command,
                    capture_output=True,
                    text=True,
                    timeout=60,
                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
                )
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
