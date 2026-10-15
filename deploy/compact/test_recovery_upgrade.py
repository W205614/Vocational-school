import copy
import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import Mock, patch
from recovery_upgrade import (ALIASES, SERVICES, load_upgrade, upgraded_configuration, preserve_generated_configs,
                              activate_generated_configs, copy_verified_manifests, compare_broker_archives,
                              compare_redis_snapshots, tar_inventory, verify_source_commit)


class RecoveryUpgradeTest(unittest.TestCase):
    def manifests(self, home):
        local = home / '.local'
        local.mkdir(parents=True)
        images = {alias: {'image': 'tianji-compact/' + alias + ':local', 'imageId': 'sha256:' + hashlib.sha256(alias.encode()).hexdigest(),
                          **({'jarSha256': 'a' * 64} if alias not in {'student', 'admin'} else {})} for alias in ALIASES}
        source = {'sourceCommit': '1' * 40, 'imageDigests': {('web-' if alias in {'student', 'admin'} else 'app-') + alias: image['imageId'] for alias, image in images.items()}}
        self.write_manifests(home, images, source)
        return images, source

    def write_manifests(self, home, images, source):
        (home / '.local/images.json').write_text(json.dumps(images), encoding='utf8')
        (home / '.local/build-source.json').write_text(json.dumps(source), encoding='utf8')

    def test_all_seven_immutable_images_and_source_are_bound_before_upgrade(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory) / 'source'
            images, source = self.manifests(home)
            inspect, verify = Mock(side_effect=lambda image: image), Mock()
            proof = load_upgrade(home, Path(directory), inspect, verify)
            self.assertEqual(source['imageDigests'], proof['imageDigests'])
            self.assertEqual(7, inspect.call_count)
            verify.assert_called_once_with(Path(directory), source['sourceCommit'])
            clone = Path(directory) / 'clone'
            (clone / '.local').mkdir(parents=True)
            copy_verified_manifests(home, clone, proof)
            self.assertEqual((home / '.local/images.json').read_bytes(), (clone / '.local/images.json').read_bytes())

    def test_partial_mutable_mixed_and_unavailable_image_manifests_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory) / 'source'
            images, source = self.manifests(home)
            variants = []
            missing = copy.deepcopy(images);missing.pop('student');variants.append((missing, source))
            mutable = copy.deepcopy(images);mutable['student']['imageId'] = 'latest';variants.append((mutable, source))
            mixed = copy.deepcopy(images);mixed['support']['sourceCommit'] = '2' * 40;variants.append((mixed, source))
            no_jar = copy.deepcopy(images);no_jar['gateway'].pop('jarSha256');variants.append((no_jar, source))
            changed = copy.deepcopy(source);changed['imageDigests']['web-admin'] = 'sha256:' + 'f' * 64;variants.append((images, changed))
            for image_manifest, source_manifest in variants:
                with self.subTest(images=image_manifest):
                    self.write_manifests(home, image_manifest, source_manifest)
                    with self.assertRaises(RuntimeError):load_upgrade(home, Path(directory), lambda image: image, lambda *args: None)
            self.write_manifests(home, images, source)
            with self.assertRaises(RuntimeError):load_upgrade(home, Path(directory), lambda image: 'sha256:' + '0' * 64, lambda *args: None)

    def test_manifests_changed_after_preflight_cannot_be_installed(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory);home = base / 'source';images, source = self.manifests(home)
            proof = load_upgrade(home, base, lambda image: image, lambda *args: None)
            original = (home / '.local/build-source.json').read_bytes()
            images['student']['image'] = 'different'
            self.write_manifests(home, images, source)
            clone = base / 'clone';(clone / '.local').mkdir(parents=True)
            with self.assertRaises(RuntimeError):copy_verified_manifests(home, clone, proof)
            self.assertEqual(original, (home / '.local/build-source.json').read_bytes())

    def compose(self, prefix):
        services = {name: {'image': prefix, 'environment': {'APP_PORT': '25310'}, 'volumes': ['./.local/configs:/run/configs:ro']} for name in SERVICES}
        services['rabbitmq'] = {'image': 'sha256:' + 'b' * 64, 'hostname': 'original-broker', 'volumes': ['compact_rabbit:/var/lib/rabbitmq']}
        return {'name': 'tianji-recovery-test', 'services': services, 'volumes': {'compact_rabbit': {}}}

    def test_new_app_settings_keep_original_broker_and_isolated_infrastructure(self):
        recorded, generated = self.compose('old'), self.compose('new')
        recorded['services']['app-gateway']['environment']['APP_PORT'] = '27310'
        generated['services']['rabbitmq']['hostname'] = 'new-unrelated-host'
        before = copy.deepcopy(recorded)
        upgraded = upgraded_configuration(recorded, generated, {name: 'sha256:' + 'a' * 64 for name in SERVICES})
        self.assertEqual('25310', upgraded['services']['app-gateway']['environment']['APP_PORT'])
        self.assertEqual(recorded['services']['rabbitmq'], upgraded['services']['rabbitmq'])
        self.assertEqual(before, recorded)
        generated['services']['app-gateway']['volumes'] = ['E:/source/.local:/run/configs']
        with self.assertRaises(RuntimeError):upgraded_configuration(recorded, generated, {name: 'sha256:' + 'a' * 64 for name in SERVICES})

    def test_generated_offset_configs_survive_recorded_config_restore(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory);configs = home / '.local/configs';configs.mkdir(parents=True)
            (configs / 'learning.yml').write_text('port: 26003\n', encoding='utf8')
            saved = preserve_generated_configs(home)
            (configs / 'learning.yml').write_text('port: 27003\n', encoding='utf8')
            (configs / 'obsolete.yml').write_text('old: true\n', encoding='utf8')
            self.assertEqual(64, len(activate_generated_configs(home, saved)))
            self.assertEqual('port: 26003\n', (configs / 'learning.yml').read_text())
            self.assertFalse((configs / 'obsolete.yml').exists())
            self.assertEqual('port: 27003\n', (home / '.local/raw-restored-module-configs/learning.yml').read_text())
            with self.assertRaises(RuntimeError):activate_generated_configs(home, saved)

    def archive(self, path, content=b'broker state', name='./mnesia/data'):
        with tarfile.open(path, 'w:gz') as archive:
            info = tarfile.TarInfo(name);info.mode = 0o600;info.size = len(content)
            archive.addfile(info, io.BytesIO(content))

    def test_broker_restore_proves_file_bytes_and_rejects_changed_or_unsafe_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory);old, restored = base / 'old.tar.gz', base / 'restored.tar.gz'
            self.archive(old);self.archive(restored)
            self.assertTrue(compare_broker_archives(old, restored)['brokerFileContentsMatched'])
            self.archive(restored, b'changed')
            with self.assertRaises(RuntimeError):compare_broker_archives(old, restored)
            self.archive(restored, name='../escape')
            with self.assertRaises(RuntimeError):tar_inventory(restored)

    def test_redis_verifies_live_values_and_expirations_while_allowing_natural_expiry(self):
        expected = {'format': 1, 'records': [{'db': 0, 'key': 'a', 'value': 'dump-a', 'expiresAt': None},
                                            {'db': 1, 'key': 'b', 'value': 'dump-b', 'expiresAt': 10000},
                                            {'db': 2, 'key': 'c', 'value': 'dump-c', 'expiresAt': 100}]}
        restored = copy.deepcopy(expected);restored['records'].pop();restored['records'][1]['expiresAt'] += 5
        proof = compare_redis_snapshots(expected, restored, 1000)
        self.assertEqual(1, proof['redisExpiredRecordsSkipped'])
        for edit in ('missing', 'value', 'expiry', 'extra'):
            changed = copy.deepcopy(restored)
            if edit == 'missing':changed['records'].pop(0)
            if edit == 'value':changed['records'][0]['value'] = 'changed'
            if edit == 'expiry':changed['records'][0]['expiresAt'] = 9000
            if edit == 'extra':changed['records'].append({'db': 0, 'key': 'extra', 'value': 'value', 'expiresAt': None})
            with self.subTest(edit=edit), self.assertRaises(RuntimeError):compare_redis_snapshots(expected, changed, 1000)

    def test_current_production_or_migration_changes_cannot_claim_recorded_source_commit(self):
        with patch('recovery_upgrade.subprocess.run'), patch('recovery_upgrade.subprocess.check_output') as output:
            output.side_effect = ['README.md\n', 'deploy/compact/recovery_upgrade.py\n']
            verify_source_commit(Path('repo'), 'a' * 40)
            output.side_effect = ['deploy/acceptance/migrations/tj_learning/V009.sql\n', '']
            with self.assertRaises(RuntimeError):verify_source_commit(Path('repo'), 'a' * 40)
        with self.assertRaises(RuntimeError):verify_source_commit(Path('repo'), 'HEAD')


if __name__ == '__main__':
    unittest.main()
