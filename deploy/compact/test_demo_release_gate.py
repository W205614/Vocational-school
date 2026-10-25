import unittest
from copy import deepcopy
from demo_release_gate import check_reports


class DemoReleaseGateTest(unittest.TestCase):
    def reports(self):
        expected = {'sourceCommit': 'source', 'imageDigests': {'app-gateway': 'sha256:a', 'mysql': 'sha256:b'}}
        backend = {'status': 'PASSED', 'sourceCommit': 'source', 'tests': 3, 'failures': 0,
                   'errors': 0, 'skipped': 0, 'requestedClasses': ['ChangedPathTest']}
        roles = {'status': 'PASSED', 'sourceCommit': 'source', 'imageDigests': {'app-gateway': 'sha256:a'},
                 'passedRoles': ['visitor', 'student', 'teacher', 'admin'],
                 'requiredScenariosPassed': True, 'failed': 0}
        recovery = {'status': 'PASSED', 'tableContentChecksumsMatched': True,
                    'brokerFileContentsMatched': True, 'redisValuesAndExpiryMatched': True,
                    'migrationUpgradeProof': {'status': 'PASSED', 'sourceCommit': 'source'}}
        history = {'status': 'PASSED', 'sourceCommit': 'source',
                   'originalIdentifiersPreserved': True, 'mediaHashesPreserved': True}
        return [expected, backend, roles, recovery, history]

    def test_demo_needs_no_capacity_claim_but_requires_every_role_and_recovery(self):
        rows = self.reports()
        self.assertTrue(all(check_reports(*rows).values()))
        for index, key, bad in [(1, 'sourceCommit', 'old'), (1, 'skipped', 1),
                                (2, 'passedRoles', ['student']), (2, 'imageDigests', {}),
                                (3, 'brokerFileContentsMatched', False),
                                (4, 'mediaHashesPreserved', False)]:
            damaged = deepcopy(rows); damaged[index][key] = bad
            self.assertFalse(all(check_reports(*damaged).values()))
        rows[3]['migrationUpgradeProof']['sourceCommit'] = 'old'
        self.assertFalse(all(check_reports(*rows).values()))


if __name__ == '__main__': unittest.main()
