import copy
import unittest
from application_layout import application_mounts, application_dependencies, isolate_applications, GROUPS


class ApplicationLayoutTest(unittest.TestCase):
    def test_only_identity_can_read_signing_key_and_only_support_writes_objects(self):
        for group, aliases in GROUPS.items():
            mounts = application_mounts(group)
            self.assertEqual(group == 'identity', any('signing.jks' in item for item in mounts))
            self.assertEqual(group == 'support', any('/run/objects' in item for item in mounts))
            configs = [item.split(':')[0].rsplit('/', 1)[1][:-4] for item in mounts if '/configs/' in item]
            self.assertEqual({group, *aliases}, set(configs))
            self.assertNotIn('./.local/configs:/run/compact/configs:ro', mounts)

    def test_layout_upgrade_is_idempotent_and_preserves_volumes_images_ports_and_custom_mounts(self):
        shared = ['./.local/configs:/run/compact/configs:ro', './.local/signing.jks:/run/compact/signing.jks:ro', './.local/objects:/run/objects', '/audit:/audit:ro']
        original = {'services': {'app-' + group: {'image': 'sha256:fixture', 'ports': ['127.0.0.1:24001:24001'], 'volumes': shared,
                    'depends_on': {key: {'condition': 'service_healthy'} for key in ('mysql','redis','rabbitmq','elasticsearch')}} for group in GROUPS},
                    'volumes': {'original-db': {}}, 'name': 'fixture'}
        before=copy.deepcopy(original);updated=isolate_applications(original)
        self.assertEqual(before,original);self.assertEqual(updated,isolate_applications(updated));self.assertEqual(original['volumes'],updated['volumes'])
        for group in GROUPS:
            service=updated['services']['app-'+group]
            self.assertEqual('sha256:fixture',service['image']);self.assertIn('/audit:/audit:ro',service['volumes'])
            self.assertEqual(group=='support','elasticsearch' in service['depends_on'])

    def test_custom_long_form_binds_are_not_silently_rewritten(self):
        with self.assertRaises(RuntimeError):
            isolate_applications({'services': {'app-identity': {'volumes': [{'source': '/custom', 'target': '/run/compact/configs'}]}}})


if __name__=='__main__': unittest.main()
