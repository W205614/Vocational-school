"""Exercise migration, independent homes and relative bind paths with real Compose parsing."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import yaml
from runtime_compose import active_compose, runtime_compose, compose_command, verify_migration, equivalent_commands


class RuntimeComposeTest(unittest.TestCase):
    def test_legacy_migration_preserves_bytes_and_does_not_rewrite_template(self):
        with tempfile.TemporaryDirectory() as directory:
            home=Path(directory)
            original=b'services:\n  rabbitmq:\n    image: rabbitmq:4.1-management\n    hostname: original-node\n'
            (home/'compose.yaml').write_bytes(original)
            runtime=runtime_compose(home)
            self.assertEqual(original,runtime.read_bytes())
            self.assertEqual(runtime,active_compose(home))
            (home/'compose.yaml').write_text('public template changed')
            self.assertEqual(original,runtime_compose(home).read_bytes())

    def test_parsing_keeps_bind_mounts_ports_images_and_broker_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            home=Path(directory);(home/'.env').write_text('')
            (home/'.local/objects').mkdir(parents=True)
            value={'name':'runtime-audit','services':{'broker':{'image':'rabbitmq:4.1-management','hostname':'kept-node','ports':['127.0.0.1:29373:5672'],'volumes':['./.local/objects:/objects','original_data:/data']}},'volumes':{'original_data':{}}}
            (home/'compose.yaml').write_text(yaml.safe_dump(value))
            runtime=verify_migration(home,'runtime-audit')
            parsed=json.loads(subprocess.check_output(compose_command(home,'runtime-audit')+['config','--format','json'],text=True))
            broker=parsed['services']['broker']
            self.assertEqual(str((home/'.local/objects').resolve()),broker['volumes'][0]['source'])
            self.assertEqual('kept-node',broker['hostname'])
            self.assertEqual('runtime-audit_original_data',parsed['volumes']['original_data']['name'])
            self.assertEqual(runtime,active_compose(home))
            self.assertEqual('29373',broker['ports'][0]['published'])
            legacy=['docker','compose','-p','runtime-audit','-f',str(home/'compose.yaml'),'--env-file',str(home/'.env')]
            self.assertTrue(equivalent_commands(legacy,compose_command(home,'runtime-audit'),home))
            foreign=legacy.copy();foreign[foreign.index('-p')+1]='other-project'
            self.assertFalse(equivalent_commands(foreign,compose_command(home,'runtime-audit'),home))
            value['services']['broker']['hostname']='different-node'
            (home/'compose.yaml').write_text(yaml.safe_dump(value))
            self.assertFalse(equivalent_commands(legacy,compose_command(home,'runtime-audit'),home))

    def test_new_homes_are_independent_and_existing_runtime_takes_precedence(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);a=root/'a';b=root/'b'
            for home in (a,b):
                command=compose_command(home,'test-'+home.name)
                self.assertEqual(str(home.resolve()),command[command.index('--project-directory')+1])
                self.assertEqual(home/'.local/compose.runtime.yaml',active_compose(home))
            self.assertNotEqual(active_compose(a),active_compose(b))


if __name__=='__main__':unittest.main()
