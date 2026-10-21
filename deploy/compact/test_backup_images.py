import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from backup_images import refresh


class CurrentImagesTest(unittest.TestCase):
    def prepare(self, directory):
        local=Path(directory); bundle=local/'backup/20261021T030000.000001Z';bundle.mkdir(parents=True)
        binding={'app-identity':'sha256:current'}
        (bundle/'runtime-images.json').write_text(json.dumps(binding));(bundle/'manifest.json').write_text('{}')
        folder=local/'current-runtime-backup';folder.mkdir();(folder/'runtime-images.tar').write_bytes(b'current-image')
        record={'services':binding,'sha256':hashlib.sha256(b'current-image').hexdigest(),'tags':{'tianji-compact/identity:local':'sha256:current'}}
        (folder/'recovery.json').write_text(json.dumps(record))
        def runner(command,**kwargs):
            if command[1]=='ps':return 'container-id'
            if command[1]=='inspect':return json.dumps([{'Config':{'Labels':{'com.docker.compose.service':'app-identity'}},'Image':binding['app-identity']}])
            return json.dumps([{'Id':binding['app-identity'],'Architecture':'amd64','Os':'linux','RepoTags':[]}])
        return local,bundle,folder,binding,runner

    def test_daily_backups_reuse_one_unchanged_image_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            local,bundle,folder,binding,runner=self.prepare(directory)
            with patch('backup_images.subprocess.run') as save,patch('backup_retention.recycle') as dispose:
                result=refresh(local,'tianji-compact',bundle,runner,save)
            self.assertTrue(result['reused']);save.assert_not_called();dispose.assert_not_called()
            self.assertEqual([folder/'runtime-images.tar'],list(folder.glob('*.tar')))
            self.assertEqual(str(bundle),json.loads((folder/'recovery.json').read_text())['dataBundle'])

    def test_failed_new_export_keeps_old_image_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            local,bundle,folder,binding,runner=self.prepare(directory)
            record=json.loads((folder/'recovery.json').read_text());record['services']={'app-identity':'old'}
            (folder/'recovery.json').write_text(json.dumps(record))
            def execute(command,**kwargs):Path(command[command.index('-o')+1]).write_bytes(b'incomplete')
            with patch('retire_images.verify_archive',side_effect=RuntimeError('corrupt')),patch('backup_retention.recycle') as dispose:
                with self.assertRaisesRegex(RuntimeError,'corrupt'):refresh(local,'tianji-compact',bundle,runner,execute)
            dispose.assert_not_called();self.assertEqual(b'current-image',(folder/'runtime-images.tar').read_bytes())
            self.assertEqual(record,json.loads((folder/'recovery.json').read_text()))

    def test_changed_images_replace_one_archive_only_after_successful_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            local,bundle,folder,binding,runner=self.prepare(directory)
            record=json.loads((folder/'recovery.json').read_text());record['services']={'app-identity':'old'}
            (folder/'recovery.json').write_text(json.dumps(record));events=[]
            def execute(command,**kwargs):
                events.append('export');Path(command[command.index('-o')+1]).write_bytes(b'new-image')
                self.assertEqual(['sha256:current'],command[command.index('-o')+2:])
            def verified(*args):events.append('verified');return hashlib.sha256(b'new-image').hexdigest()
            def dispose(path):
                events.append('recycled');self.assertEqual('verified',events[-2]);path.unlink()
            with patch('retire_images.verify_archive',side_effect=verified):
                result=refresh(local,'tianji-compact',bundle,runner,execute,dispose)
            self.assertFalse(result['reused']);self.assertEqual(['export','verified','recycled'],events)
            self.assertEqual([folder/'runtime-images.tar'],list(folder.glob('*.tar')))
            self.assertEqual(b'new-image',(folder/'runtime-images.tar').read_bytes())

    def test_changed_runtime_or_corrupt_current_archive_does_not_recycle(self):
        with tempfile.TemporaryDirectory() as directory:
            local,bundle,folder,binding,runner=self.prepare(directory)
            with patch('backup_retention.recycle') as dispose:
                (folder/'runtime-images.tar').write_bytes(b'corrupt')
                with self.assertRaisesRegex(RuntimeError,'corrupt'):refresh(local,'tianji-compact',bundle,runner)
                (bundle/'runtime-images.json').write_text('{"app-identity":"wrong"}')
                with self.assertRaisesRegex(RuntimeError,'differ'):refresh(local,'tianji-compact',bundle,runner)
            dispose.assert_not_called()


if __name__ == '__main__':unittest.main()
