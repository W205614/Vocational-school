"""Create and trust a disposable CA only on GitHub-hosted ephemeral Linux runners."""
import os
import shlex
from pathlib import Path
import subprocess
from enable_tls import configure


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def main():
    if (os.environ.get('GITHUB_ACTIONS') != 'true'
            or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted'
            or os.name != 'posix'):
        raise RuntimeError('CI trust installation is restricted to ephemeral GitHub-hosted Linux')
    from setup import BASE, LOCAL
    directory = LOCAL / 'tls'
    directory.mkdir(mode=0o700)
    os.umask(0o077)
    ca, ca_key = directory / 'ca.crt', directory / 'ca.key'
    cert, key, csr = directory / 'server.crt', directory / 'server.key', directory / 'server.csr'
    run('openssl', 'req', '-x509', '-newkey', 'rsa:3072', '-nodes', '-days', '2',
        '-subj', '/CN=Zhixuetang isolated CI CA', '-keyout', str(ca_key), '-out', str(ca))
    run('openssl', 'req', '-newkey', 'rsa:3072', '-nodes', '-subj', '/CN=localhost',
        '-keyout', str(key), '-out', str(csr))
    extension = directory / 'server.ext'
    extension.write_text('subjectAltName=DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth\n')
    run('openssl', 'x509', '-req', '-in', str(csr), '-CA', str(ca), '-CAkey', str(ca_key),
        '-CAcreateserial', '-days', '2', '-extfile', str(extension), '-out', str(cert))
    # The nginx worker is unprivileged; only this disposable server key is readable
    # in its container. The CA signing key remains inside the runner's 0700 directory.
    key.chmod(0o644)
    run('sudo', 'install', '-m', '0644', str(ca), '/usr/local/share/ca-certificates/zhixuetang-ci.crt')
    run('sudo', 'update-ca-certificates')
    run('sudo', 'apt-get', 'install', '-y', '--no-install-recommends', 'libnss3-tools')
    nss = Path.home() / '.pki/nssdb'
    nss.mkdir(parents=True, exist_ok=True)
    if not (nss / 'cert9.db').exists():
        run('certutil', '-N', '--empty-password', '-d', 'sql:' + str(nss))
    run('certutil', '-A', '-d', 'sql:' + str(nss), '-n', 'Zhixuetang isolated CI CA',
        '-t', 'C,,', '-i', str(ca))
    firefox_store = directory / 'firefox-store'
    firefox_store.mkdir(mode=0o700)
    run('certutil', '-N', '--empty-password', '-d', 'sql:' + str(firefox_store))
    run('certutil', '-A', '-d', 'sql:' + str(firefox_store), '-n', 'Zhixuetang isolated CI CA',
        '-t', 'C,,', '-i', str(ca))
    firefox = subprocess.check_output([
        'node', '-e', "process.stdout.write(require('./frontend/node_modules/playwright').firefox.executablePath())"
    ], text=True)
    launcher = directory / 'firefox-with-ca.sh'
    launcher.write_text(
        '#!/bin/sh\nset -eu\nprevious=\nseeded=no\nfor argument do\n'
        '  if [ "$previous" = "-profile" ]; then\n'
        + '    cp ' + shlex.quote(str(firefox_store / 'cert9.db')) + ' "$argument/cert9.db"\n'
        + '    cp ' + shlex.quote(str(firefox_store / 'key4.db')) + ' "$argument/key4.db"\n'
        + '    seeded=yes\n  fi\n  previous=$argument\ndone\n'
        '[ "$seeded" = yes ] || { echo "Firefox profile argument is required" >&2; exit 2; }\n'
        + 'exec ' + shlex.quote(firefox) + ' "$@"\n', encoding='utf8')
    launcher.chmod(0o700)
    configure(BASE, cert, key)
    with Path(os.environ['GITHUB_ENV']).open('a', encoding='utf8') as env:
        env.write('ZXT_FIREFOX_EXECUTABLE=' + str(launcher.resolve())
                  + '\nZXT_COMPACT_HTTPS=1\nNODE_EXTRA_CA_CERTS=' + str(ca)
                  + '\nREQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt\n')


if __name__ == '__main__':
    main()
