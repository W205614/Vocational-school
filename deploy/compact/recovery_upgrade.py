"""Read-only upgrade provenance checks and isolated recovery proof helpers."""
import copy
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tarfile

ALIASES = {'identity', 'commerce', 'education', 'support', 'gateway', 'student', 'admin'}
SERVICES = {('web-' if alias in {'student', 'admin'} else 'app-') + alias for alias in ALIASES}
IMAGE_ID = re.compile(r'sha256:[0-9a-f]{64}')


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def verify_source_commit(root, commit):
    if not isinstance(commit, str) or not re.fullmatch(r'[0-9a-f]{40}|[0-9a-f]{64}', commit):
        raise RuntimeError('Upgrade source needs a complete Git commit ID')
    git = ['git', '-C', str(root)]
    subprocess.run(git + ['cat-file', '-e', commit + '^{commit}'], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    changed = subprocess.check_output(git + ['diff', '--name-only', commit, '--'], text=True).splitlines()
    untracked = subprocess.check_output(git + ['ls-files', '--others', '--exclude-standard'], text=True).splitlines()
    from build_provenance import production
    if any(production(path) or path.startswith('deploy/acceptance/migrations/') or path == 'deploy/compact/setup.py' for path in changed + untracked):
        raise RuntimeError('Upgrade images, current production source, migrations and generated configuration must share their recorded commit')


def inspect_image(image):
    return subprocess.check_output(['docker', 'image', 'inspect', image, '--format', '{{.Id}}'], text=True).strip()


def load_upgrade(home, root, inspect=inspect_image, verify_source=verify_source_commit):
    """Validate all seven bindings before creating or changing a recovery deployment."""
    home = Path(home).resolve()
    files = {name: home / '.local' / name for name in ('build-source.json', 'images.json')}
    if any(not path.is_file() or path.is_symlink() for path in files.values()):
        raise RuntimeError('Upgrade deployment needs complete source and image manifests')
    raw = {name: path.read_bytes() for name, path in files.items()}
    source = json.loads(raw['build-source.json'])
    images = json.loads(raw['images.json'])
    if not isinstance(source, dict) or not isinstance(images, dict) or set(images) != ALIASES:
        raise RuntimeError('All seven compact application images are required for recovery upgrade')
    commit = source.get('sourceCommit')
    verify_source(root, commit)
    expected = {}
    for alias, details in images.items():
        if not isinstance(details, dict) or not isinstance(details.get('imageId'), str) or not IMAGE_ID.fullmatch(details['imageId']):
            raise RuntimeError('Upgrade requires immutable application image IDs')
        if not isinstance(details.get('image'), str) or not details['image']:
            raise RuntimeError('Upgrade image manifest is incomplete')
        if details.get('sourceCommit', commit) != commit:
            raise RuntimeError('Upgrade application images have mixed source commits')
        if alias not in {'student', 'admin'} and (not isinstance(details.get('jarSha256'), str) or not re.fullmatch(r'[0-9a-f]{64}', details['jarSha256'])):
            raise RuntimeError('Upgrade backend artifact provenance is incomplete')
        image = details['imageId']
        if inspect(image) != image:
            raise RuntimeError('Upgrade image is unavailable; load the verified image archive first')
        expected[('web-' if alias in {'student', 'admin'} else 'app-') + alias] = image
    if source.get('imageDigests') != expected:
        raise RuntimeError('Upgrade source manifest and all seven image bindings differ')
    return {'sourceCommit': commit, 'imageDigests': expected, 'manifests': {name: hashlib.sha256(data).hexdigest() for name, data in raw.items()},
            'sourceManifest': source, 'imageManifest': images}


def upgraded_configuration(recorded, generated, application_images):
    """Retain recovered infrastructure and fresh volumes; use new application settings."""
    from operations_config import MONITORS
    if set(application_images) != SERVICES or set(recorded['services'])-MONITORS != set(generated['services'])-MONITORS:
        raise RuntimeError('Recovery upgrade topology differs from the verified seven-image deployment')
    if recorded.get('name') != generated.get('name'):
        raise RuntimeError('Upgrade configuration must belong to the isolated recovery project')
    result = copy.deepcopy(recorded)
    for name, image in application_images.items():
        if not IMAGE_ID.fullmatch(image):
            raise RuntimeError('Upgrade Compose requires immutable image IDs')
        service = copy.deepcopy(generated['services'][name])
        service.pop('container_name', None)
        service['image'] = image
        result['services'][name] = service
    for name in MONITORS & set(generated['services']):
        result['services'][name] = copy.deepcopy(generated['services'][name])
    for name in MONITORS:
        if 'compact_'+name in generated.get('volumes',{}):result.setdefault('volumes',{}).setdefault('compact_'+name,{})
    if any(settings for settings in result.get('volumes', {}).values()):
        raise RuntimeError('Upgrade cannot reference shared or externally named volumes')
    for service in result['services'].values():
        for mount in service.get('volumes', []):
            source = mount.split(':')[0] if isinstance(mount, str) else mount.get('source', '')
            if source not in result.get('volumes', {}) and not (source.startswith('./.local/') and '..' not in PurePosixPath(source).parts):
                raise RuntimeError('Upgrade mount escaped the isolated recovery home')
    return result


def preserve_generated_configs(home):
    local = Path(home).resolve() / '.local'
    source, destination = local / 'configs', local / 'generated-upgrade-configs'
    if source.is_symlink() or not source.is_dir() or destination.exists():
        raise RuntimeError('Generated upgrade configuration needs a fresh independent directory')
    for path in source.iterdir():
        if not path.is_file() or path.is_symlink() or path.suffix not in {'.yml', '.security'}:
            raise RuntimeError('Unexpected generated upgrade configuration')
    shutil.copytree(source, destination)
    return destination


def activate_generated_configs(home, saved):
    local = Path(home).resolve() / '.local'
    source = Path(saved).resolve()
    current, preserved = local / 'configs', local / 'raw-restored-module-configs'
    if source != local / 'generated-upgrade-configs' or not source.is_dir() or current.is_symlink() or preserved.exists():
        raise RuntimeError('Upgrade configuration must stay within the new isolated recovery home')
    if not current.resolve().is_relative_to(local) or not preserved.resolve().is_relative_to(local):
        raise RuntimeError('Recovery configuration paths escaped the isolated home')
    current.rename(preserved)
    shutil.copytree(source, current)
    return fingerprint({path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(current.iterdir())})


def copy_verified_manifests(source_home, recovery_home, proof):
    destination = Path(recovery_home).resolve() / '.local'
    for name, expected in proof['manifests'].items():
        source = Path(source_home).resolve() / '.local' / name
        data = source.read_bytes()
        if source.is_symlink() or hashlib.sha256(data).hexdigest() != expected:
            raise RuntimeError('Upgrade manifests changed after preflight validation')
        (destination / name).write_bytes(data)


def tar_inventory(path):
    result = {}
    with tarfile.open(path, mode='r:gz') as archive:
        for item in archive:
            name = PurePosixPath(item.name)
            if name.is_absolute() or '..' in name.parts or '\\' in item.name:
                raise RuntimeError('Unsafe recovery broker archive path')
            key = str(name)
            if key in result:
                raise RuntimeError('Duplicate recovery broker archive entry')
            record = {'type': item.type.decode('ascii'), 'mode': item.mode, 'uid': item.uid, 'gid': item.gid}
            if item.isfile():
                with archive.extractfile(item) as stream:
                    record.update(size=item.size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
            elif item.issym() or item.islnk():
                target = PurePosixPath(item.linkname)
                if target.is_absolute() or '..' in target.parts:
                    raise RuntimeError('Unsafe recovery broker archive link')
                record['link'] = item.linkname
            elif not item.isdir():
                raise RuntimeError('Unsupported recovery broker archive member')
            result[key] = record
    return result


def compare_broker_archives(expected, restored):
    source, actual = tar_inventory(expected), tar_inventory(restored)
    if not source or source != actual:
        raise RuntimeError('Restored broker file inventory or contents differ from the recovery bundle')
    return {'brokerBytesRestored': True, 'brokerFileContentsMatched': True, 'brokerMemberCount': len(actual), 'brokerInventoryFingerprint': fingerprint(actual)}


def compare_redis_snapshots(expected, restored, now_ms, tolerance_ms=1000):
    def indexed(snapshot):
        if snapshot.get('format') not in (1, 2) or not isinstance(snapshot.get('records'), list):
            raise RuntimeError('Unsupported Redis recovery snapshot')
        result = {}
        for row in snapshot['records']:
            key = (row['db'], row['key'])
            if snapshot['format'] == 2 and (row.get('type') not in {'string','hash','set','list','zset'} or not re.fullmatch(r'[0-9a-f]{64}', str(row.get('semanticSha256', '')))):
                raise RuntimeError('Invalid Redis semantic fingerprint')
            if key in result:
                raise RuntimeError('Duplicate Redis recovery record')
            result[key] = row
        return result
    source, actual = indexed(expected), indexed(restored)
    semantic = expected['format'] == restored['format'] == 2
    reserialized = 0
    for key, row in actual.items():
        old = source.get(key)
        if old is None:
            raise RuntimeError('Restored Redis values differ from the recovery bundle')
        if semantic:
            if row['type'] != old['type'] or row['semanticSha256'] != old['semanticSha256']:
                raise RuntimeError('Restored Redis types or logical contents differ from the recovery bundle')
            reserialized += int(row['value'] != old['value'])
        elif row['value'] != old['value']:
            raise RuntimeError('Restored Redis values differ from the recovery bundle')
        expiry, restored_expiry = old['expiresAt'], row['expiresAt']
        if (expiry is None and restored_expiry is not None) or (expiry is not None and (restored_expiry is None or abs(expiry - restored_expiry) > tolerance_ms)):
            raise RuntimeError('Restored Redis expiration differs from the recovery bundle')
    for key, row in source.items():
        expiry = row['expiresAt']
        if key not in actual and (expiry is None or expiry > now_ms + tolerance_ms):
            raise RuntimeError('A live Redis recovery record is missing')
    return {'redisRecords': len(source), 'redisRestoredRecords': len(actual), 'redisValuesAndExpiryMatched': True,
            'redisExpiredRecordsSkipped': len(source) - len(actual), 'redisComparison': 'semantic' if semantic else 'dump-bytes',
            'redisReserializedRecords': reserialized}
