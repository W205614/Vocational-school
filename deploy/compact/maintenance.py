"""Bounded host maintenance. Never launches Docker Desktop or deletes recovery points."""
from contextlib import contextmanager
from datetime import datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo
import argparse
import json
import logging
from logging.handlers import RotatingFileHandler
import os
import hashlib
import re
import shutil
import subprocess
import sys
import time

SHANGHAI = ZoneInfo('Asia/Shanghai')
REQUIRED = ('mysql', 'redis', 'rabbitmq')
class TransitionalService(RuntimeError):
    pass


@contextmanager
def maintenance_lock(local):
    """OS-owned lock: a process crash releases it without deleting guessed files."""
    path = Path(local) / 'maintenance.lock'
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('a+b') as handle:
        if path.stat().st_size == 0:
            handle.write(b'0'); handle.flush()
        handle.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise RuntimeError('Another backup or publication owns the maintenance lock') from error
        try:
            yield
        finally:
            handle.seek(0)
            if os.name == 'nt':
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(handle, fcntl.LOCK_UN)


def atomic_json(path, value):
    path = Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2), encoding='utf8')
    temporary.replace(path)


def status(local, state, **values):
    folder = Path(local) / 'ops-status'; folder.mkdir(parents=True, exist_ok=True)
    path = folder / 'backup.json'
    previous = json.loads(path.read_text(encoding='utf8')) if path.exists() else {}
    current = {**previous, **values, 'state': state, 'checkedAt': int(time.time())}
    atomic_json(path, current)
    # Numeric-only, secret-free status is mounted read-only into the gateway.
    props = folder / 'backup.properties'; temporary = props.with_suffix('.tmp')
    temporary.write_text('\n'.join(f'{key}={int(current.get(key, 0))}' for key in
                                  ('lastSuccess', 'lastFailure', 'running')) + '\n', encoding='ascii')
    temporary.replace(props)
    return current


def backup_due(previous, now=None):
    now = now or datetime.now(SHANGHAI)
    boundary = now.replace(hour=3, minute=30, second=0, microsecond=0)
    if now < boundary:
        boundary -= timedelta(days=1)
    return previous.get('lastSuccess', 0) < boundary.timestamp()


def logger(local):
    result = logging.getLogger('tianji-maintenance')
    if not result.handlers:
        Path(local).mkdir(parents=True, exist_ok=True)
        handler = RotatingFileHandler(Path(local) / 'maintenance.log', maxBytes=1024 * 1024,
                                      backupCount=2, encoding='utf8')
        handler.setFormatter(logging.Formatter('%(asctime)s %(levelname)s %(message)s'))
        result.addHandler(handler); result.setLevel(logging.INFO)
    return result


def docker_available(runner=subprocess.run):
    try:
        return runner(['docker', 'info', '--format', '{{.ServerVersion}}'], stdout=subprocess.DEVNULL,
                      stderr=subprocess.DEVNULL, timeout=12).returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


@contextmanager
def required_resources(runtime, check=subprocess.check_output, run=subprocess.run, before_start=None):
    """Start existing infrastructure only; never start business apps for a backup."""
    running = set(check(runtime.COMPOSE + ['ps', '--status', 'running', '--services'], text=True, timeout=30).splitlines())
    temporary = [name for name in REQUIRED if name not in running]
    activated = []
    try:
        for name in REQUIRED:
            identifier=check(runtime.COMPOSE + ['ps', '-aq', name], text=True, timeout=30).strip()
            if not identifier or '\n' in identifier:
                raise RuntimeError('Backup needs initialized existing infrastructure: ' + name)
            state=check(['docker','inspect','--format','{{.State.Status}}',identifier],text=True,timeout=30).strip()
            if state not in {'running','exited'}:
                raise TransitionalService('Infrastructure is paused or transitioning; leave its state untouched')
        if before_start:before_start(running,temporary)
        if temporary:
            activated=temporary
            run(runtime.COMPOSE + ['start', '--wait', '--wait-timeout', '180', *temporary],
                check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=210)
        yield
    finally:
        if activated:
            run(runtime.COMPOSE + ['stop', '-t', '60', *activated], check=True,
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)


def resume_interrupted(runtime, local):
    path=Path(local)/'ops-status/resume.json'
    if not path.exists():return
    journal=json.loads(path.read_text(encoding='utf8'))
    from runtime_compose import equivalent_commands
    same_command=journal.get('compose')==runtime.COMPOSE
    if journal.get('home')!=str(Path(local).resolve()) or (not same_command and not equivalent_commands(journal.get('compose',[]),runtime.COMPOSE,Path(local).parent)):
        raise RuntimeError('Unfinished service restoration belongs to a different deployment')
    apps=journal['apps'];temporary=journal['temporary']
    if any(not name.startswith(('app-','web-')) for name in apps) or any(name not in REQUIRED for name in temporary):
        raise RuntimeError('Invalid original service-state journal')
    # capture can be interrupted between rabbitmqctl stop_app/start_app while
    # the container itself still reports running. Restore the broker before apps.
    if 'rabbitmq' not in temporary:
        subprocess.run(runtime.COMPOSE+['start','rabbitmq'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=60)
        subprocess.run(runtime.COMPOSE+['exec','-T','rabbitmq','rabbitmqctl','start_app'],check=True,
                       stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=180)
        subprocess.run(runtime.COMPOSE+['exec','-T','rabbitmq','rabbitmq-diagnostics','-q','check_running'],check=True,
                       stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=30)
    if apps:subprocess.run(runtime.COMPOSE+['start','--wait','--wait-timeout','180',*apps],check=True,
                           stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=210)
    if temporary:subprocess.run(runtime.COMPOSE+['stop','-t','60',*temporary],check=True,
                                stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=120)
    path.unlink()


def retain_automatic(local, bundle, verify):
    """Retain two *owned* automatic bundles. Existing/manual backups are never candidates."""
    root = (Path(local) / 'backup').resolve()
    bundle = Path(bundle).resolve()
    if bundle.parent != root or bundle.is_symlink():
        raise RuntimeError('Automatic backup escaped its project directory')
    marker_name = 'automatic-owner.json'
    manifest_hash = lambda path: hashlib.sha256((path / 'manifest.json').read_bytes()).hexdigest()
    atomic_json(bundle / marker_name, {'owner': 'tianji-maintenance-v1', 'home': str(Path(local).resolve()),
                                      'manifestSha256': manifest_hash(bundle)})
    candidates = []
    for path in root.iterdir():
        if not path.is_dir() or path.is_symlink() or (path/'publication-pin.json').exists() or not re.fullmatch(r'[0-9]{8}T[0-9]{6}\.[0-9]{6}Z', path.name):
            continue
        marker = path / marker_name
        if not marker.is_file() or marker.is_symlink(): continue
        try:
            owned = json.loads(marker.read_text(encoding='utf8'))
            if owned != {'owner': 'tianji-maintenance-v1', 'home': str(Path(local).resolve()), 'manifestSha256': manifest_hash(path)}:
                continue
            verify(path)
        except (ValueError, OSError, RuntimeError, KeyError):
            continue
        candidates.append(path)
    for path in sorted(candidates, key=lambda p: p.name, reverse=True)[2:]:
        # Validate the final absolute target immediately before recursive removal.
        if path.resolve().parent != root or not root.is_relative_to(Path(local).resolve()) or path.is_symlink():
            raise RuntimeError('Retention target escaped owned project backup root')
        shutil.rmtree(path)


def perform_backup(online=False, automated=False):
    import setup
    sys.path.insert(0, str(setup.SOURCE.parent / 'final'))
    from final_backup import capture, quiesce, verify
    runtime = setup.configure_acceptance()
    with maintenance_lock(setup.LOCAL):
        resume_interrupted(runtime,setup.LOCAL)
        journal=setup.LOCAL/'ops-status/resume.json'
        status(setup.LOCAL, 'RUNNING', running=1)
        try:
            destination = (setup.LOCAL / 'backup').resolve()
            if not destination.is_relative_to(setup.ROOT.resolve()):
                raise RuntimeError('Compact backups must remain inside this project')
            os.environ['TJ_BACKUP_HOME'] = str(destination)
            def record(running,temporary):
                atomic_json(journal,{'home':str(setup.LOCAL.resolve()),'compose':runtime.COMPOSE,
                    'apps':sorted(name for name in running if name.startswith(('app-','web-'))),'temporary':temporary})
            with required_resources(runtime,before_start=record):
                if online:
                    bundle = capture(runtime, setup.BASE)
                else:
                    with quiesce(runtime):
                        bundle = capture(runtime, setup.BASE, quiesced=True)
            if journal.exists():journal.unlink()
            if automated and not online:
                retain_automatic(setup.LOCAL, bundle, verify)
            # Partial online exports never satisfy the full-backup schedule.
            values = {'running': 0, 'lastBundle': str(bundle)}
            if not online: values.update(lastSuccess=int(time.time()),requiresAttention=False)
            status(setup.LOCAL, 'PARTIAL' if online else 'SUCCEEDED', **values)
            logger(setup.LOCAL).info('Partial export completed' if online else 'Full backup verified; original running state restored')
            return bundle
        except TransitionalService:
            status(setup.LOCAL,'SKIPPED_TRANSITIONAL_STATE',running=0)
            return None
        except Exception:
            # The journal is retained if ordinary finally cleanup did not finish.
            try:resume_interrupted(runtime,setup.LOCAL)
            except Exception:pass
            status(setup.LOCAL, 'FAILED', running=0, lastFailure=int(time.time()),requiresAttention=True)
            # Do not log exception messages, commands or credentials.
            logger(setup.LOCAL).error('Backup failed; private incomplete bundle retained; inspect service state')
            raise


def scheduled():
    import setup
    path = setup.LOCAL / 'ops-status' / 'backup.json'
    previous = json.loads(path.read_text(encoding='utf8')) if path.exists() else {}
    if previous.get('requiresAttention') and not (setup.LOCAL/'ops-status/resume.json').exists():
        return 0
    if not backup_due(previous) and not (setup.LOCAL/'ops-status/resume.json').exists():
        return 0
    if not docker_available():
        # Expected during intentional shutdown: overwrite constant-size status, no error log spam.
        status(setup.LOCAL, 'SKIPPED_DOCKER_UNAVAILABLE', running=0)
        return 0
    try:
        if (setup.LOCAL/'ops-status/resume.json').exists():
            with maintenance_lock(setup.LOCAL):resume_interrupted(setup.configure_acceptance(),setup.LOCAL)
            status(setup.LOCAL,'INTERRUPTED_RESTORED',running=0,lastFailure=int(time.time()),requiresAttention=True)
            return 0
        perform_backup(automated=True)
        return 0
    except RuntimeError as error:
        if 'maintenance lock' in str(error):
            # A deployment or another task is active. The next bounded check retries.
            return 0
        return 1
    except Exception:
        return 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=('scheduled', 'backup', 'retry', 'lock'))
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if args.action == 'scheduled': return scheduled()
    if args.action == 'backup': perform_backup(); return 0
    import setup
    if args.action=='retry':
        with maintenance_lock(setup.LOCAL):status(setup.LOCAL,'RETRY_REQUESTED',requiresAttention=False,running=0)
        if not docker_available():
            status(setup.LOCAL,'SKIPPED_DOCKER_UNAVAILABLE',running=0)
            return 0
        perform_backup(automated=True)
        return 0
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    if not command: parser.error('lock requires a command')
    with maintenance_lock(setup.LOCAL):
        return subprocess.run(command, check=False).returncode


if __name__ == '__main__':
    raise SystemExit(main())
