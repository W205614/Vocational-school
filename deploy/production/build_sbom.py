"""Build the actual reactor and bind a resolved SBOM to clean committed source."""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--commit", required=True)
    args = parser.parse_args(argv)
    if not re.fullmatch(r"[0-9a-f]{40}", args.commit):
        parser.error("An exact source commit is required")
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if head != args.commit or subprocess.run(["git", "diff", "--quiet", "HEAD", "--"]).returncode:
        print("SBOM build refused: source differs from the requested clean commit", file=sys.stderr)
        return 1
    started = dt.datetime.now(dt.timezone.utc)
    command = [shutil.which("mvn") or "mvn", "-B", "-Pcompact", "-DskipTests", "package",
        "org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom", "-DincludeProvidedScope=false",
        "-DincludeTestScope=false", "-DincludeSystemScope=false", "-DincludeRuntimeScope=true", "-DincludeCompileScope=true"]
    if subprocess.run(command).returncode:
        return 1
    path = Path("target/bom.json")
    try:
        bom = json.loads(path.read_text(encoding="utf8"))
        components = bom.get("components", [])
        if bom.get("bomFormat") != "CycloneDX" or not components or path.stat().st_mtime < started.timestamp() - 2:
            raise ValueError("A freshly generated resolved BOM is required")
        if any(not value.get("purl") or not value.get("version") for value in components):
            raise ValueError("Unresolved runtime components cannot pass the security build")
        if subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip() != head or subprocess.run(["git", "diff", "--quiet", "HEAD", "--"]).returncode:
            raise ValueError("Source changed during the security build")
        properties = bom.setdefault("metadata", {}).setdefault("properties", [])
        properties.append({"name": "tianji:source:revision", "value": head})
        path.write_text(json.dumps(bom, indent=2) + "\n", encoding="utf8")
        binding = {"schema": "tianji-security-sbom-v1", "commit": head, "started_at": started.isoformat(),
            "completed_at": dt.datetime.now(dt.timezone.utc).isoformat(), "bom_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "components": len(components), "resolved_runtime": True, "maven_exit_code": 0}
        Path("target/security-bom-binding.json").write_text(json.dumps(binding, indent=2) + "\n", encoding="utf8")
        print("Resolved runtime SBOM bound to " + head)
        return 0
    except (OSError, ValueError):
        print("SBOM build failed: fresh resolved components/source binding unavailable", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
