"""Run a check and bind only its fresh result; exceptions and stale files fail closed."""
import argparse
import json
import subprocess
import sys
import time
from setup import LOCAL
from evidence import save_report, utcnow

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('name')
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if not args.command: parser.error('A command is required')
    started = utcnow(); started_ns = time.time_ns(); path = LOCAL / (args.name + '.json')
    result = {'status': 'FAILED'}
    code = 1
    try:
        code = subprocess.run(args.command).returncode
        if path.exists() and path.stat().st_mtime_ns >= started_ns:
            result = json.loads(path.read_text(encoding='utf8'))
        if code != 0: result['status'] = 'FAILED'
    except Exception as error:
        result = {'status': 'FAILED', 'failureType': type(error).__name__}
    save_report(LOCAL, args.name, result, started)
    return code or (0 if result.get('status') == 'PASSED' else 1)

if __name__ == '__main__': raise SystemExit(main())
