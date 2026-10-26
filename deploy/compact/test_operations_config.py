import copy
from pathlib import Path
import tempfile
import unittest
import yaml
from operations_config import observability, module_defaults, upgrade, MONITORS
from runtime_compose import active_compose


class OperationsConfigurationTest(unittest.TestCase):
    def test_static_discovery_probe_is_disabled_for_fresh_and_upgraded_module_defaults(self):
        original = {"spring": {"cloud": {"discovery": {"client": {"simple": {"instances": {"auth-service": [{"uri": "http://app-identity:25001/_modules/auth"}]}}}}}}}
        before = copy.deepcopy(original)
        for alias in ("auth", "gateway", "compact-identity"):
            updated = module_defaults(alias, original)
            client = updated["spring"]["cloud"]["discovery"]["client"]
            self.assertFalse(client["health-indicator"]["enabled"])
            self.assertEqual(before["spring"]["cloud"]["discovery"]["client"]["simple"], client["simple"])
            self.assertEqual(updated, module_defaults(alias, updated))
        self.assertEqual(before, original)

    def compose(self):
        names = ['mysql','redis','rabbitmq','elasticsearch','app-identity','app-commerce','app-education','app-support','app-gateway','web-student','web-admin']
        result = {'services': {name: {'image': 'sha256:preserved', 'environment': {'APP_PORT': str(24310 if name=='app-gateway' else 24001)}, 'volumes': []} for name in names}, 'volumes': {'original_search_volume': {}}}
        result['services']['elasticsearch']['volumes'] = ['original_search_volume:/data']
        return result

    def test_hardening_keeps_images_data_and_limits_observability(self):
        original = self.compose(); before = copy.deepcopy(original)
        result = observability(original, 1000)
        self.assertEqual(before, original)
        for name, service in before['services'].items(): self.assertEqual(service['image'],result['services'][name]['image'])
        self.assertEqual(['original_search_volume:/data'], result['services']['elasticsearch']['volumes'])
        self.assertEqual('unless-stopped', result['services']['mysql']['restart'])
        for name in MONITORS:
            self.assertTrue(result['services'][name]['ports'][0].startswith('127.0.0.1:'))
            self.assertEqual('3',result['services'][name]['logging']['options']['max-file'])
        self.assertIn('--storage.tsdb.retention.size=256MB',result['services']['prometheus']['command'])
        self.assertEqual(result, observability(result,1000))

    def test_incremental_upgrade_preserves_custom_settings_and_retains_originals(self):
        with tempfile.TemporaryDirectory() as directory:
            home=Path(directory); configs=home/'.local/configs'; configs.mkdir(parents=True)
            original = self.compose(); (home/'compose.yaml').write_text(yaml.safe_dump(original))
            gateway={'server':{'port':24310},'tj':{'gateway':{'proxy':{'trusted-addresses':['existing-proxy']}}}}
            (configs/'gateway.yml').write_text(yaml.safe_dump(gateway))
            backup=upgrade(home,'private-token',rabbit_hostname='original-node')
            self.assertEqual(original,yaml.safe_load((backup/'compose.yaml').read_text()))
            current=yaml.safe_load((configs/'gateway.yml').read_text())
            self.assertEqual(['existing-proxy'],current['tj']['gateway']['proxy']['trusted-addresses'])
            self.assertEqual('none',current['server']['forward-headers-strategy'])
            self.assertEqual('private-token',(home/'.local/observability/internal-token').read_text())
            self.assertNotIn('private-token',(home/'compose.yaml').read_text())
            self.assertEqual(original,yaml.safe_load((home/'compose.yaml').read_text()))
            self.assertEqual('original-node',yaml.safe_load(active_compose(home).read_text())['services']['rabbitmq']['hostname'])


if __name__=='__main__': unittest.main()
