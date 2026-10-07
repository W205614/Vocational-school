"""Bind evidence to one immutable release manifest; never publish private config contents."""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import subprocess
import uuid

def utcnow():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def tree_digest(directory):
    root = Path(directory)
    files = sorted(path for path in root.rglob('*') if path.is_file())
    if not files:
        raise ValueError('Fingerprint requires nonempty directory')
    return hashlib.sha256(json.dumps([(str(path.relative_to(root)).replace('\\', '/'), digest(path))
                                     for path in files], separators=(',', ':')).encode()).hexdigest()

def manifest(local, compose, source_root, snapshot):
    images = {}
    services = subprocess.check_output(compose + ['ps', '-q'], text=True).splitlines()
    if not services:
        raise ValueError('No running deployment to bind')
    for container in services:
        info = json.loads(subprocess.check_output(['docker', 'inspect', container], text=True))[0]
        service = info['Config']['Labels']['com.docker.compose.service']
        images[service] = info['Image']
    compose_file = Path(compose[compose.index('-f') + 1])
    configuration = hashlib.sha256((tree_digest(Path(local) / 'configs') + digest(compose_file)).encode()).hexdigest()
    return {'schemaVersion': 1, 'releaseRunId': uuid.uuid4().hex,
            'sourceCommit': subprocess.check_output(['git', '-C', str(source_root), 'rev-parse', 'HEAD'], text=True).strip(),
            'imageDigests': images, 'configFingerprint': configuration,
            'baseSnapshotFingerprint': digest(snapshot), 'createdAt': utcnow()}

BINDINGS = ('schemaVersion', 'releaseRunId', 'sourceCommit', 'imageDigests',
            'configFingerprint', 'baseSnapshotFingerprint')

def bind(data, expected, started=None):
    if data.get('status') not in {'PASSED', 'FAILED', 'INSUFFICIENT', 'INVALID'}:
        raise ValueError('Explicit terminal report status required')
    return {**data, 'evidence': {**{key: expected[key] for key in BINDINGS},
                              'startedAt': started or utcnow(), 'finishedAt': utcnow()}}

def validate(data, expected):
    evidence = data.get('evidence', {})
    if any(evidence.get(key) != expected.get(key) for key in BINDINGS):
        return False
    try:
        started = datetime.datetime.fromisoformat(evidence['startedAt'])
        finished = datetime.datetime.fromisoformat(evidence['finishedAt'])
        created = datetime.datetime.fromisoformat(expected['createdAt'])
        now = datetime.datetime.now(datetime.timezone.utc)
        if not created <= started <= finished <= now:
            return False
    except (KeyError, TypeError, ValueError):
        return False
    return data.get('status') == 'PASSED'

def save_report(local, name, data, started=None):
    local = Path(local)
    expected = json.loads((local / 'release-run.json').read_text(encoding='utf8'))
    report = bind(data, expected, started)
    directory = local / 'evidence' / expected['releaseRunId']
    directory.mkdir(parents=True, exist_ok=True)
    temporary = directory / (name + '.json.tmp')
    temporary.write_text(json.dumps(report, indent=2), encoding='utf8')
    temporary.replace(directory / (name + '.json'))
    return report

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['start'])
    parser.add_argument('--snapshot', required=True, type=Path)
    args = parser.parse_args()
    from setup import LOCAL, COMPOSE, ROOT
    value = manifest(LOCAL, COMPOSE, ROOT, args.snapshot)
    (LOCAL / 'release-run.json').write_text(json.dumps(value, indent=2), encoding='utf8')
    print('New bound evidence batch: ' + value['releaseRunId'])

if __name__ == '__main__':
    main()
