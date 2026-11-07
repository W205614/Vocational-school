"""Exercise migration, independent homes and relative bind paths with real Compose parsing."""

import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import yaml
from runtime_compose import (
    active_compose,
    runtime_compose,
    compose_command,
    verify_migration,
    equivalent_commands,
    resolve_project,
    rabbit_username,
)


class RuntimeComposeTest(unittest.TestCase):
    def test_existing_project_identity_keeps_original_volume_names(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            (home / '.local').mkdir()
            (home / '.env').write_text('')
            (home / 'compose.yaml').write_text('name: zhixuetang-compact\n')
            original = {
                'name': 'tianji-compact',
                'services': {
                    'mysql': {
                        'image': 'mysql:8.4',
                        'volumes': ['compact_mysql:/var/lib/mysql'],
                    }
                },
                'volumes': {'compact_mysql': {}},
            }
            path = home / '.local/compose.runtime.yaml'
            path.write_text(yaml.safe_dump(original))
            before = path.read_bytes()
            self.assertEqual('tianji-compact', resolve_project(home))
            with self.assertRaisesRegex(ValueError, 'conflicts'):
                resolve_project(home, 'zhixuetang-compact')
            parsed = json.loads(
                subprocess.check_output(
                    compose_command(home, resolve_project(home))
                    + ['config', '--format', 'json'],
                    text=True,
                )
            )
            self.assertEqual(
                'tianji-compact_compact_mysql',
                parsed['volumes']['compact_mysql']['name'],
            )
            self.assertEqual(before, path.read_bytes())

    def test_fresh_project_default_and_explicit_isolated_project(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual('zhixuetang-compact', resolve_project(directory))
            self.assertEqual(
                'zhixuetang-recovery-test',
                resolve_project(directory, 'zhixuetang-recovery-test'),
            )

    def test_existing_rabbit_username_is_read_without_replacing_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            self.assertEqual('zhixuetang', rabbit_username(home))
            (home / '.local/configs').mkdir(parents=True)
            (home / '.local/compose.runtime.yaml').write_text(
                yaml.safe_dump(
                    {
                        'name': 'tianji-compact',
                        'services': {
                            'rabbitmq': {
                                'environment': {
                                    'RABBITMQ_DEFAULT_USER': 'original-broker-user'
                                }
                            }
                        },
                    }
                )
            )
            self.assertEqual('original-broker-user', rabbit_username(home))
            config = home / '.local/configs/auth.yml'
            config.write_text(
                yaml.safe_dump(
                    {
                        'spring': {
                            'rabbitmq': {
                                'username': 'actual-app-user',
                                'password': 'untouched',
                            }
                        }
                    }
                )
            )
            before = config.read_bytes()
            self.assertEqual('actual-app-user', rabbit_username(home))
            self.assertEqual(before, config.read_bytes())

    def test_legacy_migration_preserves_bytes_and_does_not_rewrite_template(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            original = b'services:\n  rabbitmq:\n    image: rabbitmq:4.1-management\n    hostname: original-node\n'
            (home / 'compose.yaml').write_bytes(original)
            runtime = runtime_compose(home)
            self.assertEqual(original, runtime.read_bytes())
            self.assertEqual(runtime, active_compose(home))
            (home / 'compose.yaml').write_text('public template changed')
            self.assertEqual(original, runtime_compose(home).read_bytes())

    def test_parsing_keeps_bind_mounts_ports_images_and_broker_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            (home / '.env').write_text('')
            (home / '.local/objects').mkdir(parents=True)
            value = {
                'name': 'runtime-audit',
                'services': {
                    'broker': {
                        'image': 'rabbitmq:4.1-management',
                        'hostname': 'kept-node',
                        'ports': ['127.0.0.1:29373:5672'],
                        'volumes': ['./.local/objects:/objects', 'original_data:/data'],
                    }
                },
                'volumes': {'original_data': {}},
            }
            (home / 'compose.yaml').write_text(yaml.safe_dump(value))
            runtime = verify_migration(home, 'runtime-audit')
            parsed = json.loads(
                subprocess.check_output(
                    compose_command(home, 'runtime-audit')
                    + ['config', '--format', 'json'],
                    text=True,
                )
            )
            broker = parsed['services']['broker']
            self.assertEqual(
                str((home / '.local/objects').resolve()), broker['volumes'][0]['source']
            )
            self.assertEqual('kept-node', broker['hostname'])
            self.assertEqual(
                'runtime-audit_original_data',
                parsed['volumes']['original_data']['name'],
            )
            self.assertEqual(runtime, active_compose(home))
            self.assertEqual('29373', broker['ports'][0]['published'])
            legacy = [
                'docker',
                'compose',
                '-p',
                'runtime-audit',
                '-f',
                str(home / 'compose.yaml'),
                '--env-file',
                str(home / '.env'),
            ]
            self.assertTrue(
                equivalent_commands(
                    legacy, compose_command(home, 'runtime-audit'), home
                )
            )
            foreign = legacy.copy()
            foreign[foreign.index('-p') + 1] = 'other-project'
            self.assertFalse(
                equivalent_commands(
                    foreign, compose_command(home, 'runtime-audit'), home
                )
            )
            value['services']['broker']['hostname'] = 'different-node'
            (home / 'compose.yaml').write_text(yaml.safe_dump(value))
            self.assertFalse(
                equivalent_commands(
                    legacy, compose_command(home, 'runtime-audit'), home
                )
            )

    def test_new_homes_are_independent_and_existing_runtime_takes_precedence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            a = root / 'a'
            b = root / 'b'
            for home in (a, b):
                command = compose_command(home, 'test-' + home.name)
                self.assertEqual(
                    str(home.resolve()),
                    command[command.index('--project-directory') + 1],
                )
                self.assertEqual(
                    home / '.local/compose.runtime.yaml', active_compose(home)
                )
            self.assertNotEqual(active_compose(a), active_compose(b))


if __name__ == '__main__':
    unittest.main()
