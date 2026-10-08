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
    build_path=Path(local)/'build-source.json'
    if not build_path.exists():raise ValueError('Frozen image/source manifest is required')
    build=json.loads(build_path.read_text(encoding='utf8'))
    actual={name:image for name,image in images.items() if name.startswith(('app-','web-'))}
    if actual!=build.get('imageDigests'):raise ValueError('Running application images differ from the frozen source manifest')
    return {'schemaVersion': 1, 'releaseRunId': uuid.uuid4().hex,
            'sourceCommit': build['sourceCommit'],
            'imageDigests': images, 'configFingerprint': configuration,
            'baseSnapshotFingerprint': digest(snapshot), 'snapshotPath':str(Path(snapshot).resolve()), 'createdAt': utcnow()}

def verify_publication(source_root, measured, publication='HEAD', deployment_manifest=None):
    """A README-only descendant does not rewrite the commit recorded in measurements."""
    base=['git','-C',str(source_root)]
    head=subprocess.check_output(base+['rev-parse',publication],text=True).strip()
    if subprocess.run(base+['merge-base','--is-ancestor',measured,head],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode:
        return False
    changed=subprocess.check_output(base+['diff','--name-only',measured,head],text=True).splitlines()
    if any(not path.endswith('.md') and not path.startswith('deploy/compact/reports/') for path in changed):return False
    dirty=subprocess.check_output(base+['diff','--name-only',head],text=True).splitlines()
    untracked=subprocess.check_output(base+['ls-files','--others','--exclude-standard'],text=True).splitlines()
    allowed={'.gitignore'}
    # The public Compose template uses buildable image names. Freezing a local
    # deployment replaces them with exact image IDs. This generated worktree
    # file is accepted only when the entire live configuration equals the
    # captured manifest; it may not be changed in the publication commit.
    if deployment_manifest and deployment_manifest.get('sourceCommit')==measured:
        home=Path(source_root)/'deploy/compact'
        from config_equivalence import config_fingerprint
        try:
            if config_fingerprint(home)==deployment_manifest.get('configFingerprint'):
                allowed.add('deploy/compact/compose.yaml')
        except (OSError,ValueError):pass
    return not any(not path.endswith('.md') and path not in allowed for path in dirty) and not any('/src/main/' in path or path.endswith('pom.xml') or path.startswith('frontend/') for path in untracked)

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
    if not __import__('re').fullmatch(r'[a-z][a-z0-9-]{0,63}',name):raise ValueError('Invalid evidence check name')
    expected = json.loads((local / 'release-run.json').read_text(encoding='utf8'))
    report = bind(data, expected, started)
    directory = local / 'evidence' / expected['releaseRunId']
    directory.mkdir(parents=True, exist_ok=True)
    attempt=directory/'attempts'/name
    attempt.mkdir(parents=True,exist_ok=True)
    report['attemptId']=uuid.uuid4().hex
    (attempt/(report['attemptId']+'.json')).write_text(json.dumps(report,indent=2),encoding='utf8')
    temporary = directory / (name + '.json.tmp')
    temporary.write_text(json.dumps(report, indent=2), encoding='utf8')
    temporary.replace(directory / (name + '.json'))
    return report

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['start'])
    parser.add_argument('--snapshot', required=True, type=Path)
    parser.add_argument('--audit-batch', type=Path)
    args = parser.parse_args()
    from setup import LOCAL, COMPOSE, ROOT
    value = manifest(LOCAL, COMPOSE, ROOT, args.snapshot)
    if args.audit_batch:
        batch=json.loads(args.audit_batch.read_text(encoding='utf8'));value['releaseRunId']=batch['releaseRunId']
        value['baselineSourceCommit']=batch['sourceCommit']
    (LOCAL / 'release-run.json').write_text(json.dumps(value, indent=2), encoding='utf8')
    print('New bound evidence batch: ' + value['releaseRunId'])

if __name__ == '__main__':
    main()
