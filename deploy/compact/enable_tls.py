"""Use an operator-provided certificate on both existing web services."""
import argparse
from pathlib import Path
import subprocess
import json
import shutil
import datetime
import yaml
from runtime_compose import active_compose


def configure(home, certificate, private_key):
    home = Path(home).resolve()
    from operations_config import private_directory
    private_directory(home / '.local')
    certificate, private_key = Path(certificate).resolve(), Path(private_key).resolve()
    cert_public = subprocess.check_output(
        ['openssl', 'x509', '-in', str(certificate), '-pubkey', '-noout'])
    key_public = subprocess.check_output(
        ['openssl', 'pkey', '-in', str(private_key), '-passin', 'pass:', '-pubout'])
    if cert_public != key_public:
        raise ValueError('TLS certificate and private key do not match')
    path = active_compose(home)
    if path.is_symlink():
        raise ValueError('Runtime Compose must not be a symlink')
    compose = yaml.safe_load(path.read_text(encoding='utf8'))
    template = Path(__file__).resolve().parents[1] / 'acceptance/docker/nginx.conf.template'
    text = template.read_text(encoding='utf8').replace('listen 8080;', '''listen 8080;
    listen 8443 ssl;
    ssl_certificate /run/tls/server.crt;
    ssl_certificate_key /run/tls/server.key;
    ssl_protocols TLSv1.2 TLSv1.3;''')
    target = home / '.local/web-tls.conf.template'
    if target.is_symlink():
        raise ValueError('TLS template must not be a symlink')
    target.write_text(text, encoding='utf8')
    for name in ('web-student', 'web-admin'):
        service = compose['services'][name]
        if any(not isinstance(port, str) or not port.endswith(':8080')
               for port in service['ports']):
            raise ValueError('TLS setup requires unmodified HTTP web port bindings')
        service['ports'] = [port[:-4] + '8443' for port in service['ports']]
        service.setdefault('volumes', []).extend([
            str(certificate).replace('\\', '/') + ':/run/tls/server.crt:ro',
            str(private_key).replace('\\', '/') + ':/run/tls/server.key:ro',
            str(target).replace('\\', '/') + ':/etc/nginx/templates/default.conf.template:ro',
        ])
    original = private_directory(home / '.local/tls-runtime-originals')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    shutil.copyfile(path, original / (stamp + '.yaml'))
    path.write_text(yaml.safe_dump(compose, sort_keys=False), encoding='utf8')
    (home / '.local/web-tls.json').write_text(
        json.dumps({'scheme': 'https', 'host': 'localhost'}, indent=2), encoding='utf8')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--certificate', type=Path, required=True)
    parser.add_argument('--private-key', type=Path, required=True)
    args = parser.parse_args()
    configure(args.home, args.certificate, args.private_key)
