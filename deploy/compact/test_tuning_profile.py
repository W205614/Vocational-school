import unittest
from tuning_profile import profile


class CandidateBudgetTest(unittest.TestCase):
    def test_allowed_candidates_keep_control_capacity_and_reject_unbounded_settings(self):
        self.assertEqual(128,profile(12,{'TJ_PERF_POOL_SIZE':'8','TJ_PERF_OPERATION_THREADS':'2','TJ_PERF_INTERVAL_MS':'250'})['databaseConnectionCeiling'])
        for value in ({'TJ_PERF_POOL_SIZE':'32'},{'TJ_PERF_OPERATION_THREADS':'20'},{'TJ_PERF_INTERVAL_MS':'1'}):
            with self.assertRaises(ValueError):profile(12,value)
        with self.assertRaises(ValueError):profile(19,{'TJ_PERF_POOL_SIZE':'8'})


if __name__=='__main__':unittest.main()
