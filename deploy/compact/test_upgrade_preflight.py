from pathlib import Path
import tempfile
import unittest
import sys
from unittest.mock import patch
import yaml
import setup
sys.path.insert(0,str(setup.SOURCE.parent/'operations'))
import upgrade_existing


class UpgradePreflightTest(unittest.TestCase):
    def test_missing_monitor_image_fails_before_stopping_apps_or_migrating(self):
        with tempfile.TemporaryDirectory() as directory:
            home=Path(directory);local=home/'.local';candidate=local/'candidates/source';backup=local/'backup/snapshot'
            candidate.mkdir(parents=True);backup.mkdir(parents=True)
            compose={'services':{'app-gateway':{'image':'gateway','environment':{'APP_PORT':'24310'}}},'volumes':{}}
            (home/'compose.yaml').write_text(yaml.safe_dump(compose))
            with patch.object(setup,'BASE',home),patch.object(setup,'LOCAL',local),patch.object(setup,'secrets_config',return_value=({'ACCEPTANCE_INTERNAL_TOKEN':'token','ACCEPTANCE_GRAFANA_PASSWORD':'password'},{})),patch.object(setup,'configure_acceptance') as configure,patch.object(upgrade_existing,'load_upgrade',return_value={'sourceCommit':'a'*40,'imageDigests':{}}),patch('runtime_backup.verify',return_value={'quiesced':True}),patch.object(upgrade_existing.subprocess,'check_output',side_effect=RuntimeError('missing monitor')):
                with self.assertRaisesRegex(RuntimeError,'missing monitor'):upgrade_existing.deploy(candidate,backup)
            configure.assert_not_called()
            self.assertTrue((backup/'publication-pin.json').is_file())


if __name__=='__main__':unittest.main()
