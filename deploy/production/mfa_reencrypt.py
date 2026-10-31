"""Explicit MFA ciphertext maintenance; no backup, deployment or application startup."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

from database import DatabaseError, new_json, overwrite_private_json, private_file

MAIN = "com.tianji.auth.maintenance.MfaSecretMaintenance"


def maintain(settings, jar, expected_host, expected_database, receipt, java="java"):
    settings = private_file(settings)
    jar = Path(jar).resolve()
    with zipfile.ZipFile(jar) as archive:
        if "BOOT-INF/classes/" + MAIN.replace(".", "/") + ".class" not in archive.namelist():
            raise DatabaseError("Use a built executable artifact containing the MFA maintenance entry point")
    state = {"schema": "tianji-mfa-maintenance-receipt-v1", "status": "PREPARED_NOT_APPLIED",
             "expectedHost": expected_host, "expectedDatabase": expected_database,
             "artifactSha256": hashlib.sha256(jar.read_bytes()).hexdigest()}
    new_json(receipt, state)
    process = subprocess.run([java, "-Dloader.main=" + MAIN, "-cp", str(jar),
                              "org.springframework.boot.loader.launch.PropertiesLauncher", str(settings),
                              expected_host, expected_database], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if process.returncode:
        state.update(status="FAILED_OR_UNCONFIRMED", exitCode=process.returncode)
        overwrite_private_json(receipt, state)
        raise DatabaseError("MFA maintenance did not confirm completion; inspect its target and protected receipt before retrying")
    try:
        results = [line.removeprefix("MFA_MAINTENANCE_RESULT=") for line in process.stdout.splitlines()
                   if line.startswith("MFA_MAINTENANCE_RESULT=")]
        if len(results) != 1:
            raise ValueError("Missing unambiguous maintenance result")
        result = json.loads(results[0])
        if set(result) != {"status", "examined", "reencrypted", "activeKeyId"} or result["status"] != "APPLIED":
            raise ValueError("Unexpected maintenance result")
        if any(type(result[field]) is not int or result[field] < 0 for field in ("examined", "reencrypted")):
            raise ValueError("Unexpected maintenance counts")
        if result["reencrypted"] > result["examined"] or len(result["activeKeyId"]) != 64 or any(c not in "0123456789abcdef" for c in result["activeKeyId"]):
            raise ValueError("Unexpected maintenance key identifier")
    except (ValueError, TypeError, KeyError):
        state.update(status="FAILED_OR_UNCONFIRMED", exitCode=process.returncode)
        overwrite_private_json(receipt, state)
        raise DatabaseError("MFA maintenance output is unconfirmed; inspect the target and protected receipt") from None
    state.update(result)
    overwrite_private_json(receipt, state)
    return state


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--settings", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--expected-host", required=True)
    parser.add_argument("--expected-database", required=True)
    parser.add_argument("--receipt", required=True, type=Path)
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    try:
        result = maintain(args.settings,args.jar,args.expected_host,args.expected_database,args.receipt,args.java)
    except (DatabaseError, OSError, zipfile.BadZipFile) as error:
        parser.exit(1, str(error) + "\n")
    print(json.dumps({key: result[key] for key in ("status", "examined", "reencrypted", "activeKeyId")}))


if __name__ == "__main__":
    main()
