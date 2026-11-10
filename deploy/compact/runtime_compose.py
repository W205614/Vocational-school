"""Keep generated deployment state outside Git, with legacy bundle compatibility."""

from pathlib import Path
import json
import re
import shutil
import subprocess
import yaml

DEFAULT_PROJECT = 'zhixuetang-compact'
PRIMARY_PROJECTS = frozenset({DEFAULT_PROJECT, 'tianji-compact'})


def resolve_project(home, explicit=None, default=DEFAULT_PROJECT):
    """Keep an existing deployment attached to its original Compose resources."""
    runtime = Path(home).resolve() / '.local/compose.runtime.yaml'
    recorded = None
    if runtime.is_file():
        recorded = yaml.safe_load(runtime.read_text(encoding='utf8')).get('name')
        if not isinstance(recorded, str) or not re.fullmatch(
            r'[a-z][a-z0-9-]{2,40}', recorded
        ):
            raise ValueError('Existing runtime must record its Compose project name')
    if explicit and recorded and explicit != recorded:
        raise ValueError('Explicit project conflicts with existing runtime identity')
    project = explicit or recorded or default
    if not re.fullmatch(r'[a-z][a-z0-9-]{2,40}', project):
        raise ValueError('Invalid deployment project name')
    return project


def rabbit_username(home):
    config = Path(home) / '.local/configs/auth.yml'
    if config.is_file():
        value = yaml.safe_load(config.read_text(encoding='utf8'))['spring'][
            'rabbitmq'
        ].get('username')
        if not isinstance(value, str) or not value or '${' in value:
            raise ValueError('Existing RabbitMQ username must be explicit')
        return value
    compose = active_compose(home)
    if compose.is_file():
        broker = (
            yaml.safe_load(compose.read_text(encoding='utf8'))
            .get('services', {})
            .get('rabbitmq')
        )
        if broker is not None:
            environment = broker.get('environment', {})
            if isinstance(environment, list):
                environment = dict(
                    item.split('=', 1) for item in environment if '=' in item
                )
            value = environment.get('RABBITMQ_DEFAULT_USER')
            if not isinstance(value, str) or not value or '${' in value:
                raise ValueError('Existing RabbitMQ username must be explicit')
            return value
    return 'zhixuetang'


def active_compose(home):
    home = Path(home).resolve()
    runtime = home / '.local/compose.runtime.yaml'
    legacy = home / 'compose.yaml'
    return runtime if runtime.is_file() or not legacy.is_file() else legacy


def runtime_compose(home):
    """Adopt legacy bytes once; never regenerate existing mounts or broker identity."""
    home = Path(home).resolve()
    destination = home / '.local/compose.runtime.yaml'
    if destination.is_symlink() or (home / '.local').is_symlink():
        raise RuntimeError('Runtime configuration must belong to the deployment home')
    if not destination.exists():
        destination.parent.mkdir(parents=True, exist_ok=True)
        legacy = home / 'compose.yaml'
        if legacy.is_symlink():
            raise RuntimeError('Legacy configuration must not be a symlink')
        if legacy.is_file():
            # Exclusive creation prevents concurrent maintenance from overwriting state.
            with destination.open('xb') as output, legacy.open('rb') as source:
                shutil.copyfileobj(source, output)
    return destination


def compose_command(home, project):
    home = Path(home).resolve()
    from secret_store import load_existing
    load_existing(home)
    return [
        'docker',
        'compose',
        '--project-directory',
        str(home),
        '-p',
        project,
        '-f',
        str(runtime_compose(home)),
        '--env-file',
        str(home / '.env'),
    ]


def verify_migration(home, project):
    """Compare parsed configurations without starting or replacing any container."""
    home = Path(home).resolve()
    legacy = home / 'compose.yaml'
    destination = runtime_compose(home)
    command = compose_command(home, project)
    current = json.loads(
        subprocess.check_output(command + ['config', '--format', 'json'], text=True)
    )
    if legacy.is_file():
        before = command.copy()
        before[before.index('-f') + 1] = str(legacy)
        previous = json.loads(
            subprocess.check_output(before + ['config', '--format', 'json'], text=True)
        )
        if current != previous:
            raise RuntimeError(
                'Legacy and private runtime configurations differ; preserve both and inspect'
            )
    return destination


def equivalent_commands(recorded, current, home):
    """Accept a legacy interrupted journal only after exact project/config verification."""
    home = Path(home).resolve()

    def identity(command):
        if command[:2] != ['docker', 'compose']:
            return None
        options = {}
        args = command[2:]
        if len(args) % 2:
            return None
        for key, value in zip(args[::2], args[1::2]):
            if (
                key not in {'-p', '-f', '--env-file', '--project-directory'}
                or key in options
            ):
                return None
            options[key] = value
        if not {'-p', '-f', '--env-file'} <= options.keys():
            return None
        if Path(options['-f']).resolve() not in {
            home / 'compose.yaml',
            home / '.local/compose.runtime.yaml',
        }:
            return None
        if Path(options['--env-file']).resolve() != home / '.env':
            return None
        if (
            '--project-directory' in options
            and Path(options['--project-directory']).resolve() != home
        ):
            return None
        return options['-p']

    project = identity(current)
    if not project or identity(recorded) != project:
        return False
    try:
        normalized = []
        for command in (recorded, current):
            command = command.copy()
            if '--project-directory' not in command:
                command += ['--project-directory', str(home)]
            normalized.append(
                json.loads(
                    subprocess.check_output(
                        command + ['config', '--format', 'json'], text=True
                    )
                )
            )
        return normalized[0] == normalized[1]
    except (OSError, ValueError, subprocess.CalledProcessError):
        return False
