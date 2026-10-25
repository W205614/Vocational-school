import unittest
from pathlib import Path
from retirement_policy import healthy_runtime, disposable_volume, owned_container


class RetirementPolicyTest(unittest.TestCase):
    def test_effective_topology_includes_optional_monitors(self):
        names = ['mysql', 'app-gateway', 'web-student', 'web-admin', 'prometheus']
        compose = {'services': {name: {} for name in names}}
        rows = [{'Id': name, 'Config': {'Labels': {'com.docker.compose.project': 'ours',
                'com.docker.compose.service': name}}, 'State': {'Running': True,
                'Health': {'Status': 'healthy'}}} for name in names]
        self.assertEqual(set(names), set(healthy_runtime(rows, compose, 'ours')))
        with self.assertRaises(RuntimeError): healthy_runtime(rows[:-1], compose, 'ours')
        rows[-1]['State']['Running'] = False
        with self.assertRaises(RuntimeError): healthy_runtime(rows, compose, 'ours')

    def test_volume_requires_ownership_no_reference_and_recreation_proof(self):
        volume = {'Name': 'test_data', 'Labels': {'com.docker.compose.project': 'test'}}
        proof = {'project': 'test', 'status': 'PASSED', 'syntheticOnly': True,
                 'recreationVerified': True, 'sourceCommit': 'commit'}
        self.assertTrue(disposable_volume(volume, {'test'}, set(), proof))
        self.assertFalse(disposable_volume(volume, {'test'}, {'test_data'}, proof))
        self.assertFalse(disposable_volume(volume, {'test'}, set(), {}))
        self.assertFalse(disposable_volume({'Name': 'anonymous', 'Labels': None}, {'test'}, set(), proof))
        self.assertFalse(disposable_volume(volume, {'test'}, set(), {**proof, 'syntheticOnly': False}))

    def test_workspace_and_project_are_both_required(self):
        root = Path(__file__).resolve().parents[2]
        row = {'Config': {'Labels': {'com.docker.compose.project': 'test',
               'com.docker.compose.project.config_files': str(root / 'deploy/compact/compose.yaml')}}}
        self.assertTrue(owned_container(row, {'test'}, root))
        self.assertFalse(owned_container(row, {'other'}, root))
        row['Config']['Labels']['com.docker.compose.project.config_files'] = str(root.parent / 'other/compose.yaml')
        self.assertFalse(owned_container(row, {'test'}, root))


if __name__ == '__main__': unittest.main()
