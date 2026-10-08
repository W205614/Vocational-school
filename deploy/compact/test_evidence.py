import datetime
import unittest
import tempfile,json
from pathlib import Path
from evidence import bind, validate, utcnow,save_report,verify_publication

class EvidenceBindingTest(unittest.TestCase):
    def test_generated_runtime_compose_requires_exact_captured_configuration(self):
        from unittest.mock import patch,Mock
        from config_equivalence import config_fingerprint
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);home=root/'deploy/compact';(home/'.local/configs').mkdir(parents=True)
            (home/'.local/configs/app.yml').write_text('pool: 8\n');(home/'compose.yaml').write_text('image: sha256:captured\n')
            captured={'sourceCommit':'source','configFingerprint':config_fingerprint(home)}
            def check(manifest):
                with patch('evidence.subprocess.check_output',side_effect=['head\n','','deploy/compact/compose.yaml\n','']),patch('evidence.subprocess.run',return_value=Mock(returncode=0)):
                    return verify_publication(root,'source',deployment_manifest=manifest)
            self.assertFalse(check(None));self.assertTrue(check(captured))
            self.assertFalse(check({**captured,'sourceCommit':'another-source'}))
            (home/'compose.yaml').write_text('image: sha256:unmeasured\n');self.assertFalse(check(captured))

    def test_wrong_commit_old_batch_failure_and_unbound_proof_are_rejected(self):
        expected = {'schemaVersion': 1, 'releaseRunId': 'batch-a', 'sourceCommit': 'commit-a',
                    'imageDigests': {'education': 'sha256:a'}, 'configFingerprint': 'config-a',
                    'baseSnapshotFingerprint': 'snapshot-a', 'createdAt': utcnow()}
        proof = bind({'status': 'PASSED'}, expected)
        self.assertTrue(validate(proof, expected))
        self.assertFalse(validate({'status': 'PASSED'}, expected))
        for key in ('releaseRunId', 'sourceCommit', 'imageDigests', 'configFingerprint', 'baseSnapshotFingerprint'):
            changed = {**expected, key: 'different'}
            self.assertFalse(validate(proof, changed), key)
        self.assertFalse(validate(bind({'status': 'FAILED'}, expected), expected))

    def test_future_and_pre_batch_reports_are_rejected(self):
        expected = {'schemaVersion': 1, 'releaseRunId': 'a', 'sourceCommit': 'a',
                    'imageDigests': {}, 'configFingerprint': 'a', 'baseSnapshotFingerprint': 'a', 'createdAt': utcnow()}
        proof = bind({'status': 'PASSED'}, expected, '2000-01-01T00:00:00+00:00')
        self.assertFalse(validate(proof, expected))

    def test_a_successful_retry_keeps_the_independent_failed_attempt(self):
        with tempfile.TemporaryDirectory() as directory:
            local=Path(directory)
            expected={'schemaVersion':1,'releaseRunId':'batch','sourceCommit':'commit','imageDigests':{},'configFingerprint':'config','baseSnapshotFingerprint':'snapshot','createdAt':utcnow()}
            (local/'release-run.json').write_text(json.dumps(expected),encoding='utf8')
            failed=save_report(local,'backend-result',{'status':'FAILED'})
            passed=save_report(local,'backend-result',{'status':'PASSED'})
            folder=local/'evidence/batch'
            self.assertEqual('PASSED',json.loads((folder/'backend-result.json').read_text())['status'])
            self.assertEqual('FAILED',json.loads((folder/'attempts/backend-result'/(failed['attemptId']+'.json')).read_text())['status'])
            self.assertNotEqual(failed['attemptId'],passed['attemptId'])
            with self.assertRaises(ValueError):save_report(local,'../other',{'status':'PASSED'})

if __name__ == '__main__': unittest.main()
