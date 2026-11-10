"""Isolated acceptance data clone. Original deployment is only read."""

from pathlib import Path
import subprocess, secrets, argparse, json, time, os, sys

BASE = Path(__file__).resolve().parent
sys.path.append(str(BASE.parent / 'compact'))
from runtime_compose import rabbit_username
from operations_config import private_directory, private_file, private_text
from secret_store import environment, write_values, load_existing

RUNTIME = Path(os.environ.get('ZXT_RUNTIME_HOME', str(BASE))).resolve()
LOCAL = RUNTIME / '.local'
COMPOSE = [
    'docker',
    'compose',
    '--project-name',
    os.environ.get('ZXT_RUNTIME_PROJECT', 'vocational-acceptance'),
    '--file',
    str(RUNTIME / 'compose.yaml'),
    '--env-file',
    str(RUNTIME / '.env'),
]
load_existing(RUNTIME)
DATABASES = [
    'tj_auth',
    'tj_user',
    'tj_course',
    'tj_learning',
    'tj_trade',
    'tj_promotion',
    'tj_exam',
    'tj_remark',
    'tj_pay',
    'tj_search',
    'tj_message',
    'tj_media',
]


def run(args, **kwargs):
    result = subprocess.run(args, check=False, **kwargs)
    if result.returncode:
        raise RuntimeError('Command failed: ' + args[0] + ' (output saved locally)')
    return result


def mysql(sql, db=None):
    cmd = COMPOSE + [
        'exec',
        '-T',
        'mysql',
        'sh',
        '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B'
        + (' ' + db if db else ''),
    ]
    result = subprocess.run(
        cmd,
        input=sql.encode(),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=300,
    )
    if result.returncode:
        LOCAL.mkdir(exist_ok=True)
        (LOCAL / 'mysql-errors.log').write_bytes(result.stderr)
        raise RuntimeError(
            'Isolated SQL failed; diagnostics in private .local/mysql-errors.log'
        )
    return result.stdout.decode('utf8').strip()


def local_secrets():
    private_directory(LOCAL)
    path = RUNTIME / '.env'
    if not path.exists() and not (LOCAL / 'environment.enc').exists():
        write_values(RUNTIME, 'environment', {
            'ACCEPTANCE_DB_PASSWORD': secrets.token_hex(24),
            'ACCEPTANCE_MQ_PASSWORD': secrets.token_hex(24),
            'ACCEPTANCE_INTERNAL_TOKEN': secrets.token_hex(32),
            'ACCEPTANCE_GRAFANA_PASSWORD': secrets.token_hex(24),
        })
        private_text(path, '# Credentials are encrypted in .local/environment.enc\n')
    values = environment(RUNTIME)
    if 'FINAL_DB_PASSWORD' in values:
        return dict(
            ACCEPTANCE_MQ_USERNAME=rabbit_username(RUNTIME),
            ACCEPTANCE_DB_PASSWORD=values['FINAL_DB_PASSWORD'],
            ACCEPTANCE_MQ_PASSWORD=values['FINAL_MQ_PASSWORD'],
            ACCEPTANCE_INTERNAL_TOKEN=values['FINAL_INTERNAL_TOKEN'],
            ACCEPTANCE_GRAFANA_PASSWORD=values['FINAL_GRAFANA_PASSWORD'],
        )
    if not all(k in values for k in ('ACCEPTANCE_INTERNAL_TOKEN', 'ACCEPTANCE_GRAFANA_PASSWORD')):
        raise RuntimeError('Existing credentials are incomplete; explicit credential migration required')
    values.setdefault('ACCEPTANCE_MQ_USERNAME', rabbit_username(RUNTIME))
    return values


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--clone', action='store_true')
    args = parser.parse_args()
    LOCAL.mkdir(exist_ok=True)
    env = BASE / '.env'
    local_secrets()
    run(
        COMPOSE + ['up', '-d', '--wait'],
        stdout=open(LOCAL / 'infra.log', 'w'),
        stderr=subprocess.STDOUT,
    )
    if args.clone:
        if (
            mysql(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='tj_trade'"
            )
            != '0'
        ):
            raise RuntimeError('Refusing to overwrite an existing acceptance clone')
        dump = LOCAL / 'baseline.sql'
        with dump.open('wb') as output:
            run(
                [
                    'docker',
                    'exec',
                    'tianji-desktop-mysql-1',
                    'sh',
                    '-c',
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --databases '
                    + ' '.join(DATABASES),
                ],
                stdout=output,
                stderr=open(LOCAL / 'dump-errors.log', 'wb'),
            )
        with dump.open('rb') as source:
            run(
                COMPOSE
                + [
                    'exec',
                    '-T',
                    'mysql',
                    'sh',
                    '-c',
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot',
                ],
                stdin=source,
                stdout=open(LOCAL / 'restore.log', 'w'),
                stderr=subprocess.STDOUT,
            )
        (LOCAL / 'baseline-counts.json').write_text(
            json.dumps(
                {
                    db: mysql(
                        "SELECT table_name,table_rows FROM information_schema.tables WHERE table_schema='"
                        + db
                        + "' ORDER BY table_name"
                    )
                    for db in DATABASES
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding='utf8',
        )
    print('Isolated infrastructure ready. Existing deployment was not modified.')


if __name__ == '__main__':
    main()
