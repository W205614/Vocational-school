"""Prepare-only opt-in read-only UI evidence after the live recovery reward purchase."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import stat
import subprocess
import sys
from datetime import datetime, timezone
import yaml

def repository_root():
    for candidate in Path(__file__).resolve().parents:
        if (candidate / 'pom.xml').is_file() and (candidate / 'frontend/package.json').is_file() and (candidate / 'deploy/compact/setup.py').is_file():
            return candidate
    raise RuntimeError('Repository root markers were not found; no runtime exercised')


ROOT = repository_root()
SOURCE = ROOT / 'deploy/compact'
TASK = SOURCE / '.local/learning-upgrade-20261015'
EXPECTED = '8dcf8e7d44c6048318ad1e306e1425c8fd40fb70'
SEVEN = {'app-identity', 'app-commerce', 'app-education', 'app-support', 'app-gateway', 'web-student', 'web-admin'}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def read(file):
    return json.loads(file.read_text(encoding='utf-8-sig'))


def digest(file):
    return hashlib.sha256(file.read_bytes()).hexdigest()


def capture(args):
    return subprocess.check_output(args, stderr=subprocess.PIPE, text=True).strip()


def no_links(file):
    for item in [file, *file.parents]:
        metadata = item.lstat()
        require(not item.is_symlink() and not (getattr(metadata, 'st_file_attributes', 0) & stat.FILE_ATTRIBUTE_REPARSE_POINT), 'Linked runtime paths are forbidden')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--project', required=True)
    parser.add_argument('--offset', type=int, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--settlement-proof', type=Path)
    args = parser.parse_args()
    out = args.out.resolve()
    require(out.is_relative_to(ROOT / '.local') or out.is_relative_to(TASK), 'Output must stay in ignored task evidence')
    require(not out.exists(), 'Preserve previous evidence; choose a fresh output directory')
    out.mkdir(parents=True)
    report = dict(status='RUNNING', sourceCommit=EXPECTED, phase='preflight', startedAt=datetime.now(timezone.utc).isoformat(), businessRecordsWritten=False,
                  scope='Read-only actual student UI and HTTP after an independently verified reward purchase; private screenshots only')
    try:
        raw_home = args.home.absolute()
        no_links(raw_home)
        home = raw_home.resolve()
        require(args.offset == 1000 and re.fullmatch(r'tianji-recovery-[a-z0-9-]+', args.project), 'Explicit independent recovery offset1000 required')
        require(home.parent in {ROOT / '.local', SOURCE / '.local'} and re.fullmatch(r'final-upgrade-[a-z0-9-]+', home.name), 'Fresh final-upgrade recovery home required')
        require(args.project == 'tianji-recovery-' + home.name, 'Home and project must share exact independent ownership')
        require(home != SOURCE.resolve(), 'Primary deployment is forbidden')
        proof_path = (args.settlement_proof or TASK / 'live-settlement-result.json').absolute()
        no_links(proof_path)
        require(proof_path.is_file(), 'Existing plain settlement proof file required')
        proof_path = proof_path.resolve()
        require(proof_path.is_relative_to(TASK.resolve()), 'Settlement proof must remain within the ignored TASK directory')
        proof = read(proof_path)
        require(proof.get('status') == 'PASSED' and proof.get('sourceCommit') == EXPECTED and proof.get('executedSource') == EXPECTED, 'Successful exact-source live settlement proof required')
        require(Path(proof.get('home', '')).resolve() == home and proof.get('project') == args.project and proof.get('offset') == 1000, 'Settlement proof belongs to another runtime')
        purchase = proof.get('rewardPurchase', {})
        require(proof.get('actualRewardPurchased') is True and purchase.get('actualRewardPurchased') is True and purchase.get('actualRewardUsed') is True, 'Actual earned-ticket purchase and use must already pass')
        require(purchase.get('paymentMode') == 'LOCAL_SIMULATOR', 'Only the recorded local simulator purchase is in scope')
        for field in ['orderId', 'orderDetailId', 'courseId', 'userCouponId', 'couponId']:
            require(re.fullmatch(r'[1-9][0-9]{0,19}', str(purchase.get(field, ''))), 'Purchase correlation identifier missing')
        require(str(proof.get('userCouponId')) == str(purchase['userCouponId']) and str(proof.get('couponId')) == str(purchase['couponId']), 'Settlement and purchase ticket correlation differs')
        require(proof.get('ticketCount') == 1 and proof.get('stockDebitedOnce') is True and proof.get('brokerAckObserved') is True, 'Single actual rewarded ticket and broker receipt proof required')
        require(isinstance(proof.get('requestId'), str) and re.fullmatch(r'[0-9]{6}', str(proof.get('month', ''))), 'Reward request and month required')
        require(int(purchase['discountAmount']) > 0 and int(purchase['paidAmount']) > 0 and int(purchase['totalAmount']) - int(purchase['discountAmount']) == int(purchase['paidAmount']), 'Recorded amounts are inconsistent')
        interaction = read(TASK / 'live-interaction-result.json')
        require(interaction.get('status') == 'PASSED' and interaction.get('sourceCommit') == EXPECTED and Path(interaction['home']).resolve() == home and interaction['project'] == args.project, 'Matching successful interaction proof must precede the UI check')
        local = home / '.local'
        manifest = read(local / 'build-source.json')
        require(manifest.get('sourceCommit') == EXPECTED and set(manifest.get('imageDigests', {})) == SEVEN, 'Exact seven-image source manifest required')
        recovery = read(local / 'recovery-result.json')
        require(recovery.get('status') == 'PASSED' and recovery.get('rawRestoreProof', {}).get('status') == 'PASSED' and recovery.get('migrationUpgradeProof', {}).get('sourceCommit') == EXPECTED, 'Verified raw recovery and final-source migration required')
        browser = read(local / 'browser-result.json')
        require(all(browser.get(key) == value for key, value in dict(status='PASSED', passed=42, failed=0, skipped=30).items()), 'Prior complete recovery browser proof requires 42/0/30')
        compose = yaml.safe_load((home / 'compose.yaml').read_text(encoding='utf8'))
        require(compose.get('name') == args.project, 'Compose ownership differs')
        expected_ports = {'mysql': '127.0.0.1:25316:3306', 'web-student': '127.0.0.1:25500:8080', 'web-admin': '127.0.0.1:25501:8080', 'app-gateway': '127.0.0.1:25310:25310'}
        # Gateway's container port is configuration-dependent; verify its actual published host binding below.
        for service in ['mysql', 'web-student', 'web-admin']:
            require(expected_ports[service] in compose['services'][service]['ports'], 'Recovery loopback offset port differs')
        command = ['docker', 'compose', '-p', args.project, '-f', str(home / 'compose.yaml'), '--env-file', str(home / '.env')]
        observed = {}
        for service, definition in compose['services'].items():
            container = capture(command + ['ps', '-q', service])
            require(container and '\n' not in container, 'One already running container per service required')
            info = json.loads(capture(['docker', 'inspect', container]))[0]
            labels = info['Config'].get('Labels', {})
            require(labels.get('com.docker.compose.project') == args.project and labels.get('com.docker.compose.service') == service and Path(labels.get('com.docker.compose.project.working_dir', '')).resolve() == home, 'Live service ownership differs')
            require(info['State'].get('Health', {}).get('Status') == 'healthy', 'Recovery services must remain healthy')
            require(all(network.startswith(args.project + '_') for network in info['NetworkSettings']['Networks']), 'Shared recovery network is forbidden')
            for mount in info['Mounts']:
                require(mount['Type'] == 'volume' and mount['Name'].startswith(args.project + '_') or mount['Type'] == 'bind' and Path(mount['Source']).resolve().is_relative_to(home), 'Foreign recovery mount is forbidden')
            if service in SEVEN:
                require(info['Image'] == manifest['imageDigests'][service] == definition['image'], 'Actual image differs from frozen source manifest')
                observed[service] = info['Image']
            if service in {'web-student', 'app-gateway'}:
                bindings = [binding for values in info['NetworkSettings']['Ports'].values() if values for binding in values]
                wanted = '25500' if service == 'web-student' else '25310'
                require(any(binding['HostIp'] == '127.0.0.1' and binding['HostPort'] == wanted for binding in bindings), 'Browser/API host port differs')
        require(set(observed) == SEVEN, 'All seven live final-source images required')
        require(read(local / 'accounts.json').get('student') and read(local / 'browser-fixture.json').get('course') and (local / 'motivation-fixture.json').exists(), 'Private recovery accounts and completed fixture required')
        config = dict(sourceCommit=EXPECTED, home=str(home), project=args.project, offset=1000, out=str(out), studentUrl='http://127.0.0.1:25500', proofPath=str(proof_path), proofSha256=digest(proof_path), imageDigests=observed)
        config_path = out / 'runtime-verified.json'
        config_path.write_text(json.dumps(config, indent=2), encoding='utf8')
        report.update(phase='browser', runtimePreflightPassed=True, imageCount=len(observed))
        runner = Path(__file__).with_name('live_reward_ui.mjs')
        with (out / 'private-browser.log').open('wb') as log:
            completed = subprocess.run([shutil.which('node') or 'node', str(runner), '--config', str(config_path)], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=180)
        ui = read(out / 'report.json') if (out / 'report.json').exists() else {}
        require(completed.returncode == 0 and ui.get('status') == 'PASSED' and ui.get('sourceCommit') == EXPECTED, 'Read-only browser contract failed; private evidence retained')
        report.update(status='PASSED', uiChecks=len(ui['uiChecks']), httpChecks=len(ui['httpChecks']), limitations=ui['limitations'])
    except Exception as error:
        report.update(status='FAILED', failureType=type(error).__name__)
        (out / 'private-preflight-error.txt').write_text(str(error), encoding='utf8')
    finally:
        report['completedAt'] = datetime.now(timezone.utc).isoformat()
        (out / 'aggregate.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
    print('Recovery earned-reward UI ' + report['status'] + '; independent private evidence retained. No business commands submitted.')
    return 0 if report['status'] == 'PASSED' else 1


if __name__ == '__main__':
    sys.exit(main())
