"""Check recovery compatibility without starting a database or altering a real bundle."""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

DEPLOY=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(DEPLOY/'operations'))
import runtime_backup
import runtime_redis_snapshot


class SharedOperationsTest(unittest.TestCase):
    def legacy(self,name):
        spec=importlib.util.spec_from_file_location('compat_'+name,DEPLOY/'final'/(name+'.py'))
        module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module

    def test_old_imports_share_the_actual_verifier_and_redis_wire_protocol(self):
        legacy=self.legacy('final_backup');redis=self.legacy('redis_snapshot')
        self.assertIs(legacy.verify,runtime_backup.verify)
        self.assertIs(legacy.quiesce,runtime_backup.quiesce)
        self.assertIs(redis.transfer,runtime_redis_snapshot.transfer)
        self.assertEqual(redis.SEMANTIC_SCRIPT,runtime_redis_snapshot.SEMANTIC_SCRIPT)
        with self.assertRaises(RuntimeError):runtime_backup.capture()

    def test_legacy_media_bundle_still_verifies_and_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle=Path(directory);content=b'synthetic private media bytes'
            with zipfile.ZipFile(bundle/'media.zip','w') as archive:archive.writestr('owned/fixture.mp4',content)
            with zipfile.ZipFile(bundle/'private-config.zip','w') as archive:archive.writestr('fixture.yml','mode: synthetic')
            manifest={'files':{name:runtime_backup.digest(bundle/name) for name in ('media.zip','private-config.zip')},'mediaFiles':{'owned/fixture.mp4':hashlib.sha256(content).hexdigest()}}
            (bundle/'manifest.json').write_text(json.dumps(manifest))
            self.assertEqual(manifest,runtime_backup.verify(bundle))
            (bundle/'media.zip').write_bytes(b'tampered')
            with self.assertRaises(RuntimeError):runtime_backup.verify(bundle)

    def test_table_checksum_names_remain_bounded_and_complete(self):
        queries=[]
        self.assertEqual({'tj_user.user':12},runtime_backup.table_checksums(lambda sql:queries.append(sql) or 'tj_user.user\t12',['tj_user.user']))
        self.assertEqual(['CHECKSUM TABLE `tj_user`.`user` EXTENDED'],queries)
        with self.assertRaises(RuntimeError):runtime_backup.table_checksums(lambda sql:'', ['tj_user.user;DROP TABLE user'])
        with self.assertRaises(RuntimeError):runtime_backup.table_checksums(lambda sql:'', ['tj_user.user'])


if __name__=='__main__':unittest.main()
