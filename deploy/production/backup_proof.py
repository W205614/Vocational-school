"""Publish verified managed-backup evidence to a Prometheus textfile collector.

The input is an operator/provider report, not a backup command. Missing, changed,
expired or failed evidence never refreshes the success timestamp. A textfile
exporter must be installed in the protected monitoring environment separately.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import sys

import database
from runtime_checks import candidate


def validate(proof, root, binding, now=None):
    now = now or dt.datetime.now(dt.timezone.utc)
    if (
        proof.get("schema") != "tianji-managed-backup-proof-v1"
        or proof.get("status") != "VERIFIED"
        or proof.get("namespace") != binding[2]
        or proof.get("commit") != binding[0]
        or proof.get("manifest_sha256") != binding[1]
    ):
        raise ValueError("Backup proof is not bound to this candidate")
    moment = dt.datetime.fromisoformat(proof["completed_at"])
    if moment.tzinfo is None or not 0 <= (now - moment).total_seconds() <= 86400:
        raise ValueError("Verified backup proof must be within the last 24 hours")
    if any(
        not isinstance(proof.get(key), str) or not proof[key].strip()
        for key in ("provider", "backup_id", "resource_id")
    ):
        raise ValueError("Provider, backup and resource identities are required")
    required = {"database", "objects", "recovery_keys"}
    if set(proof.get("verified_components", [])) != required:
        raise ValueError(
            "Database, private objects and recovery keys must all be verified"
        )
    artifacts = proof.get("artifacts", [])
    if not artifacts:
        raise ValueError("Actual provider/verification artifacts are required")
    root = Path(root).resolve()
    for item in artifacts:
        original = root / item["path"]
        path = original.resolve()
        if (
            not path.is_relative_to(root)
            or original.is_symlink()
            or not path.is_file()
            or not re.fullmatch(r"[0-9a-f]{64}", item["sha256"])
            or hashlib.sha256(path.read_bytes()).hexdigest() != item["sha256"]
        ):
            raise ValueError("Backup proof attachment is unavailable or changed")
    return moment.timestamp()


def publish(target, timestamp, namespace):
    target = Path(target)
    if target.suffix != ".prom" or not target.parent.is_dir() or target.is_symlink():
        raise ValueError(
            "Use a regular .prom file in the existing protected textfile directory"
        )
    if not re.fullmatch(r"[a-z][a-z0-9-]{1,50}", namespace):
        raise ValueError("Invalid monitoring namespace")
    temporary = target.with_name(target.name + ".pending")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="ascii") as stream:
        stream.write(
            "# HELP tianji_backup_last_verified_timestamp_seconds Timestamp of the last artifact-backed managed backup verification.\n"
        )
        stream.write("# TYPE tianji_backup_last_verified_timestamp_seconds gauge\n")
        stream.write(
            'tianji_backup_last_verified_timestamp_seconds{namespace="'
            + namespace
            + '"} '
            + str(timestamp)
            + "\n"
        )
        stream.flush()
        os.fsync(stream.fileno())
    database.protect(temporary)
    os.replace(temporary, target)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--proof", type=Path, required=True)
    parser.add_argument("--textfile", type=Path, required=True)
    args = parser.parse_args(argv)
    proof = json.loads(database.private_file(args.proof).read_text(encoding="utf8"))
    timestamp = validate(proof, args.proof.parent, candidate(args.package))
    publish(args.textfile, timestamp, proof["namespace"])
    print("Managed backup proof published; no backup or restore was executed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError, database.DatabaseError):
        print(
            "Managed backup proof rejected; last verified timestamp was not refreshed",
            file=sys.stderr,
        )
        raise SystemExit(2)
