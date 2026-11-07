import copy
import json
from pathlib import Path
import tempfile
import unittest

import yaml
from brand_migration import (
    candidates,
    environment_keys,
    module_configuration,
    monitoring,
    migrate,
)


class BrandMigrationTest(unittest.TestCase):
    def test_apply_retains_private_originals_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            local = home / '.local'
            (local / 'configs').mkdir(parents=True)
            compose = local / 'compose.runtime.yaml'
            compose.write_text('name: tianji-compact\nservices: {}\n', encoding='utf8')
            config = local / 'configs/custom.yml'
            config.write_text('tj:\n  secret: unchanged\n', encoding='utf8')
            original = config.read_bytes()
            identity = compose.read_bytes()
            report = migrate(home, True)
            backup = Path(report['backup'])
            self.assertEqual(original, (backup / 'configs/custom.yml').read_bytes())
            self.assertEqual(
                {'zxt': {'secret': 'unchanged'}},
                yaml.safe_load(config.read_text(encoding='utf8')),
            )
            self.assertEqual(identity, compose.read_bytes())
            self.assertEqual([], migrate(home, True)['files'])

    def test_only_owned_names_and_metadata_change_while_secrets_and_storage_stay(self):
        original = {
            'tj': {
                'swagger': {
                    'title': '天机学堂',
                    'package-path': 'com.tianji.user.controller',
                    'contact-email': 'old@example.invalid',
                },
                'local-storage': {'directory': '/objects/tianji/tj-1'},
                'custom-secret': 'TJ_com.tianji.tj.123',
            },
            'spring': {
                'datasource': {
                    'url': 'jdbc:mysql://mysql:3306/tj_user',
                    'password': 'com.tianji.TJ_tj.',
                },
                'rabbitmq': {'username': 'tianji', 'password': 'tj.secret'},
                'autoconfigure': {
                    'exclude': ['com.tianji.common.MqConfig', 'org.example.Custom']
                },
            },
            'encrypt': {
                'key-store': {'alias': 'compact', 'password': 'original-password'}
            },
        }
        before = copy.deepcopy(original)
        metadata = {'title': '职学堂', 'contact-name': 'Zhixuetang'}
        result = module_configuration(original, metadata)
        self.assertNotIn('tj', result)
        self.assertEqual('职学堂', result['zxt']['swagger']['title'])
        self.assertNotIn('contact-email', result['zxt']['swagger'])
        self.assertEqual(
            'com.zhixuetang.user.controller', result['zxt']['swagger']['package-path']
        )
        self.assertEqual(before['spring']['datasource'], result['spring']['datasource'])
        self.assertEqual(before['spring']['rabbitmq'], result['spring']['rabbitmq'])
        self.assertEqual(before['encrypt'], result['encrypt'])
        self.assertEqual(before['tj']['local-storage'], result['zxt']['local-storage'])
        self.assertEqual(before['tj']['custom-secret'], result['zxt']['custom-secret'])
        self.assertEqual(
            ['com.zhixuetang.common.MqConfig', 'org.example.Custom'],
            result['spring']['autoconfigure']['exclude'],
        )
        self.assertEqual(result, module_configuration(result, metadata))
        self.assertEqual(before, original)
        with self.assertRaisesRegex(ValueError, 'roots'):
            module_configuration({'tj': {}, 'zxt': {}})

    def test_environment_changes_only_names_and_rejects_ambiguous_credentials(self):
        original = (
            '# TJ_COMMENT=keep\r\nTJ_INTERNAL_TOKEN=abcTJ_def=tj.\r\nOTHER=tianji\n'
        )
        expected = (
            '# TJ_COMMENT=keep\r\nZXT_INTERNAL_TOKEN=abcTJ_def=tj.\r\nOTHER=tianji\n'
        )
        self.assertEqual(expected, environment_keys(original))
        self.assertEqual(expected, environment_keys(expected))
        with self.assertRaisesRegex(ValueError, 'conflict'):
            environment_keys('TJ_INTERNAL_TOKEN=a\nZXT_INTERNAL_TOKEN=b\n')

    def test_provider_placeholder_names_change_without_changing_secret_defaults(self):
        original = {
            'tj': {
                'pay': {
                    'ali': {
                        'appId': '${TJ_PAY_TJ_PAY_ALI_APPID:}',
                        'merchantPrivateKey': '${TJ_PAY_TJ_PAY_ALI_MERCHANTPRIVATEKEY:TJ_secret.default}',
                        'publicKey': 'literal-TJ_com.tianji',
                    },
                    'notifyHost': 'https://tianji.example/TJ_',
                }
            }
        }
        updated = module_configuration(original)
        self.assertEqual(
            '${ZXT_PAY_ZXT_PAY_ALI_APPID:}', updated['zxt']['pay']['ali']['appId']
        )
        self.assertEqual(
            '${ZXT_PAY_ZXT_PAY_ALI_MERCHANTPRIVATEKEY:TJ_secret.default}',
            updated['zxt']['pay']['ali']['merchantPrivateKey'],
        )
        self.assertEqual(
            original['tj']['pay']['ali']['publicKey'],
            updated['zxt']['pay']['ali']['publicKey'],
        )
        self.assertEqual(
            original['tj']['pay']['notifyHost'], updated['zxt']['pay']['notifyHost']
        )

    def test_monitoring_changes_metrics_and_header_names_only(self):
        original = {
            'title': 'Tianji Reliability',
            'targets': [
                {'expr': 'sum(tj_privacy_tasks_failed) + sum(tianji:requests:rate5m)'}
            ],
            'http_headers': {'X-TJ-Service': {'values': ['observability']}},
            'url': 'https://tianji.invalid/TJ_path',
            'token': 'tianji:keep',
        }
        result = monitoring(original)
        self.assertEqual(
            'sum(zxt_privacy_tasks_failed) + sum(zhixuetang:requests:rate5m)',
            result['targets'][0]['expr'],
        )
        self.assertEqual(
            {'X-ZXT-Service': {'values': ['observability']}}, result['http_headers']
        )
        self.assertEqual(original['url'], result['url'])
        self.assertEqual(original['token'], result['token'])
        self.assertEqual(result, monitoring(result))

    def test_plan_reads_explicit_files_and_keeps_compose_keys_objects_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            local = home / '.local'
            (local / 'configs').mkdir(parents=True)
            compose = local / 'compose.runtime.yaml'
            compose.write_text('name: tianji-compact\nservices: {}\n', encoding='utf8')
            config = local / 'configs/custom.yml'
            config.write_text(
                yaml.safe_dump({'tj': {'secret': 'TJ_tianji_tj.'}}), encoding='utf8'
            )
            (local / 'app.env').write_text(
                'TJ_INTERNAL_TOKEN=unchanged\n', encoding='utf8'
            )
            (local / 'objects').mkdir()
            video = local / 'objects/retained.mp4'
            video.write_bytes(b'preserved bytes')
            (local / 'signing.jks').write_bytes(b'preserved signing key bytes')
            snapshots = {
                path: path.read_bytes() for path in local.rglob('*') if path.is_file()
            }
            plan = candidates(home)
            self.assertEqual(
                {'configs/custom.yml', 'app.env'},
                {path.relative_to(local).as_posix() for path in plan},
            )
            self.assertEqual(
                'TJ_tianji_tj.', yaml.safe_load(plan[config][1])['zxt']['secret']
            )
            self.assertEqual(snapshots, {path: path.read_bytes() for path in snapshots})


if __name__ == '__main__':
    unittest.main()
