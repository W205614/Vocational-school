"""Check source-bound, artifact-backed external acceptance evidence.

No missing experiment becomes a pass. This checks submitted evidence consistency;
it does not independently attest the origin or truth of an uploaded experiment.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import math
from pathlib import Path
import sys

REQUIRED = {"application_failover", "zone_failover", "database_failover", "broker_failover", "restore",
            "media_revocation", "identity_mfa", "notifications", "external_alert", "telemetry_redaction", "capacity"}
OBSERVATIONS = {name: {"fault_event", "business_query"} for name in REQUIRED if name.endswith("failover")}
OBSERVATIONS.update({"restore": {"business_query"}, "media_revocation": {"http"}, "identity_mfa": {"http"},
    "notifications": {"provider_receipt"}, "external_alert": {"provider_receipt"}, "telemetry_redaction": {"telemetry_search"}, "capacity": {"load_sample"}})


def number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def measurement_report(value, suite, name, evidence, now):
    if value.get("schema") != "tianji-production-measurement-v1" or value.get("suite") != name or value.get("commit") != evidence.get("commit") or value.get("manifest_sha256") != evidence.get("manifest_sha256"):
        return False
    execution = value.get("execution", {})
    if execution.get("kind") != "runtime-experiment" or execution.get("environment") != "isolated-production-candidate" or execution.get("exit_code") != 0 or execution.get("required_skips") != 0:
        return False
    try:
        start, end = [dt.datetime.fromisoformat(execution[key]) for key in ["started_at", "completed_at"]]
        if start.tzinfo is None or end.tzinfo is None or not start <= end <= now or (now - start).total_seconds() > 7 * 86400:
            return False
    except (ValueError, KeyError, TypeError):
        return False
    observations = value.get("observations", [])
    if not observations or any(not isinstance(v, dict) or v.get("kind") not in {
        "http", "business_query", "provider_receipt", "fault_event", "load_sample", "telemetry_search"} or "value" not in v for v in observations):
        return False
    if not OBSERVATIONS[name].issubset({v["kind"] for v in observations}):
        return False
    measurements = value.get("measurements", {})
    keys = [key for key in suite if key not in {"status", "required_skips", "artifacts"}]
    return bool(measurements) and all(key in measurements and measurements[key] == suite[key] for key in keys)


def validate(evidence, root, commit, manifest_hash, now=None):
    errors = []
    root = Path(root).resolve()
    now = now or dt.datetime.now(dt.timezone.utc)
    if evidence.get("schema") != "tianji-production-acceptance-v1" or evidence.get("commit") != commit or evidence.get("manifest_sha256") != manifest_hash:
        errors.append("Evidence is not bound to the exact candidate source and manifest")
    if evidence.get("environment") != "isolated-production-candidate":
        errors.append("Acceptance must use an isolated candidate with synthetic accounts")
    try:
        observed = dt.datetime.fromisoformat(evidence["observed_at"])
        if observed.tzinfo is None or not 0 <= (now - observed).total_seconds() <= 7 * 86400:
            raise ValueError()
    except (ValueError, KeyError, TypeError):
        errors.append("Evidence timestamp is absent, future-dated or older than seven days")
    suites = evidence.get("suites", {})
    for name in sorted(REQUIRED):
        suite = suites.get(name, {})
        if suite.get("status") != "PASSED" or suite.get("required_skips", 0) != 0:
            errors.append(name + ": missing, failed or skipped")
            continue
        artifacts = suite.get("artifacts", [])
        bound_measurement = False
        if not artifacts:
            errors.append(name + ": no supporting artifact")
        for artifact in artifacts:
            try:
                original = root / artifact["path"]
                path = original.resolve()
                if path == root or not path.is_relative_to(root) or original.is_symlink() or not path.is_file():
                    raise ValueError()
                if hashlib.sha256(path.read_bytes()).hexdigest() != artifact["sha256"]:
                    raise ValueError()
                if path.suffix == ".json":
                    value = json.loads(path.read_text(encoding="utf8"))
                    if isinstance(value, dict) and measurement_report(value, suite, name, evidence, now):
                        bound_measurement = True
            except (OSError, KeyError, TypeError, ValueError):
                errors.append(name + ": supporting artifact unavailable or changed")
        if not bound_measurement:
            errors.append(name + ": no matching structured runtime measurement report")
        if name.endswith("failover") or name == "restore":
            if not number(suite.get("recovery_seconds")) or not 0 <= suite["recovery_seconds"] <= 1800:
                errors.append(name + ": RTO evidence exceeds 1800 seconds or is absent")
            if not number(suite.get("data_loss_seconds")) or not 0 <= suite["data_loss_seconds"] <= 300:
                errors.append(name + ": RPO evidence exceeds 300 seconds or is absent")
            if not number(suite.get("duplicate_business_effects")) or suite["duplicate_business_effects"] != 0 or suite.get("invariant_checks_passed") is not True:
                errors.append(name + ": business invariants are not proven")
        if name == "capacity":
            for key in ("active_users", "duration_seconds", "p95_ms", "p99_ms", "error_ratio", "admitted_active_users"):
                if not number(suite.get(key)) or suite[key] < 0:
                    errors.append(name + ": missing numeric " + key)
            if not suite.get("workload_model_sha256") or suite.get("budget_passed") is not True:
                errors.append(name + ": no workload binding or approved latency/error budget")
            if any(not number(suite.get(key)) or suite[key] < 1 for key in ["active_users", "duration_seconds"]):
                errors.append(name + ": no active workload was measured")
            if number(suite.get("error_ratio")) and suite["error_ratio"] > 1:
                errors.append(name + ": error ratio must be between zero and one")
            if number(suite.get("p95_ms")) and number(suite.get("p99_ms")) and suite["p95_ms"] > suite["p99_ms"]:
                errors.append(name + ": latency percentiles are inconsistent")
            if number(suite.get("admitted_active_users")) and number(suite.get("active_users")):
                if suite["admitted_active_users"] > suite["active_users"]:
                    errors.append(name + ": admission capacity exceeds measured capacity")
            if not all(number(suite.get("workflow_counts", {}).get(kind)) and suite["workflow_counts"][kind] > 0
                       for kind in ["purchase", "learning_heartbeat", "video", "exam_submit"]):
                errors.append(name + ": required business workflows were not measured")
        if name == "restore" and suite.get("objects_verified") is not True:
            errors.append(name + ": object data not verified")
        if name == "external_alert" and suite.get("delivered_to_external_receiver") is not True:
            errors.append(name + ": no actual external delivery evidence")
    return {"status": "PASSED" if not errors else "BLOCKED", "failures": errors,
            "monthly_slo_proven": False, "region_disaster_rto_committed": False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--evidence", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        release = json.loads((args.package / "release.json").read_text(encoding="utf8"))
        raw = (args.package / "resources.yaml").read_bytes()
        fingerprint = hashlib.sha256(raw).hexdigest()
        if fingerprint != release["manifestSha256"]:
            raise ValueError("Candidate manifest changed after rendering")
        evidence = json.loads(args.evidence.read_text(encoding="utf8"))
        result = validate(evidence, args.evidence.parent, release["commit"], fingerprint)
        print(json.dumps(result, indent=2))
        return 0 if result["status"] == "PASSED" else 2
    except (OSError, ValueError, KeyError, TypeError):
        print("Production acceptance BLOCKED: source-bound evidence is unavailable or malformed", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
