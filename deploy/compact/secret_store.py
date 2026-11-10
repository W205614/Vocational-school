"""Encrypt generated host credentials; inject values only into child environments.

Windows binds the encryption key to the current account with DPAPI. Other hosts
must supply a Fernet key from their external secret manager through ZXT_SECRET_KEY.
Existing plaintext installations are read without silently rewriting recovery data.
"""
import json
import os
from pathlib import Path
from cryptography.fernet import Fernet


def cipher(home, create=False):
    if os.name == 'nt':
        import win32crypt
        from operations_config import private_directory, private_file
        local = private_directory(Path(home) / '.local')
        path = local / 'secrets.key.dpapi'
        if not path.exists():
            if not create or any(local.glob('*.enc')):
                raise RuntimeError('DPAPI key is missing; restore the original key and account access')
            wrapped = win32crypt.CryptProtectData(
                Fernet.generate_key(), 'Zhixuetang host credentials', None, None, None, 1)
            with path.open('xb') as output:
                output.write(wrapped)
        private_file(path)
        key = win32crypt.CryptUnprotectData(path.read_bytes(), None, None, None, 1)[1]
    else:
        key = os.environ.get('ZXT_SECRET_KEY')
        if not key:
            raise RuntimeError('ZXT_SECRET_KEY must be supplied by an external secret manager')
    return Fernet(key)


def write_values(home, name, values):
    from operations_config import private_directory, private_file
    directory = private_directory(Path(home) / '.local')
    path = directory / (name + '.enc')
    payload = cipher(home, create=True).encrypt(json.dumps(values).encode('utf8'))
    if not path.exists():
        path.touch(mode=0o600)
    private_file(path).write_bytes(payload)


def read_values(home, name):
    path = Path(home) / '.local' / (name + '.enc')
    return json.loads(cipher(home).decrypt(path.read_bytes()))


def environment(home):
    home = Path(home)
    if (home / '.local/environment.enc').is_file():
        values = read_values(home, 'environment')
    else:
        from operations_config import private_file
        path = private_file(home / '.env')
        values = dict(line.split('=', 1) for line in
                      path.read_text(encoding='utf8').splitlines()
                      if '=' in line and not line.startswith('#'))
    os.environ.update(values)
    if (home / '.local/database-accounts.enc').is_file():
        os.environ.update({'ZXT_DB_' + alias: value for alias, value
                           in read_values(home, 'database-accounts').items()})
    return values


def load_existing(home):
    home = Path(home)
    if (home / '.env').is_file() or (home / '.local/environment.enc').is_file():
        return environment(home)
    return {}
