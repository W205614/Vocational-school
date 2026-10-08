"""Require every socket/connector case to execute, preserving each attempt."""
import json
import os
import re
import shutil
import subprocess
import uuid
from setup import LOCAL, ROOT, secrets_config


def main():
    values, _ = secrets_config()
    env = {**os.environ, 'TJ_INTERNAL_TOKEN': values['ACCEPTANCE_INTERNAL_TOKEN']}
    env.setdefault('JAVA_HOME', 'E:/Program Files/jdk')
    tests = ['LocalModuleTransportTest', 'ModuleGuardTest']
    folder = LOCAL / 'transport-tests' / uuid.uuid4().hex
    folder.mkdir(parents=True, exist_ok=False)
    command = [shutil.which('mvn') or 'E:/download/apache-maven-3.9.4/bin/mvn.cmd',
               '-B', '-Pcompact', '-pl', 'tj-compact/runtime', '-am',
               '-Dtest=' + ','.join(tests), '-Dsurefire.failIfNoSpecifiedTests=false', 'test']
    with (folder / 'tests.log').open('wb') as output:
        code = subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT).returncode
    log = (folder / 'tests.log').read_text(encoding='utf8', errors='replace')
    summaries = re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+).* -- in ([\w.$]+)', log)
    executed = [row[4] for row in summaries]
    missing = [name for name in tests if not any(value.endswith('.' + name) for value in executed)]
    counts = [sum(int(row[index]) for row in summaries) for index in range(4)]
    success = code == 0 and not missing and bool(summaries) and counts[0] > 0 and not any(counts[1:])
    report = dict(status='PASSED' if success else 'FAILED', requestedClasses=tests,
                  executedClasses=executed, missingClasses=missing,
                  tests=counts[0], failures=counts[1], errors=counts[2], skipped=counts[3])
    (folder / 'result.json').write_text(json.dumps(report, indent=2), encoding='utf8')
    (LOCAL / 'transport-result.json').write_text(json.dumps(report, indent=2), encoding='utf8')
    shutil.copyfile(folder / 'tests.log', LOCAL / 'transport-tests.log')
    print('Local module transport tests ' + report['status'] + '; required tests: ' + str(counts[0]))
    return 0 if success else 1


if __name__ == '__main__':
    raise SystemExit(main())
