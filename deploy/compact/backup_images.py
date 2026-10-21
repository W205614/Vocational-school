"""Maintain one verified offline image bundle for the current complete data backup."""
import hashlib
import json
from pathlib import Path
import subprocess
import uuid


def refresh(local, project, bundle, runner=subprocess.check_output, execute=subprocess.run, dispose=None):
    from maintenance import atomic_json
    from backup_retention import recycle
    from retire_images import verify_archive
    local = Path(local).resolve(); bundle = Path(bundle).resolve()
    if bundle.parent != local / 'backup' or bundle.is_symlink():
        raise RuntimeError('Current image backup requires a project-local data bundle')
    binding = json.loads((bundle / 'runtime-images.json').read_text(encoding='utf8'))
    ids = runner(['docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project=' + project], text=True).split()
    containers = json.loads(runner(['docker', 'inspect', *ids], text=True)) if ids else []
    actual = {c['Config']['Labels']['com.docker.compose.service']: c['Image'] for c in containers}
    if not actual or len(actual) != len(containers) or actual != binding:
        raise RuntimeError('Current containers differ from the complete data backup')
    folder = local / 'current-runtime-backup'
    if folder.is_symlink(): raise RuntimeError('Current image backup escaped its project directory')
    folder.mkdir(exist_ok=True)
    archive = folder / 'runtime-images.tar'; record = folder / 'recovery.json'
    previous = json.loads(record.read_text(encoding='utf8')) if record.is_file() else {}
    if archive.is_symlink() or record.is_symlink():
        raise RuntimeError('Current image backup must use project-owned regular files')
    reused = False
    if archive.is_file() and previous.get('services') == binding:
        with archive.open('rb') as source: checksum = hashlib.file_digest(source, 'sha256').hexdigest()
        if checksum != previous.get('sha256'):
            raise RuntimeError('Existing current image backup is corrupt; older data backups retained')
        tags = previous['tags']; reused = True
    else:
        # Export the *current* images only. Never archive superseded rollback images.
        if list(folder.glob('pending-*.tar')) or list(folder.glob('.tmp-pending-*')):
            raise RuntimeError('An incomplete image export already exists; inspect it before retrying')
        images = json.loads(runner(['docker', 'image', 'inspect', *sorted(set(binding.values()))], text=True))
        platforms = {i['Id']: {'architecture': i['Architecture'], 'os': i['Os']} for i in images}
        pending = folder / ('pending-' + uuid.uuid4().hex + '.tar')
        execute(['docker', 'image', 'save', '-o', str(pending), *sorted(set(binding.values()))], check=True)
        checksum = verify_archive(pending, set(binding.values()), platforms)
        if archive.exists():
            if not previous.get('services') or not previous.get('sha256'):
                raise RuntimeError('Existing image archive has no ownership record; inspect it first')
            (dispose or recycle)(archive)
        pending.replace(archive)
        tags = {tag: image['Id'] for image in images for tag in image.get('RepoTags') or []}
    value = {'status': 'PASSED', 'dataBundle': str(bundle), 'imageArchive': str(archive),
             'sha256': checksum, 'services': binding, 'tags': tags,
             'dataManifestSha256': hashlib.sha256((bundle / 'manifest.json').read_bytes()).hexdigest(),
             'bytes': archive.stat().st_size}
    atomic_json(record, value)
    (folder / 'README.md').write_text(
        '# Current recovery bundle\n\n'
        'Current data backup: `' + str(bundle) + '`\n\n'
        'This directory keeps one image archive. Unchanged images are reused; superseded image '
        'archives are recycled only after the replacement archive passes validation. '
        'Original image aliases and archive SHA-256 are recorded in recovery.json.\n\n'
        'Restore images with `python deploy/compact/backup_images.py restore`; then use '
        '`deploy/compact/restore.py` with the recorded data bundle and a new independent home/project.\n', encoding='utf8')
    return {'reused': reused, 'services': len(binding), 'sha256': checksum}


def restore(local):
    folder = Path(local) / 'current-runtime-backup'
    record = json.loads((folder / 'recovery.json').read_text(encoding='utf8'))
    archive = folder / 'runtime-images.tar'
    with archive.open('rb') as source:
        if hashlib.file_digest(source, 'sha256').hexdigest() != record['sha256']:
            raise RuntimeError('Current image backup checksum mismatch')
    subprocess.run(['docker', 'image', 'load', '-i', str(archive)], check=True)
    for tag, image in record['tags'].items():
        subprocess.run(['docker', 'image', 'tag', image, tag], check=True)


if __name__ == '__main__':
    import argparse
    import setup
    from maintenance import maintenance_lock
    parser = argparse.ArgumentParser(); parser.add_argument('action', choices=['refresh', 'restore'])
    args = parser.parse_args()
    with maintenance_lock(setup.LOCAL):
        if args.action == 'restore': restore(setup.LOCAL)
        else:
            import sys
            sys.path.insert(0, str(setup.SOURCE.parent / 'final'))
            from final_backup import verify
            bundle = Path(json.loads((setup.LOCAL / 'ops-status/backup.json').read_text())['lastBundle'])
            if not verify(bundle).get('quiesced'): raise RuntimeError('A complete data backup is required')
            print(json.dumps(refresh(setup.LOCAL, setup.PROJECT, bundle)))
