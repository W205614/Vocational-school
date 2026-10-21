"""Project-owned backup rotation; Windows disposal always uses the Recycle Bin."""
import hashlib
import json
import os
from pathlib import Path
import re

OWNER = 'tianji-backup-v2'
STAMP = re.compile(r'[0-9]{8}T[0-9]{6}\.[0-9]{6}Z')


def recycle(path):
    if os.name != 'nt':
        # The production policy never silently changes into permanent deletion.
        raise RuntimeError('Automatic backup retirement requires the Windows Recycle Bin')
    from send2trash.win.modern import send2trash
    send2trash(str(path))
    if path.exists():
        raise RuntimeError('Old backup was not moved to the Recycle Bin')


def manifest_hash(path):
    return hashlib.sha256((path / 'manifest.json').read_bytes()).hexdigest()


def owned(local, path):
    for name, owner in [('backup-owner.json', OWNER), ('automatic-owner.json', 'tianji-maintenance-v1')]:
        marker = path / name
        if marker.is_file() and not marker.is_symlink():
            value = json.loads(marker.read_text(encoding='utf8'))
            return value == {'owner': owner, 'home': str(local), 'manifestSha256': manifest_hash(path)}
    return False


def retain_latest(local, bundle, verify, dispose=None):
    from maintenance import atomic_json
    local = Path(local)
    if local.is_symlink() or (local / 'backup').is_symlink() or Path(bundle).is_symlink():
        raise RuntimeError('Backup retention target escaped its project directory')
    local = local.resolve(); root = local / 'backup'; bundle = Path(bundle).resolve()
    if bundle.parent != root or not STAMP.fullmatch(bundle.name):
        raise RuntimeError('Backup retention target escaped its project directory')
    if not verify(bundle).get('quiesced'):
        raise RuntimeError('Only a verified complete backup can replace older backups')
    atomic_json(bundle / 'backup-owner.json', {'owner': OWNER, 'home': str(local),
                                             'manifestSha256': manifest_hash(bundle)})
    removed = []
    for path in sorted(root.iterdir()):
        if path == bundle or not STAMP.fullmatch(path.name) or path.name >= bundle.name:
            continue
        if path.is_symlink() or not path.is_dir(): continue
        try:
            if not owned(local, path) or not verify(path).get('quiesced'): continue
            pin = path / 'publication-pin.json'
            if pin.exists() and json.loads(pin.read_text(encoding='utf8')).get('purpose') != 'publication-rollback':
                continue
        except (OSError, ValueError, RuntimeError, KeyError):
            continue
        # A newer complete, image-backed snapshot supersedes our old publication pin.
        if path.resolve().parent != root or path.is_symlink() or not owned(local, path):
            raise RuntimeError('Backup ownership changed during retention')
        (dispose or recycle)(path)
        removed.append(path.name)
    return removed
