import copy
import json
from pathlib import Path
import tempfile
import unittest

from retire_test_volumes import apply_plan, make_plan, sha


class FakeDocker:
    def __init__(self, volumes):
        self.volumes = {value['Name']: copy.deepcopy(value) for value in volumes}
        self.items = []
        self.removed = []
        self.reads = 0
        self.attach_on_read = None

    def containers(self, project=None):
        self.reads += 1
        result = copy.deepcopy(self.items)
        if self.attach_on_read and self.reads >= self.attach_on_read[0]:
            result.append({'State': {'Running': False}, 'Mounts': [
                {'Type': 'volume', 'Name': self.attach_on_read[1]}]})
        return result

    def volume(self, name):
        return copy.deepcopy(self.volumes.get(name))

    def remove(self, name):
        self.removed.append(name)
        self.volumes.pop(name)


class TestVolumeRetirementTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.primary = self.root / 'primary'
        self.home = self.root / 'clone'
        self.local = self.home / '.local'
        self.local.mkdir(parents=True)
        (self.primary / '.local').mkdir(parents=True)
        self.project = 'tianji-demo-test'
        self.names = [self.project + '_compact_mysql', self.project + '_compact_redis']
        self.write(self.primary / '.local/accounts.json', {'student': {'id': '1'}, 'teacher': {'id': '2'}, 'admin': {'id': '3'}})
        self.write(self.local / 'init-state.json', {'phase': 'COMPLETE', 'clone': None})
        self.write(self.local / 'accounts.json', {'student': {'id': '31'}, 'teacher': {'id': '32'}, 'admin': {'id': '33'}})
        self.write(self.local / 'browser-fixture.json', {'marker': 'synthetic-course', 'course': '101'})
        self.write(self.local / 'browser-result.json', {'status': 'PASSED', 'passed': 3, 'failed': 0})
        (self.local / 'compose.runtime.yaml').write_text(
            'name: ' + self.project + '\nservices: {}\nvolumes:\n  compact_mysql: {}\n  compact_redis: {}\n', encoding='utf8')
        self.apps = {'app-gateway': 'sha256:gateway', 'web-student': 'sha256:student', 'web-admin': 'sha256:admin'}
        self.write(self.local / 'build-source.json', {'sourceCommit': 'source', 'imageDigests': self.apps})
        self.gate_path = self.primary / '.local/gate.json'
        self.expected_path = self.primary / '.local/release-run.json'
        self.write(self.gate_path, {'status': 'PASSED'})
        self.write(self.expected_path, {'sourceCommit': 'source'})
        self.history_path = self.primary / '.local/history.json'
        self.write(self.history_path, {'status': 'PASSED', 'sourceCommit': 'source',
                                     'originalIdentifiersPreserved': True, 'mediaHashesPreserved': True})
        self.context = {'expected': {'sourceCommit': 'source', 'imageDigests': {**self.apps, 'mysql': 'sha256:mysql'}},
                        'gate': {'inputs': {'history': self.ref(self.history_path)}},
                        'files': [self.ref(self.gate_path), self.ref(self.expected_path)]}
        self.proof_path = self.root / 'disposal-proof.json'
        self.proof = {'schemaVersion': 1, 'project': self.project, 'home': str(self.home),
                      'kind': 'fresh-synthetic', 'noUniqueUserData': True, 'sourceCommit': 'source',
                      'sourceBackupManifestSha': None, 'volumeNames': self.names,
                      'proofPath': str(self.local / 'browser-result.json'),
                      'proofSha256': sha(self.local / 'browser-result.json'),
                      'initState': self.ref(self.local / 'init-state.json'),
                      'accounts': self.ref(self.local / 'accounts.json'),
                      'fixture': self.ref(self.local / 'browser-fixture.json')}
        self.write(self.proof_path, self.proof)
        self.docker = FakeDocker([self.volume(name, self.project, name.split('_', 1)[1]) for name in self.names])

    @staticmethod
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value), encoding='utf8')

    @staticmethod
    def ref(path):
        return {'path': str(path.resolve()), 'sha256': sha(path)}

    @staticmethod
    def volume(name, project=None, key='compact_mysql'):
        return {'Name': name, 'Driver': 'local', 'Scope': 'local', 'Options': None,
                'CreatedAt': '2026-10-25T00:00:00Z', 'Labels': None if project is None else
                {'com.docker.compose.project': project, 'com.docker.compose.volume': key}}

    def plan(self, names=None, verifier=lambda path: {}):
        return make_plan([self.project], names or self.names, [self.proof_path], self.context,
                         self.root, self.primary, self.docker, verifier)

    def apply(self, plan, verifier=lambda path: {}):
        return apply_plan(plan, self.context, self.root, self.primary, self.docker, verifier)

    def test_explicit_synthetic_plan_does_not_mutate_and_apply_retires_only_named_volumes(self):
        plan = self.plan()
        self.assertEqual(self.names, [row['name'] for row in plan['removable']])
        self.assertFalse(plan['preserved']); self.assertFalse(self.docker.removed)
        result = self.apply(plan)
        self.assertEqual(self.names, result['removedVolumes'])
        self.assertFalse(result['currentDeploymentVolumesRemoved'])

    def test_current_foreign_anonymous_and_stopped_container_references_are_protected(self):
        extra = [('tianji-compact_compact_mysql', 'tianji-compact'), ('other_mysql', 'other'), ('anonymous_hash', None)]
        for name, project in extra:
            self.docker.volumes[name] = self.volume(name, project)
        self.docker.items = [{'State': {'Running': False}, 'Mounts': [{'Type': 'volume', 'Name': self.names[0]}]}]
        plan = self.plan(self.names + [name for name, _ in extra])
        self.assertEqual([self.names[1]], [row['name'] for row in plan['removable']])
        self.assertEqual(4, len(plan['preserved']))
        self.apply(plan)
        self.assertEqual([self.names[1]], self.docker.removed)

    def test_primary_or_unknown_project_cannot_be_selected_even_if_no_containers_exist(self):
        for project in ['tianji-compact', 'another-project']:
            with self.assertRaises(RuntimeError):
                make_plan([project], self.names, [], self.context, self.root, self.primary, self.docker)

    def test_missing_explicit_no_unique_data_statement_retains_every_volume(self):
        del self.proof['noUniqueUserData']; self.write(self.proof_path, self.proof)
        plan = self.plan(); self.assertFalse(plan['removable']); self.assertEqual(2, len(plan['preserved']))
        self.assertIn('unique user data', plan['invalidProofs'][0]['reason'])

    def test_conflicting_duplicate_proofs_cannot_override_a_preservation_decision(self):
        other = self.root / 'another-proof.json'; self.write(other, self.proof)
        plan = make_plan([self.project], self.names, [self.proof_path, other], self.context,
                         self.root, self.primary, self.docker)
        self.assertFalse(plan['removable']); self.assertEqual(2, len(plan['preserved']))

    def test_inherited_or_modified_fixture_cannot_be_declared_fresh_synthetic(self):
        self.write(self.local / 'init-state.json', {'phase': 'COMPLETE', 'clone': 'historical-backup'})
        self.proof['initState'] = self.ref(self.local / 'init-state.json'); self.write(self.proof_path, self.proof)
        self.assertFalse(self.plan()['removable'])
        self.write(self.local / 'init-state.json', {'phase': 'COMPLETE', 'clone': None})
        self.proof['initState'] = self.ref(self.local / 'init-state.json'); self.write(self.proof_path, self.proof)
        self.write(self.local / 'browser-fixture.json', {'marker': 'replaced', 'course': '102'})
        self.assertFalse(self.plan()['removable'])

    def test_shared_accounts_and_wrong_source_images_cannot_authorize_retirement(self):
        self.write(self.local / 'accounts.json', {'student': {'id': '1'}, 'teacher': {'id': '32'}, 'admin': {'id': '33'}})
        self.proof['accounts'] = self.ref(self.local / 'accounts.json'); self.write(self.proof_path, self.proof)
        self.assertFalse(self.plan()['removable'])
        self.write(self.local / 'accounts.json', {'student': {'id': '31'}, 'teacher': {'id': '32'}, 'admin': {'id': '33'}})
        self.proof['accounts'] = self.ref(self.local / 'accounts.json'); self.write(self.proof_path, self.proof)
        self.write(self.local / 'build-source.json', {'sourceCommit': 'old', 'imageDigests': self.apps})
        self.assertFalse(self.plan()['removable'])

    def test_external_storage_and_volume_names_outside_manifest_are_retained(self):
        self.docker.volumes[self.names[0]]['Options'] = {'device': '/user-data'}
        self.proof['volumeNames'] = [self.names[0]]; self.write(self.proof_path, self.proof)
        plan = self.plan(); self.assertFalse(plan['removable']); self.assertEqual(2, len(plan['preserved']))

    def test_reference_or_proof_change_after_plan_blocks_every_removal(self):
        plan = self.plan(); self.docker.items = [{'Mounts': [{'Type': 'volume', 'Name': self.names[0]}]}]
        with self.assertRaises(RuntimeError): self.apply(plan)
        self.assertFalse(self.docker.removed)
        self.docker.items = []
        self.proof['noUniqueUserData'] = False; self.write(self.proof_path, self.proof)
        with self.assertRaises(RuntimeError): self.apply(plan)
        self.assertFalse(self.docker.removed)

    def test_immediate_reference_recheck_stops_later_removal(self):
        plan = self.plan()
        # read 1 produced plan; read 2 is apply preflight; reads 3/4 precede individual rm.
        self.docker.attach_on_read = (4, self.names[1])
        with self.assertRaisesRegex(RuntimeError, 'gained a container reference'): self.apply(plan)
        self.assertEqual([self.names[0]], self.docker.removed)
        self.assertIn(self.names[1], self.docker.volumes)

    def test_tampered_selection_and_changed_gate_are_rejected(self):
        plan = self.plan(); plan['removable'][0]['snapshot']['Labels']['com.docker.compose.project'] = 'other'
        with self.assertRaises(RuntimeError): self.apply(plan)
        self.assertFalse(self.docker.removed)
        plan = self.plan(); self.write(self.gate_path, {'status': 'FAILED'})
        with self.assertRaises(RuntimeError): self.apply(plan)
        self.assertFalse(self.docker.removed)

    def recovery_fixture(self):
        backup = self.root / 'source-backup'; backup.mkdir()
        saved = {'quiesced': True, 'mediaFiles': {'video.webm': 'digest'}}
        self.write(backup / 'manifest.json', saved)
        self.write(self.local / 'init-state.json', {'phase': 'COMPLETE', 'clone': str(backup)})
        report_path = self.local / 'recovery-result.json'
        self.write(report_path, {'status': 'PASSED', 'backup': str(backup), 'rawRestoreProof':
            {'status': 'PASSED', 'tableCountsMatched': True, 'tableContentChecksumsMatched': True,
             'redisValuesAndExpiryMatched': True, 'brokerFileContentsMatched': True, 'mediaFileCount': 1}})
        self.proof.update(kind='recovery-clone', sourceBackup=str(backup),
                          sourceBackupManifestSha=sha(backup / 'manifest.json'), syntheticWritesOnly=True,
                          proofPath=str(report_path), proofSha256=sha(report_path),
                          initState=self.ref(self.local / 'init-state.json'), historyProof=self.ref(self.history_path))
        self.write(self.proof_path, self.proof)
        return lambda path: saved

    def test_recovery_clone_needs_verified_source_and_real_raw_restore_and_history(self):
        verify = self.recovery_fixture()
        plan = self.plan(verifier=verify); self.assertEqual(2, len(plan['removable']))
        self.assertEqual(self.names, self.apply(plan, verify)['removedVolumes'])

    def test_old_source_raw_recovery_can_be_disposed_without_claiming_final_images(self):
        verify = self.recovery_fixture()
        report_path = self.local / 'recovery-result.json'
        report = json.loads(report_path.read_text())
        old_apps = {name: 'old-' + image for name, image in self.apps.items()}
        report['rawRestoreProof'].update(sourceCommit='previous-source', imageDigests={**old_apps, 'mysql': 'old-mysql'})
        self.write(report_path, report); self.proof['proofSha256'] = sha(report_path)
        self.write(self.proof_path, self.proof)
        self.write(self.local / 'build-source.json', {'sourceCommit': 'previous-source', 'imageDigests': old_apps})
        plan = self.plan(verifier=verify)
        self.assertEqual(2, len(plan['removable']))
        self.assertEqual('source', self.proof['sourceCommit'])
        self.assertEqual(self.names, self.apply(plan, verify)['removedVolumes'])

    def test_old_source_recovery_image_mismatch_or_failed_raw_proof_is_retained(self):
        verify = self.recovery_fixture()
        report_path = self.local / 'recovery-result.json'
        report = json.loads(report_path.read_text())
        old_apps = {name: 'old-' + image for name, image in self.apps.items()}
        report['rawRestoreProof'].update(sourceCommit='previous-source', imageDigests=old_apps)
        self.write(report_path, report); self.proof['proofSha256'] = sha(report_path)
        self.write(self.proof_path, self.proof)
        wrong_apps = {**old_apps, 'app-gateway': 'different-image'}
        self.write(self.local / 'build-source.json', {'sourceCommit': 'previous-source', 'imageDigests': wrong_apps})
        self.assertFalse(self.plan(verifier=verify)['removable'])
        self.write(self.local / 'build-source.json', {'sourceCommit': 'previous-source', 'imageDigests': old_apps})
        report['rawRestoreProof']['status'] = 'FAILED'
        self.write(report_path, report); self.proof['proofSha256'] = sha(report_path)
        self.write(self.proof_path, self.proof)
        self.assertFalse(self.plan(verifier=verify)['removable'])

    def test_clone_unique_writes_missing_history_or_failed_restore_are_never_disposable(self):
        verify = self.recovery_fixture()
        for field, bad in [('syntheticWritesOnly', False), ('sourceBackupManifestSha', '0' * 64),
                           ('historyProof', {'path': str(self.history_path), 'sha256': '0' * 64})]:
            previous = self.proof[field]; self.proof[field] = bad; self.write(self.proof_path, self.proof)
            self.assertFalse(self.plan(verifier=verify)['removable']); self.proof[field] = previous
        report = json.loads((self.local / 'recovery-result.json').read_text())
        report['rawRestoreProof']['tableContentChecksumsMatched'] = False
        self.write(self.local / 'recovery-result.json', report)
        self.proof['proofSha256'] = sha(self.local / 'recovery-result.json'); self.write(self.proof_path, self.proof)
        self.assertFalse(self.plan(verifier=verify)['removable'])


if __name__ == '__main__': unittest.main()
