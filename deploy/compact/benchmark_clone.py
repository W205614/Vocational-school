"""Clone only private synthetic fixtures/config identity; target resources are independently owned."""
import argparse
import json
from pathlib import Path
import shutil
from setup import BASE, ROOT, LOCAL, PROJECT, prepare, COMPOSE, run, configure_acceptance, mysql, MODULES, PORTS

def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--from-home', required=True, type=Path)
    parser.add_argument('--current-configuration', action='store_true')
    parser.add_argument('--search-major',type=int,choices=[7,9])
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
    source_compose = yaml.safe_load((source / 'compose.yaml').read_text(encoding='utf8'))
    replacements = {}
    for group, port in PORTS.items():
        service = source_compose['services'].get('app-' + group)
        if service:
            old = str(service['environment']['APP_PORT'])
            replacements['http://app-' + group + ':' + old] = 'http://app-' + group + ':' + str(port)
    def translate(value):
        if isinstance(value, dict): return {key: translate(item) for key, item in value.items()}
        if isinstance(value, list): return [translate(item) for item in value]
        if isinstance(value, str):
            for old, new in replacements.items(): value = value.replace(old, new)
        return value
    # Freeze module configuration semantics to the source deployment, even when
    # the working tree is already implementing the next version.
    for config in ([] if args.current_configuration else (source / '.local/configs').glob('*.yml')):
        content = translate(yaml.safe_load(config.read_text(encoding='utf8')))
        if config.stem in PORTS: content['server']['port'] = PORTS[config.stem]
        (LOCAL / 'configs' / config.name).write_text(yaml.safe_dump(content, allow_unicode=True, sort_keys=False), encoding='utf8')
    path = BASE / 'compose.yaml'; compose = yaml.safe_load(path.read_text(encoding='utf8'))
    infrastructure = json.loads((LOCAL / 'infrastructure-images.json').read_text(encoding='utf8'))
    for name, image in infrastructure.items():
        if not args.current_configuration or name!='elasticsearch': compose['services'][name]['image'] = image
    if not args.current_configuration and args.search_major is None:
        mounts=source_compose['services']['elasticsearch']['volumes']
        if mounts not in (['compact_search:/usr/share/elasticsearch/data'],['compact_search_v9:/usr/share/elasticsearch/data']):
            raise RuntimeError('Use --search-major for a source with a migrated search volume')
        # Keep a frozen 7.x source on its independently owned 7.x volume, even
        # though a newly prepared current checkout defaults to a fresh 9.x volume.
        compose['services']['elasticsearch']['volumes']=mounts
    if args.search_major==7:
        import subprocess
        compose['services']['elasticsearch']['image']=subprocess.check_output(['docker','image','inspect','docker.elastic.co/elasticsearch/elasticsearch:7.17.29','--format','{{.Id}}'],text=True).strip()
        compose['services']['elasticsearch']['volumes']=['compact_search:/usr/share/elasticsearch/data']
    elif args.search_major==9:
        compose['services']['elasticsearch']['image']='docker.elastic.co/elasticsearch/elasticsearch:9.5.5'
        compose['services']['elasticsearch']['volumes']=['compact_search_v9:/usr/share/elasticsearch/data']
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
