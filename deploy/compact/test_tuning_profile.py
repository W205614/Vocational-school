import unittest
from tuning_profile import profile


class CandidateBudgetTest(unittest.TestCase):
    def test_allowed_candidates_keep_control_capacity_and_reject_unbounded_settings(
        self,
    ):
        self.assertEqual(
            128,
            profile(
                12,
                {
                    'ZXT_PERF_POOL_SIZE': '8',
                    'ZXT_PERF_OPERATION_THREADS': '2',
                    'ZXT_PERF_INTERVAL_MS': '250',
                },
            )['databaseConnectionCeiling'],
        )
        for value in (
            {'ZXT_PERF_POOL_SIZE': '32'},
            {'ZXT_PERF_OPERATION_THREADS': '20'},
            {'ZXT_PERF_INTERVAL_MS': '1'},
        ):
            with self.assertRaises(ValueError):
                profile(12, value)
        with self.assertRaises(ValueError):
            profile(19, {'ZXT_PERF_POOL_SIZE': '8'})


if __name__ == '__main__':
    unittest.main()
