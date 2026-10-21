import json
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

from backup_retention import retain_latest, recycle


class LatestBackupTest(unittest.TestCase):
    def bundle(self, root, number):
        path = root / f'20261021T030000.{number:06d}Z'
        path.mkdir(parents=True)
        (path / 'manifest.json').write_text('{"quiesced":true}')
        return path

    def verify(self, path):
        return json.loads((path / 'manifest.json').read_text())

    def test_new_complete_backup_supersedes_owned_manual_and_publication_backups(self):
        with tempfile.TemporaryDirectory() as directory:
            local = Path(directory); root = local / 'backup'
            first = self.bundle(root, 1)
            retain_latest(local, first, self.verify)
            (first / 'publication-pin.json').write_text('{"purpose":"publication-rollback"}')
            unknown = self.bundle(root, 0)
            second = self.bundle(root, 2)
            calls = []
            def dispose(path):
                calls.append(path); shutil.rmtree(path)
            self.assertEqual([first.name], retain_latest(local, second, self.verify, dispose))
            self.assertEqual([first], calls)
            self.assertTrue(second.exists() and unknown.exists())
            self.assertFalse(first.exists())

    def test_failed_or_partial_replacement_never_discards_the_last_backup(self):
        with tempfile.TemporaryDirectory() as directory:
            local = Path(directory); root = local / 'backup'
            first = self.bundle(root, 1); retain_latest(local, first, self.verify)
            second = self.bundle(root, 2)
            with patch('backup_retention.recycle') as dispose:
                with self.assertRaises(RuntimeError):retain_latest(local, second, lambda p: {'quiesced': False})
                with self.assertRaises(ValueError):retain_latest(local, second, lambda p: (_ for _ in ()).throw(ValueError('corrupt')))
            dispose.assert_not_called(); self.assertTrue(first.exists())

    def test_corrupt_foreign_owned_and_future_bundles_are_not_disposed(self):
        with tempfile.TemporaryDirectory() as directory:
            local = Path(directory); root = local / 'backup'
            first = self.bundle(root, 1); retain_latest(local, first, self.verify)
            owner = first / 'backup-owner.json'
            data = json.loads(owner.read_text());data['home'] = 'another-home';owner.write_text(json.dumps(data))
            future = self.bundle(root, 9); retain_latest(local, future, self.verify)
            current = self.bundle(root, 2)
            with patch('backup_retention.recycle') as dispose:retain_latest(local, current, self.verify)
            dispose.assert_not_called(); self.assertTrue(first.exists() and future.exists())

    def test_linux_does_not_fall_back_to_permanent_deletion(self):
        with patch('backup_retention.os.name', 'posix'):
            with self.assertRaisesRegex(RuntimeError, 'Recycle Bin'):recycle('unused')


if __name__ == '__main__':unittest.main()
