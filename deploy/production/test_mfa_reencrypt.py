import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from database import DatabaseError, new_json
from mfa_reencrypt import MAIN, maintain


class MfaMaintenanceWrapperTest(unittest.TestCase):
    def files(self, directory):
        root = Path(directory)
        settings = root / "settings.json"
        new_json(settings, {"password": "fixture-password", "activeKey": "fixture-key"})
        jar = root / "auth.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("BOOT-INF/classes/" + MAIN.replace(".", "/") + ".class", b"fixture")
        return settings, jar, root / "receipt.json"

    def test_only_paths_reach_the_process_and_receipt_contains_no_secret_material(self):
        with tempfile.TemporaryDirectory() as directory:
            settings, jar, receipt = self.files(directory)
            result = {"status": "APPLIED", "examined": 2, "reencrypted": 1, "activeKeyId": "a" * 64}
            with patch("mfa_reencrypt.subprocess.run", return_value=subprocess.CompletedProcess([], 0,
                       "diagnostic line\nMFA_MAINTENANCE_RESULT=" + json.dumps(result) + "\n", "")) as run:
                state = maintain(settings, jar, "127.0.0.1", "acceptance_mfa_rotation", receipt)
            self.assertEqual("APPLIED", state["status"])
            command = run.call_args.args[0]
            self.assertIn(str(settings.resolve()), command)
            for secret in ("fixture-password", "fixture-key"):
                self.assertNotIn(secret, json.dumps(command))
                self.assertNotIn(secret, receipt.read_text(encoding="utf8"))
            with patch("mfa_reencrypt.subprocess.run") as run:
                with self.assertRaises(FileExistsError):
                    maintain(settings, jar, "127.0.0.1", "acceptance_mfa_rotation", receipt)
                run.assert_not_called()

    def test_failed_or_ambiguous_execution_preserves_an_unconfirmed_receipt(self):
        for code, output in ((1, ""), (0, "not a confirmed result")):
            with self.subTest(code=code), tempfile.TemporaryDirectory() as directory:
                settings, jar, receipt = self.files(directory)
                with patch("mfa_reencrypt.subprocess.run", return_value=subprocess.CompletedProcess([], code, output, "fixture-password")):
                    with self.assertRaises(DatabaseError) as failure:
                        maintain(settings, jar, "127.0.0.1", "acceptance_mfa_rotation", receipt)
                self.assertNotIn("fixture-password", str(failure.exception))
                self.assertEqual("FAILED_OR_UNCONFIRMED", json.loads(receipt.read_text(encoding="utf8"))["status"])


if __name__ == "__main__":
    unittest.main()
