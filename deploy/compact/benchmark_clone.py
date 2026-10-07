"""Clone only private synthetic fixtures/config identity; target resources are independently owned."""
import argparse
import json
from pathlib import Path
import shutil
from setup import BASE, ROOT, LOCAL, PROJECT, prepare, COMPOSE, run, configure_acceptance, mysql, MODULES

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--from-home', required=True, type=Path)
    args = parser.parse_args()
    allowed = (ROOT / 'deploy/compact/.local/optimization').resolve()
    source = args.from_home.resolve()
    if not PROJECT.startswith('tianji-opt-') or allowed not in source.parents or allowed not in BASE.resolve().parents or source == BASE.resolve():
        raise RuntimeError('Clone is restricted to distinct synthetic benchmark homes')
    if (LOCAL / 'init-state.json').exists(): raise RuntimeError('Target already initialized')
    LOCAL.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source / '.env', BASE / '.env')
    for name in ('database-accounts.json', 'signing.jks', 'accounts.json', 'browser-fixture.json', 'load-fixture.json', 'load-accounts.json', 'standalone-images.json', 'images.json', 'infrastructure-images.json'):
        shutil.copyfile(source / '.local' / name, LOCAL / name)
    shutil.copytree(source / '.local/objects', LOCAL / 'objects', dirs_exist_ok=True)
    prepare()
    import yaml
    path = BASE / 'compose.yaml'; compose = yaml.safe_load(path.read_text(encoding='utf8'))
    infrastructure = json.loads((LOCAL / 'infrastructure-images.json').read_text(encoding='utf8'))
    for name, image in infrastructure.items(): compose['services'][name]['image'] = image
    path.write_text(yaml.safe_dump(compose, sort_keys=False), encoding='utf8')
    run(COMPOSE + ['up', '-d', '--wait', 'mysql', 'redis', 'rabbitmq', 'elasticsearch'], 'clone-infrastructure')
    configure_acceptance()
    from setup import secrets_config
    _, passwords = secrets_config()
    # Import is done by the fingerprint-checked snapshot reset, then these private grants remain valid.
    for alias, password in passwords.items():
        database = MODULES[alias][3]
        mysql("CREATE USER IF NOT EXISTS 'app_" + alias + "'@'%' IDENTIFIED BY '" + password + "';GRANT SELECT,INSERT,UPDATE,DELETE ON `" + database + "`.* TO 'app_" + alias + "'@'%';")
    (LOCAL / 'init-state.json').write_text(json.dumps({'phase': 'BENCHMARK_CLONE', 'source': str(source)}), encoding='utf8')
    print('Independent synthetic clone prepared; private identity and fixed fixtures retained')

if __name__ == '__main__': main()
