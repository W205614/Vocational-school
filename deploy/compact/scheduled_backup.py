"""Windowless Task Scheduler entry; the worker and its children share a hidden console."""

from runtime_compose import active_compose, compose_command
import argparse
import json
import logging
from logging.handlers import RotatingFileHandler
import os
from pathlib import Path
import subprocess
import time


def run(home, project, port_offset, python_path):
    home = Path(home).resolve()
    local = home / '.local'
    environment = os.environ.copy()
    environment.update(TJ_COMPACT_HOME=str(home), TJ_COMPACT_PROJECT=project,
                       TJ_COMPACT_PORT_OFFSET=str(port_offset))
    docker_bin = Path(environment.get('ProgramFiles', r'C:\Program Files')) / 'Docker/Docker/resources/bin'
    environment['PATH'] = str(docker_bin) + os.pathsep + environment.get('PATH', '')
    code = 1
    try:
        if not active_compose(home).is_file():
            raise ValueError('Existing deployment required')
        code = subprocess.run(
            [str(python_path), str(Path(__file__).with_name('maintenance.py')), 'scheduled'],
            cwd=Path(__file__).parent, env=environment, stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0,
        ).returncode
    except (OSError, ValueError):
        pass
    # A check is distinct from a successful full backup; never overwrite backup.json here.
    folder = local / 'ops-status'
    folder.mkdir(parents=True, exist_ok=True)
    destination = folder / 'scheduler.json'
    temporary = destination.with_suffix('.tmp')
    temporary.write_text(json.dumps({'checkedAt': int(time.time()), 'exitCode': code}), encoding='utf8')
    temporary.replace(destination)
    if code:
        handler = RotatingFileHandler(local / 'maintenance.log', maxBytes=1024 * 1024,
                                      backupCount=2, encoding='utf8')
        record = logging.LogRecord('tianji-scheduler', logging.ERROR, __file__, 0,
                                   'Scheduled worker failed; inspect backup status and task exit code', (), None)
        handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)s %(message)s'))
        try:
            handler.handle(record)
        finally:
            handler.close()
    return code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home', required=True)
    parser.add_argument('--project', required=True)
    parser.add_argument('--port-offset', type=int, default=0)
    parser.add_argument('--python', required=True)
    args = parser.parse_args()
    return run(args.home, args.project, args.port_offset, args.python)


if __name__ == '__main__':
    raise SystemExit(main())
