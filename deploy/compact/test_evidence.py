import datetime
import unittest
from evidence import bind, validate, utcnow

class EvidenceBindingTest(unittest.TestCase):
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

if __name__ == '__main__': unittest.main()
