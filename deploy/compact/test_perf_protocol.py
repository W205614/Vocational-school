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

class SmokeContractTest(unittest.TestCase):
    def report(self):
        profiles = allocation(10)
        return {'protocol': 'smoke', 'runs': [
            {'users': 10, 'requestedSeconds': 30, 'elapsedSeconds': 30.5,
             'status': 'COMPLETED', 'invariants': True, 'queuesDrained': True,
             'contaminated': False, 'telemetryErrors': [], 'profiles': profiles,
             'workflowCounts': {name: 1 for name in profiles},
             'counts': {'query/profile:ok': 1, 'async/notes:ok': 1},
             'warmup': {'counts': {'query/profile:ok': 1}}}]}

    def test_short_smoke_checks_correctness_without_formal_capacity_thresholds(self):
        report = self.report()
        report['runs'][0]['latencyMs'] = {'query/profile:ok': {'count': 1, 'p95': 900}}
        self.assertEqual([], check_configuration(report))

    def test_failed_business_convergence_and_observations_are_rejected(self):
        for field, value in (('invariants', False), ('queuesDrained', False),
                             ('contaminated', True), ('telemetryErrors', [{'type': 'TimeoutError'}]),
                             ('status', 'INVALID'), ('elapsedSeconds', 29.9)):
            with self.subTest(field=field):
                report = self.report()
                report['runs'][0][field] = value
                self.assertTrue(check_configuration(report))
        report = self.report()
        del report['runs'][0]['contaminated']
        self.assertTrue(check_configuration(report))

    def test_empty_partial_and_failed_workloads_cannot_pass(self):
        for mutation in ('no-runs', 'extra-run', 'no-counts', 'no-workflows',
                         'missing-profile', 'unassigned-users', 'failed-query', 'failed-warmup'):
            with self.subTest(mutation=mutation):
                report = self.report()
                run = report['runs'][0]
                if mutation == 'no-runs': report['runs'] = []
                if mutation == 'extra-run': report['runs'].append(copy.deepcopy(run))
                if mutation == 'no-counts': run['counts'] = {}
                if mutation == 'no-workflows': run['workflowCounts']['trade'] = 0
                if mutation == 'missing-profile': del run['profiles']['coupon']
                if mutation == 'unassigned-users': run['users'] = 11
                if mutation == 'failed-query': run['counts']['query/profile:admission'] = 1
                if mutation == 'failed-warmup': run['warmup']['counts']['query/profile:system'] = 1
                self.assertTrue(check_configuration(report))


if __name__ == '__main__': unittest.main()
