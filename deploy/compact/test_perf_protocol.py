import copy
import unittest
from perf_protocol import allocation, schedule, check_configuration, memory_reduction, QUERY_KINDS, ASYNC_KINDS

class PerformanceContractTest(unittest.TestCase):
    def report(self):
        return {'protocol': 'perf-3h-v1', 'runs': [
            {'users': users, 'requestedSeconds': seconds, 'repeat': repeat,
             'elapsedSeconds': seconds, 'status': 'COMPLETED', 'invariants': True, 'contaminated': False,
             'profiles': allocation(users), 'workflowCounts': {name:120 for name in allocation(users)},
             'counts': {'query/search:ok': 120, 'async/notes:ok': 120},
             'latencyMs': {**{'query/'+name+':ok': {'count':120,'p95':490} for name in QUERY_KINDS},
                           **{'async/'+name+':ok': {'count':120,'p95':2900} for name in ASYNC_KINDS}},
             'javaRssMedianBytes': 1000}
            for users, seconds, repeat in schedule('perf-3h-v1')]}

    def test_four_configurations_take_exactly_three_hours(self):
        self.assertEqual(10800, 4 * sum(seconds for _, seconds, _ in schedule('perf-3h-v1')))
        for users in (10, 50, 100, 200):
            counts = allocation(users)
            self.assertEqual(users, sum(counts.values()))
            self.assertTrue(all(counts.values()))
        self.assertEqual({'query': 100, 'notes': 20, 'progress': 30, 'exam': 20, 'trade': 20, 'coupon': 10}, allocation(200))

    def test_rejected_requests_cannot_hide_behind_fast_successes(self):
        report = self.report()
        self.assertEqual([], check_configuration(report))
        report['runs'][-1]['counts']['query/search:admission'] = 1
        self.assertTrue(check_configuration(report))

    def test_shortened_missing_and_old_protocol_runs_fail(self):
        for mutation in ('duration', 'missing', 'protocol', 'samples', 'rss'):
            report = self.report()
            if mutation == 'duration': report['runs'][-1]['elapsedSeconds'] = 599
            if mutation == 'missing': report['runs'].pop()
            if mutation == 'protocol': report['protocol'] = 'handwritten-pass'
            if mutation == 'samples': report['runs'][-1]['latencyMs']['query/search:ok']['count'] = 99
            if mutation == 'rss': report['runs'][-1]['javaRssMedianBytes'] = None
            self.assertTrue(check_configuration(report), mutation)

    def test_memory_uses_three_run_medians(self):
        standalone = self.report(); compact = copy.deepcopy(standalone)
        for run in compact['runs']: run['javaRssMedianBytes'] = 750
        self.assertEqual(.25, memory_reduction(standalone, compact))

if __name__ == '__main__': unittest.main()
