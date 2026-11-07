"""Collect actual measurement files into the existing production acceptance gate."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import sys

import acceptance
import database
from runtime_checks import candidate


def collect(root, binding):
    root = Path(root).resolve()
    evidence = {
        "schema": "tianji-production-acceptance-v1",
        "commit": binding[0],
        "manifest_sha256": binding[1],
        "environment": "isolated-production-candidate",
        "observed_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "suites": {},
    }
    for path in sorted(root.glob("*.measurement.json")):
        if path.is_symlink():
            raise ValueError("Measurement symlinks are forbidden")
        report = json.loads(path.read_text(encoding="utf8"))
        name = report.get("suite")
        if name not in acceptance.REQUIRED or name in evidence["suites"]:
            raise ValueError("Unknown or duplicate measurement suite")
        execution = report.get("execution", {})
        suite = {
            **report.get("measurements", {}),
            "required_skips": execution.get("required_skips", 1),
            "status": "PASSED" if execution.get("exit_code") == 0 else "FAILED",
            "artifacts": [
                {
                    "path": path.name,
                    "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                }
            ],
        }
        if not acceptance.measurement_report(
            report, suite, name, evidence, dt.datetime.now(dt.timezone.utc)
        ):
            suite["status"] = "BLOCKED"
        evidence["suites"][name] = suite
    return evidence


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", required=True, type=Path)
    parser.add_argument("--reports", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(argv)
    if args.output.parent.resolve() != args.reports.resolve():
        raise ValueError("Acceptance output must be in the reports directory")
    binding = candidate(args.package)
    evidence = collect(args.reports, binding)
    result = acceptance.validate(evidence, args.reports, binding[0], binding[1])
    database.new_json(args.output, evidence)
    print(
        "Production acceptance "
        + result["status"]
        + "; "
        + str(len(result["failures"]))
        + " unresolved checks"
    )
    return 0 if result["status"] == "PASSED" else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError, database.DatabaseError):
        print(
            "Evidence collection BLOCKED: invalid measurement inputs; no passing report produced",
            file=sys.stderr,
        )
        raise SystemExit(2)
