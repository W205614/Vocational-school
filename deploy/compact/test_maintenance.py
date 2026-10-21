import datetime
import json
from pathlib import Path
import tempfile
import shutil
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import maintenance


class MaintenanceTest(unittest.TestCase):
    def test_due_uses_shanghai_0330_boundary(self):
        now = datetime.datetime(2026, 10, 19, 3, 29, tzinfo=maintenance.SHANGHAI)
        last = datetime.datetime(2026, 10, 18, 3, 30, tzinfo=maintenance.SHANGHAI).timestamp()
        self.assertFalse(maintenance.backup_due({'lastSuccess': last}, now))
        self.assertTrue(maintenance.backup_due({'lastSuccess': last}, now.replace(minute=30)))

    def test_lock_prevents_overlap_and_is_released_after_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            with maintenance.maintenance_lock(directory):
                with self.assertRaisesRegex(RuntimeError, 'maintenance lock'):
                    with maintenance.maintenance_lock(directory): pass
            with maintenance.maintenance_lock(directory): pass

    def test_docker_off_skips_without_appending_error_logs(self):
        with tempfile.TemporaryDirectory() as directory:
            fake = SimpleNamespace(LOCAL=Path(directory))
            with patch.dict('sys.modules', {'setup': fake}), patch.object(maintenance, 'docker_available', return_value=False), patch.object(maintenance, 'perform_backup') as backup:
                for _ in range(5): self.assertEqual(0, maintenance.scheduled())
            backup.assert_not_called()
            self.assertFalse((Path(directory) / 'maintenance.log').exists())
            data = json.loads((Path(directory) / 'ops-status/backup.json').read_text())
            self.assertEqual('SKIPPED_DOCKER_UNAVAILABLE', data['state'])
            self.assertNotIn('lastFailure', data)
            self.assertLess((Path(directory) / 'ops-status/backup.json').stat().st_size, 1024)

    def test_cold_backup_only_starts_existing_required_infrastructure_and_restores_it(self):
        runtime = SimpleNamespace(COMPOSE=['docker', 'compose', '-p', 'owned'])
        calls = []
        def check(command, **kwargs):
            if '--status' in command: return 'prometheus\n'
            if 'inspect' in command:return 'exited\n'
            return 'existing-container-id\n'
        def run(command, **kwargs): calls.append(command)
        with self.assertRaisesRegex(ValueError, 'capture failed'):
            with maintenance.required_resources(runtime, check, run): raise ValueError('capture failed')
        self.assertEqual(['mysql', 'redis', 'rabbitmq'], calls[0][-3:])
        self.assertEqual(['mysql', 'redis', 'rabbitmq'], calls[1][-3:])
        self.assertIn('start', calls[0]); self.assertIn('stop', calls[1])
        self.assertFalse(any('app-' in arg or 'web-' in arg for call in calls for arg in call))

    def test_paused_container_is_left_untouched(self):
        runtime=SimpleNamespace(COMPOSE=['docker','compose','-p','owned'])
        calls=[]
        def check(command,**kwargs):
            if '--status' in command:return ''
            return 'paused\n' if 'inspect' in command else 'id\n'
        with self.assertRaises(maintenance.TransitionalService):
            with maintenance.required_resources(runtime,check,lambda *a,**k:calls.append(a)):pass
        self.assertEqual([],calls)

    def test_actual_failed_backup_does_not_keep_creating_large_bundles(self):
        with tempfile.TemporaryDirectory() as directory:
            local=Path(directory);maintenance.status(local,'FAILED',lastFailure=1,requiresAttention=True)
            with patch.dict('sys.modules',{'setup':SimpleNamespace(LOCAL=local)}),patch.object(maintenance,'perform_backup') as backup:
                for _ in range(20):self.assertEqual(0,maintenance.scheduled())
            backup.assert_not_called()

    def test_interrupted_broker_is_resumed_before_apps_and_scheduler_requires_attention(self):
        with tempfile.TemporaryDirectory() as directory:
            local=Path(directory); runtime=SimpleNamespace(COMPOSE=['docker','compose','-p','owned'])
            maintenance.atomic_json(local/'ops-status/resume.json',{'home':str(local.resolve()),'compose':runtime.COMPOSE,'apps':['app-commerce'],'temporary':[]})
            calls=[]
            fake=SimpleNamespace(LOCAL=local,configure_acceptance=lambda:runtime)
            with patch.dict('sys.modules',{'setup':fake}),patch.object(maintenance,'docker_available',return_value=True),patch.object(maintenance.subprocess,'run',side_effect=lambda command,**kwargs:calls.append(command)),patch.object(maintenance,'perform_backup') as backup:
                self.assertEqual(0,maintenance.scheduled())
            self.assertIn('start_app',calls[1]);self.assertIn('check_running',calls[2]);self.assertIn('app-commerce',calls[3])
            backup.assert_not_called()
            self.assertTrue(json.loads((local/'ops-status/backup.json').read_text())['requiresAttention'])

    @patch("backup_retention.recycle", side_effect=shutil.rmtree)
    def test_retention_keeps_latest_owned_bundle_and_protects_unowned_backups(self, dispose):
        with tempfile.TemporaryDirectory() as directory:
            local = Path(directory); root = local / 'backup'; root.mkdir()
            legacy = root / '20261018T030000.000001Z'; legacy.mkdir(); (legacy/'manifest.json').write_text('{}')
            manual = root / '20261018T030000.000002Z'; manual.mkdir(); (manual/'manifest.json').write_text('{}')
            verify = lambda path: json.loads((path/'manifest.json').read_text())
            created = []
            for i in range(3):
                bundle = root / f'20261019T030000.00000{i}Z'; bundle.mkdir(); (bundle/'manifest.json').write_text('{"quiesced":true}')
                created.append(bundle); maintenance.retain_automatic(local, bundle, verify)
            self.assertFalse(created[0].exists())
            self.assertFalse(created[1].exists())
            self.assertTrue(created[2].exists() and legacy.exists() and manual.exists())
            escaped = local / 'elsewhere'; escaped.mkdir()
            with self.assertRaisesRegex(RuntimeError, 'escaped'): maintenance.retain_automatic(local, escaped, verify)

    @patch("backup_retention.recycle", side_effect=shutil.rmtree)
    def test_new_complete_backup_supersedes_owned_publication_pin(self, dispose):
        with tempfile.TemporaryDirectory() as directory:
            local=Path(directory);root=local/'backup';root.mkdir()
            created=[]
            for i in range(4):
                bundle=root/f'20261019T030000.00000{i}Z';bundle.mkdir();(bundle/'manifest.json').write_text('{"quiesced":true}')
                created.append(bundle);maintenance.retain_automatic(local,bundle,lambda path:{"quiesced":True})
                if i==0:(bundle/'publication-pin.json').write_text('{"purpose":"publication-rollback"}')
            self.assertFalse(created[0].exists())
            self.assertFalse(created[1].exists())
            self.assertFalse(created[2].exists())
            self.assertTrue(created[3].exists())


if __name__ == '__main__': unittest.main()
