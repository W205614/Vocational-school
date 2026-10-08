"""Build immutable comparable images and generate a standalone benchmark topology."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import yaml

def standalone_configuration(config, source):
    # Generated host settings must not discard the application's own bean-cycle
    # policy or require health contributors absent from its standalone classpath.
    original=yaml.safe_load(source)
    main=original.get('spring',{}).get('main',{})
    config['spring']['main']={**main,**config['spring'].get('main',{})}
    readiness=original.get('management',{}).get('endpoint',{}).get('health',{}).get('group',{}).get('readiness',{})
    if 'include' not in readiness:raise RuntimeError('Standalone source must declare its required readiness contributors')
    config['management']['endpoint']['health']['group']['readiness']['include']=readiness['include']
    return config

def bind_source(compose, ref, verify_worktree=True):
    from setup import LOCAL, ROOT
    commit = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', ref], text=True).strip()
    changed = subprocess.check_output(['git', '-C', str(ROOT), 'diff', '--name-only', commit], text=True).splitlines()
    production = [name for name in changed if '/src/main/' in name or name.endswith('pom.xml') or name.startswith('frontend/')]
    untracked = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '--others', '--exclude-standard'], text=True).splitlines()
    production += [name for name in untracked if '/src/main/' in name or name.endswith('pom.xml')]
    if verify_worktree and production: raise RuntimeError('Commit does not describe current production source')
    images = {name: subprocess.check_output(['docker', 'image', 'inspect', value['image'], '--format', '{{.Id}}'], text=True).strip()
              for name, value in compose['services'].items() if name.startswith(('app-', 'web-'))}
    (LOCAL / 'build-source.json').write_text(json.dumps({'sourceCommit': commit, 'imageDigests': images}, indent=2), encoding='utf8')

def build_standalone(tag):
    from setup import LOCAL, ROOT, ACC, MODULES, run
    images = {}
    source_commit = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', tag], text=True).strip()
    from build_provenance import require_package,digest,clean_commit
    journal=require_package(LOCAL,source_commit,'standalone')
    if clean_commit(ROOT)!=source_commit:raise RuntimeError('Standalone source commit mismatch')
    for alias, (module, _, _, _) in MODULES.items():
        jar = ROOT / module / 'target' / (Path(module).name + '.jar')
        if digest(jar)!=journal['jars'].get(alias):raise RuntimeError('Standalone artifact changed after packaging')
        folder = LOCAL / 'standalone-images' / alias
        folder.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(jar, folder / 'app.jar')
        shutil.copyfile(ACC / 'docker' / 'Dockerfile.jvm', folder / 'Dockerfile')
        run(['javac', '-encoding', 'UTF-8', '-d', str(folder), str(ACC / 'docker' / 'HealthProbe.java')], 'probe-' + alias)
        image = 'tianji-opt/standalone-' + alias + ':' + tag
        run(['docker', 'build', '--pull=false', '-t', image, str(folder)], 'standalone-image-' + alias)
        images[alias] = {'image': image, 'sourceCommit': source_commit, 'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
                         'imageId': subprocess.check_output(['docker', 'image', 'inspect', image, '--format', '{{.Id}}'], text=True).strip()}
        print('Frozen standalone image: ' + alias, flush=True)
    (LOCAL / 'standalone-images.json').write_text(json.dumps(images, indent=2), encoding='utf8')

def standalone(ref='HEAD'):
    from setup import LOCAL, BASE, PORTS, MODULES, GROUPS
    images = json.loads((LOCAL / 'standalone-images.json').read_text(encoding='utf8'))
    from setup import ROOT
    commit = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', ref], text=True).strip()
    if any(details.get('sourceCommit') != commit for details in images.values()): raise RuntimeError('Standalone image provenance mismatch')
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
            owner=next(group for group,members in GROUPS.items() if member in members)
            for host in ('app-'+owner,'127.0.0.1'):
                value=value.replace('http://'+host+':'+str(PORTS[owner]+1000)+'/_modules/'+member,target)
        config = yaml.safe_load(value)
        module=MODULES[alias][0]
        original=subprocess.check_output(['git','-C',str(ROOT),'show',commit+':'+module+'/src/main/resources/application.yml'],text=True,encoding='utf8')
        config=standalone_configuration(config,original)
        config.setdefault('tj',{}).setdefault('feign',{}).pop('internal-ports',None)
        config['server']['port'] = PORTS['gateway'] if alias == 'gateway' else ports[alias]
        config['server']['tomcat'] = {'threads': {'max': 80, 'min-spare': 4}, 'accept-count': 50, 'max-connections': 2000}
        path.write_text(yaml.safe_dump(config, allow_unicode=True, sort_keys=False), encoding='utf8')
    services = compose['services']
    compact_images = json.loads((LOCAL / 'images.json').read_text(encoding='utf8'))
    for web in ('student', 'admin'): services['web-' + web]['image'] = compact_images[web]['imageId']
    for group in GROUPS:
        services.pop('app-' + group,None)
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
    runtime['applyMigrations']=commit!=subprocess.check_output(['git','-C',str(ROOT),'rev-parse','9f97d25'],text=True).strip()
    (LOCAL / 'benchmark-runtime.json').write_text(json.dumps(runtime, indent=2), encoding='utf8')
    bind_source(compose, ref, verify_worktree=False)
    print('Standalone benchmark generated: 14 Java applications, same public gateway contract')

def freeze_compact(tag):
    from setup import LOCAL, BASE, GROUPS, PORTS, ROOT
    images = json.loads((LOCAL / 'images.json').read_text(encoding='utf8'))
    from build_provenance import require_package
    commit=subprocess.check_output(['git','-C',str(ROOT),'rev-parse',tag],text=True).strip()
    journal=require_package(LOCAL,commit,'compact')
    if any(details.get('jarSha256')!=journal['jars'].get(alias) for alias,details in images.items() if alias not in ('student','admin')):raise RuntimeError('Compact images do not match the fresh packaged source')
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
    runtime['applyMigrations']=subprocess.check_output(['git','-C',str(ROOT),'rev-parse',tag],text=True).strip()!=subprocess.check_output(['git','-C',str(ROOT),'rev-parse','9f97d25'],text=True).strip()
    (LOCAL / 'benchmark-runtime.json').write_text(json.dumps(runtime, indent=2), encoding='utf8')
    bind_source(compose, tag)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['package-standalone','build-standalone', 'standalone', 'freeze-compact'])
    parser.add_argument('--tag', default='9f97d25')
    args = parser.parse_args()
    if args.action == 'package-standalone':
        from setup import ROOT,LOCAL,MODULES,run
        from build_provenance import clean_commit,packaged
        import shutil
        commit=clean_commit(ROOT)
        run([shutil.which('mvn') or 'mvn','-B','-DskipTests','clean','package'],'standalone-package')
        packaged(ROOT,LOCAL,commit,'standalone',{alias:ROOT/module/'target'/(Path(module).name+'.jar') for alias,(module,*_) in MODULES.items()})
    elif args.action == 'build-standalone': build_standalone(args.tag)
    elif args.action == 'standalone': standalone(args.tag)
    else: freeze_compact(args.tag)
