"""Snapshot/reset explicitly owned disposable benchmark resources; production is rejected."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from setup import BASE, ROOT, LOCAL, PROJECT, COMPOSE, MODULES, run

def guard():
    allowed = (ROOT / 'deploy/compact/.local/optimization').resolve()
    if not PROJECT.startswith('tianji-opt-') or allowed not in BASE.resolve().parents:
        raise RuntimeError('Reset is restricted to owned optimization benchmark homes')
    ids = subprocess.check_output(COMPOSE + ['ps', '-q', 'mysql'], text=True).splitlines()
    if len(ids) != 1:
        raise RuntimeError('Expected one benchmark database container')
    info = json.loads(subprocess.check_output(['docker', 'inspect', ids[0]], text=True))[0]
    if info['Config']['Labels'].get('com.docker.compose.project') != PROJECT:
        raise RuntimeError('Database ownership mismatch')

def capture(path):
    guard()
    databases = sorted({value[3] for alias, value in MODULES.items() if value[3] and alias != 'data'})
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --set-gtid-purged=OFF --databases ' + ' '.join(databases)
    with Path(path).open('wb') as output:
        subprocess.run(COMPOSE + ['exec', '-T', 'mysql', 'sh', '-c', command], stdout=output, stderr=subprocess.PIPE, check=True)
    metadata = {'sha256': hashlib.sha256(Path(path).read_bytes()).hexdigest(), 'sourceProject': PROJECT, 'databases': databases}
    Path(str(path) + '.json').write_text(json.dumps(metadata, indent=2), encoding='utf8')
    print('Immutable synthetic benchmark snapshot captured')

def restore(path):
    guard()
    path = Path(path)
    metadata = json.loads(Path(str(path) + '.json').read_text(encoding='utf8'))
    if hashlib.sha256(path.read_bytes()).hexdigest() != metadata['sha256']:
        raise RuntimeError('Snapshot fingerprint changed')
    services = json.loads(subprocess.check_output(COMPOSE + ['config', '--format', 'json'], text=True))['services']
    applications = [name for name in services if name.startswith(('app-', 'web-'))]
    run(COMPOSE + ['stop', '-t', '60', *applications], 'benchmark-stop')
    run(COMPOSE + ['exec', '-T', 'mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'], 'benchmark-restore', input=path.read_bytes())
    run(COMPOSE + ['exec', '-T', 'redis', 'redis-cli', 'FLUSHALL'], 'benchmark-redis-reset')
    queues = json.loads(subprocess.check_output(COMPOSE + ['exec', '-T', 'rabbitmq', 'rabbitmqctl', 'list_queues', '-q', '--formatter', 'json', 'name'], text=True))
    for queue in queues:
        run(COMPOSE + ['exec', '-T', 'rabbitmq', 'rabbitmqctl', 'purge_queue', queue['name']], 'benchmark-purge')
    # Search is a projection, rebuilt from the same restored MySQL snapshot.
    import requests
    from setup import OFFSET
    response=requests.delete('http://127.0.0.1:'+str(24920+OFFSET)+'/course',timeout=10)
    if response.status_code not in (200,404):response.raise_for_status()
    import sys
    run([sys.executable,str(ROOT/'deploy/compact/prepare_search.py')],'benchmark-search-mapping')
    run(COMPOSE + ['up', '-d', '--wait', *applications], 'benchmark-up')
    run([sys.executable, str(ROOT / 'deploy/compact/rebuild_search.py')], 'benchmark-search')
    print('Owned synthetic benchmark restored and projections rebuilt', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('action', choices=['capture', 'restore']); parser.add_argument('path', type=Path)
    args = parser.parse_args()
    (capture if args.action == 'capture' else restore)(args.path)
