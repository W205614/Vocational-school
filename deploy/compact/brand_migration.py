"""Plan precise private configuration changes while preserving deployment identities."""

import argparse
import copy
import datetime
import hashlib
import json
from pathlib import Path
import re
import shutil

import yaml
from runtime_compose import resolve_project

ROOT = Path(__file__).resolve().parents[2]
PROVIDER_BINDINGS = (
    ('tencent', 'appId'),
    ('tencent', 'secretId'),
    ('tencent', 'secretKey'),
    ('sms', 'ali', 'accessId'),
    ('sms', 'ali', 'accessSecret'),
    *[('pay', 'ali', field) for field in ('appId', 'merchantPrivateKey', 'publicKey')],
    *[
        ('pay', 'wx', field)
        for field in ('appId', 'mchId', 'mchSerialNo', 'privateKey', 'apiV3Key')
    ],
)


def placeholder(value):
    if not isinstance(value, str):
        return value
    match = re.fullmatch(r'\$\{(TJ_[A-Z0-9_]+)(:[^}]*)?\}', value)
    return (
        '${' + match[1].replace('TJ_', 'ZXT_') + (match[2] or '') + '}'
        if match
        else value
    )


def module_configuration(config, metadata=None):
    updated = copy.deepcopy(config)
    if 'tj' in updated:
        if 'zxt' in updated:
            raise ValueError('Both legacy and current property roots require review')
        updated['zxt'] = updated.pop('tj')
    for path in PROVIDER_BINDINGS:
        owner = updated.get('zxt', {})
        for key in path[:-1]:
            owner = owner.get(key, {})
        if path[-1] in owner:
            owner[path[-1]] = placeholder(owner[path[-1]])
    excludes = updated.get('spring', {}).get('autoconfigure', {}).get('exclude')
    if excludes is not None:
        owner = updated['spring']['autoconfigure']
        if isinstance(excludes, list):
            owner['exclude'] = [
                (
                    value.replace('com.tianji.', 'com.zhixuetang.', 1)
                    if value.startswith('com.tianji.')
                    else value
                )
                for value in excludes
            ]
        elif isinstance(excludes, str):
            owner['exclude'] = re.sub(
                r'(?<![\w.])com\.tianji\.', 'com.zhixuetang.', excludes
            )
        else:
            raise ValueError('Unsupported autoconfiguration exclusion format')
    aliases = updated.get('mybatis-plus', {}).get('type-aliases-package')
    if aliases:
        updated['mybatis-plus']['type-aliases-package'] = re.sub(
            r'(?<![\w.])com\.tianji\.', 'com.zhixuetang.', aliases
        )
    swagger = updated.get('zxt', {}).get('swagger')
    if swagger:
        package = swagger.get('package-path')
        if isinstance(package, str) and package.startswith('com.tianji.'):
            swagger['package-path'] = package.replace(
                'com.tianji.', 'com.zhixuetang.', 1
            )
        if metadata is not None:
            for key in (
                'title',
                'description',
                'contact-name',
                'contact-url',
                'contact-email',
            ):
                if key in metadata:
                    swagger[key] = metadata[key]
                else:
                    swagger.pop(key, None)
    return updated


def environment_keys(text):
    names = [
        line.split('=', 1)[0]
        for line in text.splitlines()
        if '=' in line and not line.lstrip().startswith('#')
    ]
    replacements = {
        name: name.replace('TJ_', 'ZXT_') for name in names if name.startswith('TJ_')
    }
    if any(name in names for name in replacements.values()):
        raise ValueError('Legacy and current environment names conflict')
    return ''.join(
        (
            replacements.get(line.split('=', 1)[0], line.split('=', 1)[0])
            + '='
            + line.split('=', 1)[1]
            if '=' in line and not line.lstrip().startswith('#')
            else line
        )
        for line in text.splitlines(keepends=True)
    )


def monitoring(document):
    """Change only metric expressions, known display labels and header names."""
    if isinstance(document, list):
        return [monitoring(value) for value in document]
    if not isinstance(document, dict):
        return document
    result = {}
    for key, value in document.items():
        if key == 'http_headers' and isinstance(value, dict):
            value = {
                ('X-ZXT-' + name[5:] if name.startswith('X-TJ-') else name): setting
                for name, setting in value.items()
            }
        elif key in {'expr', 'record'} and isinstance(value, str):
            value = re.sub(r'(?<![\w:])tj_', 'zxt_', value)
            value = re.sub(r'(?<![\w:])tianji:', 'zhixuetang:', value)
        elif key in {'title', 'name'} and isinstance(value, str):
            value = value.replace('Tianji Reliability', 'Zhixuetang Reliability')
        elif (
            key == 'summary' and isinstance(value, str) and value.startswith('Tianji ')
        ):
            value = 'Zhixuetang ' + value[7:]
        elif key == 'alert' and isinstance(value, str) and value.startswith('Tianji'):
            value = 'Zhixuetang' + value[6:]
        result[key] = monitoring(value)
    return result


def candidates(home):
    home = Path(home).resolve(strict=True)
    local = home / '.local'
    result = {}

    def add(path, value):
        if path.is_symlink() or not path.resolve().is_relative_to(local.resolve()):
            raise ValueError('Private migration path escaped deployment')
        before = path.read_bytes()
        after = value.encode('utf-8')
        if after != before:
            result[path] = (before, after)

    for path in sorted((local / 'configs').glob('*.yml')):
        before = yaml.safe_load(path.read_text(encoding='utf8'))
        sources = [
            ROOT / ('zxt-' + path.stem) / 'src/main/resources/application.yml',
            ROOT
            / ('zxt-' + path.stem)
            / ('zxt-' + path.stem + '-service')
            / 'src/main/resources/application.yml',
        ]
        metadata = None
        for source in sources:
            if source.is_file():
                metadata = (
                    yaml.safe_load(source.read_text(encoding='utf8'))
                    .get('zxt', {})
                    .get('swagger', {})
                )
                break
        after = module_configuration(before, metadata)
        if after != before:
            add(path, yaml.safe_dump(after, allow_unicode=True, sort_keys=False))
    environment = local / 'app.env'
    if environment.is_file():
        add(environment, environment_keys(environment.read_bytes().decode('utf8')))
    for path in sorted((local / 'observability').rglob('*')):
        if path.suffix not in {'.json', '.yml', '.yaml'} or not path.is_file():
            continue
        before = (
            json.loads(path.read_text(encoding='utf8'))
            if path.suffix == '.json'
            else yaml.safe_load(path.read_text(encoding='utf8'))
        )
        after = monitoring(before)
        if before != after:
            add(
                path,
                (
                    json.dumps(after, ensure_ascii=False, indent=2) + '\n'
                    if path.suffix == '.json'
                    else yaml.safe_dump(after, allow_unicode=True, sort_keys=False)
                ),
            )
    return result


def migrate(home, apply=False):
    home = Path(home).resolve(strict=True)
    project = resolve_project(home)
    changes = candidates(home)
    report = {
        'project': project,
        'applied': apply,
        'files': [
            {
                'path': str(path.relative_to(home)),
                'before_sha256': hashlib.sha256(before).hexdigest(),
                'after_sha256': hashlib.sha256(after).hexdigest(),
            }
            for path, (before, after) in changes.items()
        ],
    }
    if apply and changes:
        from maintenance import maintenance_lock

        with maintenance_lock(home / '.local'):
            if resolve_project(home) != project or candidates(home) != changes:
                raise ValueError('Migration inputs changed; review a fresh plan')
            stamp = datetime.datetime.now(datetime.timezone.utc).strftime(
                '%Y%m%dT%H%M%S.%fZ'
            )
            backup = home / '.local/brand-migration' / stamp
            backup.mkdir(parents=True)
            # The backup contains credentials and receives the same private ACL.
            import importlib.util

            spec = importlib.util.spec_from_file_location(
                'private_migration_permissions', ROOT / 'deploy/production/database.py'
            )
            permissions = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(permissions)
            permissions.protect(backup)
            for path, (before, after) in changes.items():
                original = backup / path.relative_to(home / '.local')
                original.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, original)
            for path, (before, after) in changes.items():
                # Preserve each target file's existing permissions.
                path.write_bytes(after)
            report['backup'] = str(backup)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    print(json.dumps(migrate(args.home, args.apply), ensure_ascii=False, indent=2))
