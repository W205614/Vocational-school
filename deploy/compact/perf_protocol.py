"""Versioned performance contracts; no runtime or credential side effects on import."""
from statistics import median
import math

PROTOCOLS = {
    'perf-3h-v1': [(10, 300, 1), (50, 300, 1), (100, 300, 1), (200, 600, 3)],
    'perf-long-v1': [(users, 1800, 3) for users in (10, 50, 100, 200)],
}
PROFILES = {'query': 50, 'notes': 10, 'progress': 15, 'exam': 10, 'trade': 10, 'coupon': 5}
QUERY_KINDS = ('profile', 'lessons', 'notes', 'course', 'search', 'learning', 'exam-attempt', 'order')
ASYNC_KINDS = ('notes', 'progress', 'exam-start', 'exam-submit', 'exam-grade', 'order-create', 'payment', 'refund-apply', 'refund-approve', 'refund', 'coupon')

def schedule(protocol):
    return [(users, seconds, repeat + 1)
            for users, seconds, repeats in PROTOCOLS[protocol]
            for repeat in range(repeats)]

def allocation(users):
    if users < len(PROFILES):
        raise ValueError('Mixed workload requires at least six concurrent users')
    counts = {name: max(1, math.floor(users * weight / 100)) for name, weight in PROFILES.items()}
    while sum(counts.values()) < users:
        name = max(PROFILES, key=lambda name: users * PROFILES[name] / 100 - counts[name])
        counts[name] += 1
    while sum(counts.values()) > users:
        name = max((name for name in PROFILES if counts[name] > 1),
                   key=lambda name: counts[name] - users * PROFILES[name] / 100)
        counts[name] -= 1
    return counts

def check_run(run, minimum_samples=100):
    failures = []
    if run.get('status') != 'COMPLETED':
        failures.append('measurement incomplete')
    if run.get('contaminated'):
        failures.append('background workload changed')
    for kind, count in run.get('counts', {}).items():
        if not kind.endswith(':ok') and count:
            failures.append('unexpected outcome: ' + kind)
    for prefix, limit in [('query/', 500), ('async/', 3000)]:
        matching = {name: value for name, value in run.get('latencyMs', {}).items()
                    if name.startswith(prefix) and name.endswith(':ok')}
        if not matching:
            failures.append('missing ' + prefix + ' samples')
        for name, value in matching.items():
            if value.get('count', 0) < minimum_samples:
                failures.append('insufficient samples: ' + name)
            if value.get('p95') is None or value['p95'] > limit:
                failures.append('latency target failed: ' + name)
        required = QUERY_KINDS if prefix == 'query/' else ASYNC_KINDS
        for name in required:
            if prefix + name + ':ok' not in matching:
                failures.append('missing endpoint coverage: ' + prefix + name)
    if run.get('users') == 200 and run.get('javaRssMedianBytes') is None:
        failures.append('missing stable RSS evidence')
    if run.get('invariants') is not True:
        failures.append('business invariants unverified')
    if set(run.get('profiles', {})) != set(PROFILES):
        failures.append('mixed workload incomplete')
    if any(run.get('workflowCounts', {}).get(profile, 0) < minimum_samples for profile in PROFILES):
        failures.append('insufficient successful workflows')
    return failures

def check_configuration(report):
    protocol = report.get('protocol')
    if protocol not in PROTOCOLS:
        return ['unknown protocol']
    expected = schedule(protocol)
    runs = report.get('runs', [])
    actual = [(run.get('users'), run.get('requestedSeconds'), run.get('repeat')) for run in runs]
    failures = []
    if actual != expected:
        failures.append('schedule mismatch')
    for run in runs:
        if any(value for key,value in run.get('warmup',{}).get('counts',{}).items() if not key.endswith(':ok')):
            failures.append('warm-up correctness failed')
        if run.get('elapsedSeconds', 0) < run.get('requestedSeconds', 1):
            failures.append('measurement shortened')
        if run.get('users') == 200:
            failures.extend(check_run(run))
        elif run.get('status') != 'COMPLETED' or any(
                value for key, value in run.get('counts', {}).items() if not key.endswith(':ok')):
            failures.append('low concurrency correctness failed')
        if run.get('users')!=200 and (run.get('contaminated') or run.get('invariants') is not True):
            failures.append('low concurrency invariants or environment failed')
    return failures

def memory_reduction(standalone, compact):
    def rss(report):
        values = [run['javaRssMedianBytes'] for run in report['runs'] if run['users'] == 200]
        if len(values) != 3 or any(value is None or value <= 0 for value in values):
            raise ValueError('Three valid 200-user RSS samples required')
        return median(values)
    return 1 - rss(compact) / rss(standalone)
