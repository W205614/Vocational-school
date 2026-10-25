"""Accept a single-host demo delivery without claiming production capacity.

Input reports retain their own execution provenance. In particular, a recovery
clone is not represented as having the production configuration fingerprint.
"""
import argparse
import json
from pathlib import Path
from evidence import digest, manifest, save_report, verify_publication


def check_reports(expected, backend, roles, recovery, history):
    apps = {key: value for key, value in expected['imageDigests'].items()
            if key.startswith(('app-', 'web-'))}
    checks = {
        'targeted-backend': backend.get('status') == 'PASSED'
        and backend.get('sourceCommit') == expected['sourceCommit']
        and backend.get('tests', 0) > 0
        and all(backend.get(key) == 0 for key in ('failures', 'errors', 'skipped'))
        and bool(backend.get('requestedClasses')),
        'all-role-experience': roles.get('status') == 'PASSED'
        and roles.get('sourceCommit') == expected['sourceCommit']
        and roles.get('imageDigests') == apps
        and set(roles.get('passedRoles', [])) == {'visitor', 'student', 'teacher', 'admin'}
        and roles.get('requiredScenariosPassed') is True
        and roles.get('failed') == 0,
        'independent-recovery': recovery.get('status') == 'PASSED'
        and recovery.get('tableContentChecksumsMatched') is True
        and recovery.get('brokerFileContentsMatched') is True
        and recovery.get('redisValuesAndExpiryMatched') is True,
        'incremental-migration': recovery.get('migrationUpgradeProof', {}).get('status') == 'PASSED'
        and recovery.get('migrationUpgradeProof', {}).get('sourceCommit') == expected['sourceCommit'],
        'history-preservation': history.get('status') == 'PASSED'
        and history.get('sourceCommit') == expected['sourceCommit']
        and history.get('originalIdentifiersPreserved') is True
        and history.get('mediaHashesPreserved') is True,
    }
    return checks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('backend', 'roles', 'recovery', 'history'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    from setup import LOCAL, ROOT, COMPOSE
    expected = json.loads((LOCAL / 'release-run.json').read_text(encoding='utf8'))
    values = {name: json.loads(getattr(args, name).read_text(encoding='utf8'))
              for name in ('backend', 'roles', 'recovery', 'history')}
    checks = check_reports(expected, **values)
    live = manifest(LOCAL, COMPOSE, ROOT, expected['snapshotPath'])
    checks['live-source-images-configuration'] = all(live[key] == expected[key] for key in
        ('sourceCommit', 'imageDigests', 'configFingerprint', 'baseSnapshotFingerprint'))
    checks['publication-source-equivalence'] = verify_publication(ROOT, expected['sourceCommit'])
    report = {'status': 'PASSED' if all(checks.values()) else 'FAILED',
              'acceptanceScope': 'single-host-demo', 'checks': checks,
              'performanceValidation': 'NOT_VALIDATED',
              'realProvidersValidation': 'NOT_REQUESTED',
              'inputs': {name: {'path': str(getattr(args, name).resolve()),
                               'sha256': digest(getattr(args, name))} for name in values}}
    save_report(LOCAL, 'release-gate', report)
    print(json.dumps({key: report[key] for key in ('status', 'acceptanceScope', 'checks')}, indent=2))
    return 0 if report['status'] == 'PASSED' else 1


if __name__ == '__main__': raise SystemExit(main())
