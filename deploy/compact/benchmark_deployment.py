"""Build immutable comparable images and generate a standalone benchmark topology."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import yaml

def build_standalone(tag):
    from setup import LOCAL, ROOT, ACC, MODULES, run
    images = {}
    for alias, (module, _, _, _) in MODULES.items():
        jar = ROOT / module / 'target' / (Path(module).name + '.jar')
        folder = LOCAL / 'standalone-images' / alias
        folder.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(jar, folder / 'app.jar')
        shutil.copyfile(ACC / 'docker' / 'Dockerfile.jvm', folder / 'Dockerfile')
        run(['javac', '-encoding', 'UTF-8', '-d', str(folder), str(ACC / 'docker' / 'HealthProbe.java')], 'probe-' + alias)
        image = 'tianji-opt/standalone-' + alias + ':' + tag
        run(['docker', 'build', '--pull=false', '-t', image, str(folder)], 'standalone-image-' + alias)
        images[alias] = {'image': image, 'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                         'imageId': subprocess.check_output(['docker', 'image', 'inspect', image, '--format', '{{.Id}}'], text=True).strip()}
        print('Frozen standalone image: ' + alias, flush=True)
    (LOCAL / 'standalone-images.json').write_text(json.dumps(images, indent=2), encoding='utf8')

def standalone():
    from setup import LOCAL, BASE, PORTS, MODULES, GROUPS
    images = json.loads((LOCAL / 'standalone-images.json').read_text(encoding='utf8'))
    compose = yaml.safe_load((BASE / 'compose.yaml').read_text(encoding='utf8'))
    aliases = sorted(alias for alias in MODULES if alias != 'gateway')
    ports = {alias: PORTS['gateway'] + 100 + index for index, alias in enumerate(aliases)}
    targets = {alias: 'http://app-' + alias + ':' + str(ports[alias]) for alias in aliases}
    old_targets = {alias: 'http://app-' + group + ':' + str(PORTS[group]) + '/_modules/' + alias
                   for group, members in GROUPS.items() for alias in members}
    for alias in list(aliases) + ['gateway']:
        path = LOCAL / 'configs' / (alias + '.yml')
        value = path.read_text(encoding='utf8')
        for member, target in targets.items():
            value = value.replace(old_targets[member], target)
        config = yaml.safe_load(value)
        config['server']['port'] = PORTS['gateway'] if alias == 'gateway' else ports[alias]
        config['server']['tomcat'] = {'threads': {'max': 80, 'min-spare': 4}, 'accept-count': 50, 'max-connections': 2000}
        path.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding='utf8')
    services = compose['services']
    for group in GROUPS:
        del services['app-' + group]
    template = services['app-gateway']
    for alias in aliases:
        service = json.loads(json.dumps(template))
        service.update(image=images[alias]['imageId'], mem_limit='768m', cpus=2,
                       ports=['127.0.0.1:' + str(ports[alias]) + ':' + str(ports[alias])],
                       environment={'APP_PORT': str(ports[alias])},
                       command=['--spring.config.location=file:/run/acceptance/application.yml'],
                       volumes=['./.local/configs/' + alias + '.yml:/run/acceptance/application.yml:ro',
                                './.local/signing.jks:/run/compact/signing.jks:ro', './.local/objects:/run/objects'])
        services['app-' + alias] = service
    template['image'] = images['gateway']['imageId']
    template['command'] = ['--spring.config.location=file:/run/acceptance/application.yml']
    template['volumes'] = ['./.local/configs/gateway.yml:/run/acceptance/application.yml:ro',
                           './.local/signing.jks:/run/compact/signing.jks:ro', './.local/objects:/run/objects']
    template['cpus'] = 2
    (BASE / 'compose.yaml').write_text(yaml.safe_dump(compose, sort_keys=False), encoding='utf8')
    runtime = {'mode': 'standalone', 'metrics': {alias: targets[alias].replace('app-' + alias, '127.0.0.1') + '/actuator/prometheus' for alias in aliases},
               'javaServices': ['app-' + alias for alias in aliases] + ['app-gateway']}
    (LOCAL / 'benchmark-runtime.json').write_text(json.dumps(runtime, indent=2), encoding='utf8')
    print('Standalone benchmark generated: 14 Java applications, same public gateway contract')

def freeze_compact(tag):
    from setup import LOCAL, BASE, GROUPS, PORTS
    images = json.loads((LOCAL / 'images.json').read_text(encoding='utf8'))
    compose = yaml.safe_load((BASE / 'compose.yaml').read_text(encoding='utf8'))
    for alias, value in images.items():
        image = 'tianji-opt/compact-' + alias + ':' + tag
        subprocess.run(['docker', 'tag', value['imageId'], image], check=True)
        compose['services'][('web-' if alias in ('student', 'admin') else 'app-') + alias]['image'] = value['imageId']
    infrastructure = {}
    for service in ('mysql', 'redis', 'rabbitmq', 'elasticsearch'):
        image = subprocess.check_output(['docker', 'image', 'inspect', compose['services'][service]['image'], '--format', '{{.Id}}'], text=True).strip()
        compose['services'][service]['image'] = image
        infrastructure[service] = image
    (LOCAL / 'infrastructure-images.json').write_text(json.dumps(infrastructure, indent=2), encoding='utf8')
    (BASE / 'compose.yaml').write_text(yaml.safe_dump(compose, sort_keys=False), encoding='utf8')
    runtime = {'mode': 'compact', 'metrics': {alias: 'http://127.0.0.1:' + str(PORTS[group]) + '/_modules/' + alias + '/actuator/prometheus' for group, aliases in GROUPS.items() for alias in aliases},
               'javaServices': ['app-' + group for group in list(GROUPS) + ['gateway']]}
    (LOCAL / 'benchmark-runtime.json').write_text(json.dumps(runtime, indent=2), encoding='utf8')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['build-standalone', 'standalone', 'freeze-compact'])
    parser.add_argument('--tag', default='9f97d25')
    args = parser.parse_args()
    if args.action == 'build-standalone': build_standalone(args.tag)
    elif args.action == 'standalone': standalone()
    else: freeze_compact(args.tag)
