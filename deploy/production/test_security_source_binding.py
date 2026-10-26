import copy
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import security_scan


class SourceBindingTest(unittest.TestCase):
    def test_snapshot_excludes_private_ignored_and_untracked_files_and_rejects_dirty_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            repo = base / "repo"
            repo.mkdir()
            def git(*args):
                return subprocess.check_output(["git", "-C", str(repo), *args], stderr=subprocess.DEVNULL, text=True).strip()
            git("init")
            (repo / ".gitignore").write_text(".env\n.local/\n", encoding="utf8")
            (repo / "public.txt").write_text("tracked source", encoding="utf8")
            (repo / ".env").write_text("private fixture, never publish", encoding="utf8")
            (repo / "untracked.txt").write_text("untracked fixture", encoding="utf8")
            git("add", ".gitignore", "public.txt")
            git("-c", "user.name=Contract Test", "-c", "user.email=contract@example.invalid", "commit", "--no-gpg-sign", "-m", "fixture")
            commit = git("rev-parse", "HEAD")
            snapshot = security_scan.source_snapshot(commit, base / "snapshot", repo)
            self.assertTrue((snapshot / ".gitignore").exists())
            self.assertTrue((snapshot / "public.txt").exists())
            self.assertFalse((snapshot / ".env").exists())
            self.assertFalse((snapshot / "untracked.txt").exists())
            (repo / "public.txt").write_text("changed", encoding="utf8")
            with self.assertRaises(RuntimeError):
                security_scan.source_snapshot(commit, base / "dirty", repo)

    def test_bom_requires_current_resolved_build_hash_source_and_time(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            path, binding_path = base / "bom.json", base / "binding.json"
            now = dt.datetime.now(dt.timezone.utc)
            bom = {"bomFormat": "CycloneDX", "components": [{"name": "sample", "version": "1.0", "purl": "pkg:maven/test/sample@1.0"}],
                   "metadata": {"timestamp": now.isoformat(), "properties": [{"name": "tianji:source:revision", "value": "a" * 40}]}}
            path.write_text(json.dumps(bom), encoding="utf8")
            binding = {"schema": "tianji-security-sbom-v1", "commit": "a" * 40, "resolved_runtime": True, "maven_exit_code": 0,
                "started_at": now.isoformat(), "completed_at": now.isoformat(), "components": 1,
                "bom_sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
            binding_path.write_text(json.dumps(binding), encoding="utf8")
            security_scan.validate_bom(path, binding_path, "a" * 40, now)
            for key, value in [("commit", "b" * 40), ("components", 2), ("bom_sha256", "0" * 64),
                               ("completed_at", (now - dt.timedelta(days=2)).isoformat())]:
                changed = {**binding, key: value}
                binding_path.write_text(json.dumps(changed), encoding="utf8")
                with self.assertRaises(ValueError):
                    security_scan.validate_bom(path, binding_path, "a" * 40, now)

    def test_image_id_and_revision_must_match_the_actual_local_image(self):
        identity = "sha256:" + "a" * 64
        image = {"Id": identity, "Config": {"Labels": {"org.opencontainers.image.revision": "b" * 40}}}
        result = SimpleNamespace(returncode=0, stdout=json.dumps([image]))
        with patch("security_scan.subprocess.run", return_value=result):
            security_scan.verify_image_source(identity, "b" * 40)
            with self.assertRaises(ValueError):
                security_scan.verify_image_source(identity, "c" * 40)


if __name__ == "__main__":
    unittest.main()
