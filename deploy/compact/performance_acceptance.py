"""Derive acceptance from bound raw configuration reports, never handwritten pass flags."""
import argparse
import json
from pathlib import Path
from setup import LOCAL
from evidence import save_report, validate
from perf_protocol import check_configuration, memory_reduction

def compare(standalone, compact):
    failures = check_configuration(standalone) + check_configuration(compact)
    left = standalone.get('evidence', {}); right = compact.get('evidence', {})
    for field in ('sourceCommit', 'baseSnapshotFingerprint'):
        if not left.get(field) or left[field] != right.get(field): failures.append('pair mismatch: ' + field)
    for field in ('protocol', 'fixtureFingerprint', 'workload', 'workloadFingerprint'):
        if standalone.get(field) != compact.get(field): failures.append('pair mismatch: ' + field)
    for service in ('mysql', 'redis', 'rabbitmq', 'elasticsearch'):
        if not left.get('imageDigests', {}).get(service) or left['imageDigests'][service] != right.get('imageDigests', {}).get(service):
            failures.append('infrastructure image mismatch: ' + service)
    reduction = None
    try:
        reduction = memory_reduction(standalone, compact)
        if reduction < .2: failures.append('Java RSS reduction below 20 percent')
    except (ValueError, KeyError, TypeError): failures.append('RSS comparison unavailable')
    return {'status': 'PASSED' if not failures else 'FAILED', 'protocol': compact.get('protocol'),
            'javaMemoryReduction': reduction, 'failures': failures,
            'sourceCommit': right.get('sourceCommit'), 'completedRunsPerConfiguration': len(compact.get('runs', []))}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--standalone', required=True, type=Path); parser.add_argument('--compact', required=True, type=Path)
    parser.add_argument('--baseline-standalone', required=True, type=Path); parser.add_argument('--baseline-compact', required=True, type=Path)
    args = parser.parse_args()
    reports = {name: json.loads(getattr(args, name).read_text(encoding='utf8')) for name in ('standalone', 'compact', 'baseline_standalone', 'baseline_compact')}
    value = compare(reports['standalone'], reports['compact'])
    value['baseline'] = compare(reports['baseline_standalone'], reports['baseline_compact'])
    value['rawReports'] = {name: str(getattr(args, name)) for name in reports}
    expected = json.loads((LOCAL / 'release-run.json').read_text(encoding='utf8'))
    # The final compact report must be the current release's exact tested build and data.
    evidence = reports['compact'].get('evidence', {})
    for key in ('sourceCommit', 'imageDigests', 'configFingerprint', 'baseSnapshotFingerprint'):
        if evidence.get(key) != expected.get(key):
            value['status'] = 'FAILED'; value['failures'].append('release mismatch: ' + key)
    save_report(LOCAL, 'performance-acceptance', value)
    print(json.dumps({key: value[key] for key in ('status', 'javaMemoryReduction', 'failures')}, indent=2))
    return 0 if value['status'] == 'PASSED' else 1

if __name__ == '__main__': raise SystemExit(main())
