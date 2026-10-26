"""Generate bounded, loopback-only observability and incremental runtime defaults."""

from runtime_compose import active_compose, compose_command, runtime_compose
import copy
import datetime
import json
from pathlib import Path
import shutil
import yaml

MONITORS = {'prometheus', 'alertmanager', 'grafana'}
IMAGES = {'prometheus': 'prom/prometheus:v3.15.0', 'alertmanager': 'prom/alertmanager:v0.34.1',
          'grafana': 'grafana/grafana:13.2.4'}
LOGGING = {'driver': 'local', 'options': {'max-size': '10m', 'max-file': '3'}}


def harden(compose, offset=0):
    from application_layout import isolate_applications
    result = isolate_applications(compose)
    for service in result['services'].values():
        service.setdefault('restart', 'unless-stopped')
        service['logging'] = copy.deepcopy(LOGGING)
        service.setdefault('stop_grace_period', '90s')
    return result


def observability(compose, offset=0):
    result = harden(compose, offset)
    shared = {'restart': 'unless-stopped', 'logging': copy.deepcopy(LOGGING), 'stop_grace_period': '30s'}
    definitions = {
        'prometheus': {**shared, 'image': IMAGES['prometheus'], 'mem_limit': '256m',
                       'ports': [f'127.0.0.1:{24590 + offset}:9090'],
                       'command': ['--config.file=/etc/prometheus/prometheus.yml', '--storage.tsdb.path=/prometheus',
                                   '--storage.tsdb.retention.time=7d', '--storage.tsdb.retention.size=256MB',
                                   '--storage.tsdb.wal-compression','--storage.tsdb.wal-segment-size=16MB'],
                       'volumes': ['./.local/observability:/etc/prometheus:ro', 'compact_prometheus:/prometheus'],
                       'healthcheck': {'test': ['CMD', 'wget', '-q', '-O', '/dev/null', 'http://127.0.0.1:9090/-/ready'], 'interval': '15s', 'timeout': '4s', 'retries': 20}},
        'alertmanager': {**shared, 'image': IMAGES['alertmanager'], 'mem_limit': '128m',
                         'ports': [f'127.0.0.1:{24591 + offset}:9093'],
                         'command': ['--config.file=/etc/alertmanager/alertmanager.yml', '--storage.path=/alertmanager',
                                     '--data.retention=48h', '--cluster.listen-address='],
                         'volumes': ['./.local/observability/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro', 'compact_alertmanager:/alertmanager'],
                         'healthcheck': {'test': ['CMD', 'wget', '-q', '-O', '/dev/null', 'http://127.0.0.1:9093/-/ready'], 'interval': '15s', 'timeout': '4s', 'retries': 20}},
        'grafana': {**shared, 'image': IMAGES['grafana'], 'mem_limit': '256m',
                    'ports': [f'127.0.0.1:{24592 + offset}:3000'],
                    'environment': {'GF_SECURITY_ADMIN_USER': 'tianji', 'GF_SECURITY_ADMIN_PASSWORD': '${ACCEPTANCE_GRAFANA_PASSWORD:?Existing observability password required}',
                                    'GF_USERS_ALLOW_SIGN_UP': 'false', 'GF_ANALYTICS_REPORTING_ENABLED': 'false',
                                    'GF_ANALYTICS_CHECK_FOR_UPDATES': 'false', 'GF_NEWS_NEWS_FEED_ENABLED': 'false'},
                    'volumes': ['./.local/observability/grafana:/etc/grafana/provisioning:ro', './.local/observability/dashboards:/run/dashboards:ro', 'compact_grafana:/var/lib/grafana'],
                    'healthcheck': {'test': ['CMD-SHELL', 'wget -q -O /dev/null http://127.0.0.1:3000/api/health'], 'interval': '15s', 'timeout': '4s', 'retries': 20}}
    }
    for name, definition in definitions.items():
        if name not in result['services']:
            result['services'][name] = definition
    for name in MONITORS:
        result.setdefault('volumes', {}).setdefault('compact_' + name, {})
    gateway = result['services']['app-gateway']
    mount = './.local/ops-status:/run/ops-status:ro'
    if mount not in gateway.setdefault('volumes', []): gateway['volumes'].append(mount)
    return result


def module_defaults(alias, config):
    config = copy.deepcopy(config)
    # Static endpoints do not emit InstanceRegisteredEvent. The discovery
    # registration probe would otherwise stay UNKNOWN despite usable clients.
    config.setdefault('spring', {}).setdefault('cloud', {}).setdefault('discovery', {}).setdefault('client', {})['health-indicator'] = {'enabled': False}
    if alias == 'gateway':
        config.setdefault('server', {})['forward-headers-strategy'] = 'none'
        tj = config.setdefault('tj', {})
        tj.setdefault('gateway', {}).setdefault('proxy', {}).setdefault('trusted-addresses', ['web-student', 'web-admin'])
        tj.setdefault('backup', {})['status-file'] = '/run/ops-status/backup.properties'
    return config


def generate(home, compose, token):
    local = Path(home) / '.local'; directory = local / 'observability'
    directory.mkdir(parents=True, exist_ok=True); (local / 'ops-status').mkdir(exist_ok=True)
    credentials = directory / 'internal-token'
    credentials.write_text(token, encoding='utf8')
    jobs = []
    for group, aliases in {'identity': ['auth', 'user'], 'commerce': ['trade', 'pay', 'promotion'],
                           'education': ['course', 'learning', 'exam', 'remark'], 'support': ['media', 'search', 'message', 'data']}.items():
        port = compose['services']['app-' + group]['environment']['APP_PORT']
        for alias in aliases:
            jobs.append({'job_name': alias, 'metrics_path': '/_modules/' + alias + '/actuator/prometheus',
                         'http_headers': {'X-Internal-Token': {'files': ['/etc/prometheus/internal-token']}},
                         'static_configs': [{'targets': ['app-' + group + ':' + str(port)]}]})
    jobs.append({'job_name': 'gateway', 'metrics_path': '/actuator/prometheus',
                 'http_headers': {'X-Internal-Token': {'files': ['/etc/prometheus/internal-token']}},
                 'static_configs': [{'targets': ['app-gateway:' + str(compose['services']['app-gateway']['environment']['APP_PORT'])]}]})
    prometheus = {'global': {'scrape_interval': '15s', 'evaluation_interval': '15s'},
                  'rule_files': ['/etc/prometheus/alerts.yml'], 'scrape_configs': jobs,
                  'alerting': {'alertmanagers': [{'static_configs': [{'targets': ['alertmanager:9093']}]}]}}
    (directory / 'prometheus.yml').write_text(yaml.safe_dump(prometheus, sort_keys=False), encoding='utf8')
    (directory / 'alertmanager.yml').write_text(yaml.safe_dump({'route': {'receiver': 'local-dashboard', 'group_by': ['alertname', 'job'],
        'group_wait': '10s', 'group_interval': '1m', 'repeat_interval': '24h'}, 'receivers': [{'name': 'local-dashboard'}]}), encoding='utf8')
    source = Path(__file__).resolve().parent / 'observability'
    shutil.copyfile(source / 'alerts.yml', directory / 'alerts.yml')
    shutil.copytree(source / 'grafana', directory / 'grafana', dirs_exist_ok=True)
    shutil.copytree(source / 'dashboards', directory / 'dashboards', dirs_exist_ok=True)


def upgrade(home, token, offset=0, rabbit_hostname=None):
    """Preserve existing images, secrets, service settings and original data mounts."""
    home = Path(home); local = home / '.local'; path = runtime_compose(home)
    compose = yaml.safe_load(path.read_text(encoding='utf8'))
    expected = {'mysql', 'redis', 'rabbitmq', 'elasticsearch', 'app-identity', 'app-commerce', 'app-education', 'app-support', 'app-gateway', 'web-student', 'web-admin'}
    if set(compose['services']) - MONITORS != expected:
        raise RuntimeError('Unknown topology; refusing to rewrite an existing deployment')
    candidates = {}
    for path_config in (local / 'configs').glob('*.yml'):
        original = yaml.safe_load(path_config.read_text(encoding='utf8'))
        updated = module_defaults(path_config.stem, original)
        if updated != original: candidates[path_config] = yaml.safe_dump(updated, allow_unicode=True, sort_keys=False)
    candidate = observability(compose, offset)
    if not candidate['services']['rabbitmq'].get('hostname'):
        if not rabbit_hostname or not __import__('re').fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_.-]{0,62}',rabbit_hostname):
            raise RuntimeError('Record the current RabbitMQ hostname before changing container settings')
        candidate['services']['rabbitmq']['hostname']=rabbit_hostname
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    backup = local / 'operations-upgrade' / stamp; backup.mkdir(parents=True, exist_ok=False)
    shutil.copyfile(path, backup / 'compose.yaml')
    for original in candidates:
        shutil.copyfile(original, backup / original.name)
    for original, content in candidates.items(): original.write_text(content, encoding='utf8')
    path.write_text(yaml.safe_dump(candidate, sort_keys=False), encoding='utf8')
    generate(home, candidate, token)
    return backup


if __name__ == '__main__':
    import setup
    import subprocess
    from maintenance import maintenance_lock
    with maintenance_lock(setup.LOCAL):
        container=subprocess.check_output(setup.COMPOSE+['ps','-aq','rabbitmq'],text=True,timeout=30).strip()
        if not container or '\n' in container:raise RuntimeError('Existing RabbitMQ container identity is required')
        hostname=subprocess.check_output(['docker','inspect',container,'--format','{{.Config.Hostname}}'],text=True,timeout=30).strip()
        backup = upgrade(setup.BASE, setup.secrets_config()[0]['ACCEPTANCE_INTERNAL_TOKEN'], setup.OFFSET,hostname)
    print('Existing runtime defaults updated; originals retained in project .local/operations-upgrade.')
