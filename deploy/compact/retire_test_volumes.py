"""Plan and explicitly retire proven-recreatable test volumes; never infer disposal from being unused.

Each --proof is a private JSON manifest with schemaVersion=1, project, home,
kind (fresh-synthetic/recovery-clone), noUniqueUserData=true, sourceCommit,
volumeNames, sourceBackupManifestSha (null for fresh synthetic), proofPath,
proofSha256 and hashed initState/accounts/fixture references {path,sha256}.
Recovery clones additionally require sourceBackup, historyProof and
syntheticWritesOnly=true. The operator records that assertion only after auditing
the clone's writes; this tool cannot infer absence of unique user data from Docker.

Planning never changes Docker. --apply requires the exact saved plan and repeats
the gate, proof, ownership and global container-reference checks before removal.
"""
import argparse
import datetime
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
PRIMARY_PROJECT = 'tianji-compact'
HEX = re.compile(r'[0-9a-f]{64}')
NAME = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.-]{1,254}')


def sha(path):
    with Path(path).open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def owned_path(path, root):
    candidate = Path(path)
    if candidate.is_symlink():
        raise RuntimeError('A proof path cannot be a symlink')
    resolved = candidate.resolve()
    if not resolved.is_relative_to(Path(root).resolve()):
        raise RuntimeError('Proof escaped this workspace')
    # Reject symlinked ancestors as well, even when they resolve back into root.
    for parent in candidate.absolute().parents:
        if parent == Path(root).absolute():
            break
        if parent.is_symlink():
            raise RuntimeError('A proof ancestor cannot be a symlink')
    return resolved


def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf8'))


def hashed_file(value, root, expected=None):
    if not isinstance(value, dict) or not HEX.fullmatch(str(value.get('sha256', ''))):
        raise RuntimeError('A hashed proof reference is required')
    path = owned_path(value.get('path', ''), root)
    if expected is not None and path != Path(expected).resolve():
        raise RuntimeError('Proof reference is not the expected project artifact')
    if not path.is_file() or sha(path) != value['sha256']:
        raise RuntimeError('Proof file is missing or its checksum changed')
    return {'path': str(path), 'sha256': value['sha256']}


def reference(value, root, expected=None):
    ref = hashed_file(value, root, expected)
    return read_json(ref['path']), ref


class Docker:
    @staticmethod
    def output(args):
        return subprocess.check_output(['docker', *args], text=True, stderr=subprocess.PIPE).strip()

    def containers(self, project=None):
        command = ['ps', '-aq']
        if project:
            command += ['--filter', 'label=com.docker.compose.project=' + project]
        ids = self.output(command).splitlines()
        return json.loads(self.output(['inspect', *ids])) if ids else []

    def volume(self, name):
        try:
            rows = json.loads(self.output(['volume', 'inspect', name]))
        except subprocess.CalledProcessError:
            return None
        if len(rows) != 1 or rows[0].get('Name') != name:
            raise RuntimeError('Ambiguous volume inventory')
        return rows[0]

    def remove(self, name):
        subprocess.run(['docker', 'volume', 'rm', name], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def references(containers):
    return {mount['Name'] for container in containers for mount in container.get('Mounts', [])
            if mount.get('Type') == 'volume' and mount.get('Name')}


def snapshot(volume):
    return {key: volume.get(key) for key in ('Name', 'Driver', 'Labels', 'Options', 'Scope', 'CreatedAt')}


def allowed_project(project):
    return isinstance(project, str) and (project in {'vocational-acceptance', 'tianji-acceptance', 'tianji-final'}
            or bool(re.fullmatch(r'tianji-(recovery|final|opt|redis-proof|demo|personal|acceptance)-[a-z0-9-]+', project)))


def check_gate(primary_home, root, docker):
    """Re-read the same final demo gate used for source/image retirement."""
    from evidence import validate, manifest, verify_publication
    from retirement_policy import healthy_runtime
    from runtime_compose import active_compose
    home = owned_path(primary_home, root)
    local = home / '.local'
    expected_path = local / 'release-run.json'
    expected = read_json(expected_path)
    if not re.fullmatch(r'[0-9a-f]{32}', str(expected.get('releaseRunId', ''))):
        raise RuntimeError('A bound release batch is required')
    gate_path = local / 'evidence' / expected['releaseRunId'] / 'release-gate.json'
    gate = read_json(gate_path)
    if not validate(gate, expected) or gate.get('acceptanceScope') != 'single-host-demo':
        raise RuntimeError('The bound single-host demo gate must pass before volume retirement')
    if not gate.get('checks') or not all(value is True for value in gate['checks'].values()):
        raise RuntimeError('Every final demo check must pass')
    for value in gate.get('inputs', {}).values():
        reference(value, root)
    if set(gate.get('inputs', {})) != {'backend', 'roles', 'recovery', 'history'}:
        raise RuntimeError('The final gate lacks its original execution reports')
    compose_path = active_compose(home)
    compose = yaml.safe_load(compose_path.read_text(encoding='utf8'))
    healthy_runtime(docker.containers(PRIMARY_PROJECT), compose, PRIMARY_PROJECT)
    command = ['docker', 'compose', '--project-directory', str(home), '-p', PRIMARY_PROJECT,
               '-f', str(compose_path), '--env-file', str(home / '.env')]
    live = manifest(local, command, root, expected['snapshotPath'])
    if any(live[key] != expected[key] for key in
           ('sourceCommit', 'imageDigests', 'configFingerprint', 'baseSnapshotFingerprint')):
        raise RuntimeError('The final deployment changed after acceptance')
    if not verify_publication(root, expected['sourceCommit']):
        raise RuntimeError('Publication differs from the accepted source')
    return {'expected': expected, 'gate': gate,
            'files': [{'path': str(expected_path), 'sha256': sha(expected_path)},
                      {'path': str(gate_path), 'sha256': sha(gate_path)}]}


def verify_bundle(path):
    sys.path.insert(0, str(ROOT / 'deploy' / 'operations'))
    from runtime_backup import verify
    return verify(path)


def validate_proof(path, context, root, primary_home, bundle_verify=verify_bundle):
    path = owned_path(path, root)
    proof = read_json(path)
    project = proof.get('project')
    if proof.get('schemaVersion') != 1 or not allowed_project(project) or project == PRIMARY_PROJECT:
        raise RuntimeError('Only explicitly owned test projects are eligible')
    if proof.get('noUniqueUserData') is not True:
        raise RuntimeError('Absence of unique user data has not been explicitly established')
    home = owned_path(proof.get('home', ''), root)
    if home == Path(primary_home).resolve() or home in Path(primary_home).resolve().parents:
        raise RuntimeError('The primary deployment and its ancestors are protected')
    expected = context['expected']
    if proof.get('sourceCommit') != expected.get('sourceCommit'):
        raise RuntimeError('Test proof belongs to a different source revision')
    volume_names = proof.get('volumeNames')
    if not isinstance(volume_names, list) or not volume_names or any(not NAME.fullmatch(str(name)) for name in volume_names):
        raise RuntimeError('An explicit volume inventory is required')
    local = home / '.local'
    dependencies = [{'path': str(path), 'sha256': sha(path)}]
    state, ref = reference(proof.get('initState'), root, local / 'init-state.json'); dependencies.append(ref)
    accounts, ref = reference(proof.get('accounts'), root, local / 'accounts.json'); dependencies.append(ref)
    fixture, ref = reference(proof.get('fixture'), root, local / 'browser-fixture.json'); dependencies.append(ref)
    if state.get('phase') != 'COMPLETE' or not {'student', 'teacher', 'admin'} <= set(accounts):
        raise RuntimeError('A complete independently initialized fixture is required')
    if not fixture.get('marker') or not str(fixture.get('course', '')).isdigit():
        raise RuntimeError('A named synthetic browser fixture is required')
    from runtime_compose import active_compose
    compose_path = owned_path(active_compose(home), root)
    compose = yaml.safe_load(compose_path.read_text(encoding='utf8'))
    dependencies.append({'path': str(compose_path), 'sha256': sha(compose_path)})
    if compose.get('name') != project or not compose.get('volumes'):
        raise RuntimeError('The source Compose project does not match the proof')
    if any(settings and (settings.get('external') or settings.get('name')) for settings in compose['volumes'].values()):
        raise RuntimeError('Shared or explicitly renamed Compose volumes are protected')
    build_path = owned_path(local / 'build-source.json', root)
    build = read_json(build_path); dependencies.append({'path': str(build_path), 'sha256': sha(build_path)})
    apps = {name: image for name, image in expected['imageDigests'].items() if name.startswith(('app-', 'web-'))}
    report_path = owned_path(proof.get('proofPath', ''), root)
    report, ref = reference({'path': str(report_path), 'sha256': proof.get('proofSha256')}, root)
    dependencies.append(ref)
    if report.get('status') != 'PASSED' or report.get('failed', 0) != 0 or report.get('failures', 0) != 0 or report.get('errors', 0) != 0:
        raise RuntimeError('The recreation or recovery exercise did not pass')
    accepted_build = build.get('sourceCommit') == expected['sourceCommit'] and build.get('imageDigests') == apps
    # Raw recovery may intentionally execute the backed-up source, rather than an
    # upgraded application. Bind it to its actual raw execution provenance, never
    # rewrite the old build as the final release. The disposal authorization itself
    # still belongs to the final accepted source via proof.sourceCommit above.
    raw = report.get('rawRestoreProof', {})
    raw_apps = {name: image for name, image in raw.get('imageDigests', {}).items()
                if name.startswith(('app-', 'web-'))}
    raw_build = (proof.get('kind') == 'recovery-clone' and bool(raw.get('sourceCommit'))
                 and set(raw_apps) == set(apps)
                 and build.get('sourceCommit') == raw['sourceCommit']
                 and build.get('imageDigests') == raw_apps)
    if not accepted_build and not raw_build:
        raise RuntimeError('Test application bindings match neither the accepted release nor the actual raw recovery')
    if proof.get('kind') == 'fresh-synthetic':
        if 'clone' not in state or state['clone'] is not None or proof.get('sourceBackupManifestSha', 'missing') is not None:
            raise RuntimeError('Fresh synthetic proof cannot inherit an unverified database clone')
        if not report_path.is_relative_to(local):
            raise RuntimeError('Synthetic execution proof must belong to its own isolated home')
        primary_accounts = read_json(Path(primary_home) / '.local' / 'accounts.json')
        own_ids = {str(value.get('id')) for value in accounts.values() if isinstance(value, dict)}
        primary_ids = {str(value.get('id')) for value in primary_accounts.values() if isinstance(value, dict)}
        if not own_ids or 'None' in own_ids or own_ids & primary_ids:
            raise RuntimeError('Synthetic test accounts must be independent of the primary deployment')
    elif proof.get('kind') == 'recovery-clone':
        if proof.get('syntheticWritesOnly') is not True:
            raise RuntimeError('Recovery clone writes have not been audited as synthetic-only')
        if report_path != local / 'recovery-result.json':
            raise RuntimeError('Recovery proof must be the original clone recovery report')
        backup = owned_path(proof.get('sourceBackup', ''), root)
        if not state.get('clone') or Path(state['clone']).resolve() != backup or Path(report.get('backup', '')).resolve() != backup:
            raise RuntimeError('Recovery initialization and proof disagree on the source backup')
        backup_manifest = backup / 'manifest.json'
        if not HEX.fullmatch(str(proof.get('sourceBackupManifestSha', ''))) or sha(backup_manifest) != proof['sourceBackupManifestSha']:
            raise RuntimeError('Source recovery manifest checksum changed')
        saved = bundle_verify(backup)
        if saved.get('quiesced') is not True:
            raise RuntimeError('A verified complete source backup is required')
        dependencies.append({'path': str(backup_manifest), 'sha256': sha(backup_manifest)})
        if raw.get('status') != 'PASSED' or any(raw.get(key) is not True for key in
                ('tableCountsMatched', 'tableContentChecksumsMatched', 'redisValuesAndExpiryMatched', 'brokerFileContentsMatched')):
            raise RuntimeError('Raw data, Redis and broker recovery must actually pass')
        if raw.get('mediaFileCount') != len(saved.get('mediaFiles', {})):
            raise RuntimeError('Recovered media inventory differs from the source backup')
        history, ref = reference(proof.get('historyProof'), root); dependencies.append(ref)
        if ref != context['gate']['inputs']['history']:
            raise RuntimeError('History proof must be the final deployment gate input')
        if (history.get('status') != 'PASSED' or history.get('sourceCommit') != expected['sourceCommit']
                or history.get('originalIdentifiersPreserved') is not True or history.get('mediaHashesPreserved') is not True):
            raise RuntimeError('Original records and media have not been preserved in the primary deployment')
    else:
        raise RuntimeError('Unknown test-data provenance; volume retained')
    return {'project': project, 'home': str(home), 'volumeNames': volume_names,
            'volumeKeys': sorted(compose['volumes']), 'dependencies': dependencies}


def make_plan(projects, names, proof_paths, context, root, primary_home, docker, bundle_verify=verify_bundle):
    projects = sorted(set(projects)); names = sorted(set(names))
    if not projects or any(project == PRIMARY_PROJECT or not allowed_project(project) for project in projects):
        raise RuntimeError('Only explicitly named test projects are allowed; primary and other projects are protected')
    if not names or any(not NAME.fullmatch(name) for name in names):
        raise RuntimeError('Explicit valid Docker volume names are required')
    proofs = {}; invalid = []; seen_projects = set()
    for path in proof_paths:
        try:
            declared_project = read_json(owned_path(path, root)).get('project')
            if isinstance(declared_project, str):
                if declared_project in seen_projects:
                    proofs.pop(declared_project, None)
                    raise RuntimeError('Duplicate project proof; retain that project')
                seen_projects.add(declared_project)
            proof = validate_proof(path, context, root, primary_home, bundle_verify)
            proofs[proof['project']] = proof
        except (OSError, ValueError, TypeError, KeyError, RuntimeError) as error:
            invalid.append({'proofPath': str(Path(path).resolve()), 'reason': str(error)})
    used = references(docker.containers())
    removable = []; preserved = []
    for name in names:
        item = docker.volume(name)
        reason = None
        project = ((item or {}).get('Labels') or {}).get('com.docker.compose.project')
        proof = proofs.get(project)
        if item is None: reason = 'Volume not present; no action'
        elif project == PRIMARY_PROJECT: reason = 'Current deployment volume is protected'
        elif project not in projects: reason = 'Foreign, anonymous or unknown ownership'
        elif name in used: reason = 'Referenced by a running or stopped container'
        elif proof is None: reason = 'No verified recreatable-data proof for this project'
        elif name not in proof['volumeNames']: reason = 'Not explicitly included in the data provenance manifest'
        elif (item.get('Labels') or {}).get('com.docker.compose.volume') not in proof['volumeKeys']: reason = 'Compose volume identity is not in the proved project configuration'
        elif name != project + '_' + item['Labels']['com.docker.compose.volume']: reason = 'Unexpected or shared volume name'
        elif item.get('Driver') != 'local' or item.get('Options'): reason = 'External or driver-specific storage needs manual review'
        if reason: preserved.append({'name': name, 'reason': reason})
        else: removable.append({'name': name, 'project': project, 'snapshot': snapshot(item)})
    return {'schemaVersion': 1, 'status': 'PLANNED', 'root': str(Path(root).resolve()),
            'primaryHome': str(Path(primary_home).resolve()), 'projects': projects, 'requestedVolumes': names,
            'proofPaths': [str(Path(path).resolve()) for path in proof_paths], 'gateFiles': context['files'],
            'proofs': proofs, 'removable': removable, 'preserved': preserved, 'invalidProofs': invalid}


def apply_plan(plan, context, root, primary_home, docker, bundle_verify=verify_bundle):
    if plan.get('schemaVersion') != 1 or plan.get('status') != 'PLANNED' or plan.get('root') != str(Path(root).resolve()) or plan.get('primaryHome') != str(Path(primary_home).resolve()):
        raise RuntimeError('The plan does not belong to this workspace and primary deployment')
    fresh = make_plan(plan['projects'], plan['requestedVolumes'], plan['proofPaths'], context, root, primary_home, docker, bundle_verify)
    for key in ('gateFiles', 'proofs', 'removable', 'preserved', 'invalidProofs'):
        if fresh[key] != plan[key]:
            raise RuntimeError('Retirement inputs, ownership or references changed; generate and review a new plan')
    removed = []
    for selected in plan['removable']:
        # Recheck every dependency and every container, including stopped/foreign ones,
        # immediately before each individual removal. Docker itself also rejects in-use volumes.
        for ref in [*plan['gateFiles'], *plan['proofs'][selected['project']]['dependencies']]:
            hashed_file(ref, root)
        if selected['name'] in references(docker.containers()):
            raise RuntimeError('Volume gained a container reference; further removal stopped')
        current = docker.volume(selected['name'])
        if current is None or snapshot(current) != selected['snapshot']:
            raise RuntimeError('Volume ownership changed; further removal stopped')
        docker.remove(selected['name']); removed.append(selected['name'])
    return {'status': 'PASSED', 'removedVolumes': removed, 'preserved': plan['preserved'],
            'currentDeploymentVolumesRemoved': False, 'unprovenVolumesRemoved': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home', type=Path, default=ROOT / 'deploy' / 'compact')
    parser.add_argument('--projects', nargs='+')
    parser.add_argument('--volumes', nargs='+')
    parser.add_argument('--proofs', nargs='+', type=Path)
    parser.add_argument('--out', type=Path)
    parser.add_argument('--apply', type=Path)
    args = parser.parse_args()
    home = owned_path(args.home, ROOT); local = home / '.local'; docker = Docker()
    if args.apply:
        if any((args.projects, args.volumes, args.proofs, args.out)):
            parser.error('--apply consumes an existing plan; do not supply replacement selections')
        plan_path = owned_path(args.apply, ROOT)
        from maintenance import maintenance_lock
        with maintenance_lock(local):
            context = check_gate(home, ROOT, docker)
            result = apply_plan(read_json(plan_path), context, ROOT, home, docker)
            destination = plan_path.with_name(plan_path.stem + '-result.json')
            destination.write_text(json.dumps(result, indent=2), encoding='utf8')
        print(json.dumps(result)); return 0
    if not args.projects or not args.volumes or not args.proofs:
        parser.error('Planning requires explicit --projects, --volumes and --proofs')
    context = check_gate(home, ROOT, docker)
    plan = make_plan(args.projects, args.volumes, args.proofs, context, ROOT, home, docker)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    out = owned_path(args.out or local / 'retirement' / ('volumes-' + stamp + '.json'), ROOT)
    out.parent.mkdir(parents=True, exist_ok=True)
    # Never overwrite the reviewed plan, including a prior failed execution.
    with out.open('x', encoding='utf8') as target:
        json.dump(plan, target, indent=2)
    print(json.dumps({'plan': str(out), 'eligible': len(plan['removable']),
                      'preserved': plan['preserved'], 'invalidProofs': plan['invalidProofs']}))
    return 0


if __name__ == '__main__': raise SystemExit(main())
