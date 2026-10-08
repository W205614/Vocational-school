"""Run both frozen post-fix topologies serially; derive acceptance from all four reports."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
from setup import ROOT
from evidence import digest
from mixed_load import workload_fingerprint
from performance_acceptance import baseline_complete
from perf_protocol import schedule


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--compact-home',required=True,type=Path)
    parser.add_argument('--standalone-home',required=True,type=Path)
    parser.add_argument('--compact-offset',required=True,type=int)
    parser.add_argument('--standalone-offset',required=True,type=int)
    args=parser.parse_args()
    base=ROOT/'deploy/compact/.local/optimization';script=ROOT/'deploy/compact'
    baseline=json.loads((base/'baseline-completed.json').read_text(encoding='utf8'))
    batch=json.loads((base/'audit-run.json').read_text(encoding='utf8'))
    snapshot=base/'baseline/data-v2.sql'
    for item in baseline.values():
        report=json.loads(Path(item['path']).read_text(encoding='utf8'))
        if not baseline_complete(report):raise RuntimeError('A complete uncontaminated baseline is required')
        if report.get('workloadFingerprint')!=workload_fingerprint() or report['evidence']['baseSnapshotFingerprint']!=digest(snapshot):
            raise RuntimeError('Workload or base snapshot changed since the baseline')
        if report['evidence']['releaseRunId']!=batch['releaseRunId']:raise RuntimeError('Baseline belongs to another audit batch')
    targets=[]
    for mode,home,offset in [('standalone',args.standalone_home,args.standalone_offset),('compact',args.compact_home,args.compact_offset)]:
        home=home.resolve()
        if not home.is_relative_to(base) or home==base:raise RuntimeError('Post-fix homes must be owned audit clones')
        runtime=json.loads((home/'.local/benchmark-runtime.json').read_text(encoding='utf8'))
        if runtime['mode']!=mode:raise RuntimeError('Benchmark topology mismatch')
        source=json.loads((home/'.local/build-source.json').read_text(encoding='utf8'))
        project='tianji-opt-post-'+mode
        compose=['docker','compose','-p',project,'-f',str(home/'compose.yaml'),'--env-file',str(home/'.env')]
        # Explicitly stop these two owned clones before any formal round.
        subprocess.run(compose+['stop','-t','60'],check=True)
        targets.append((mode,home,offset,project,compose,source['sourceCommit']))
    if len({item[-1] for item in targets})!=1:raise RuntimeError('Post-fix source commits differ')
    ids=subprocess.check_output(['docker','ps','-q'],text=True).splitlines()
    if ids:
        for container in json.loads(subprocess.check_output(['docker','inspect',*ids],text=True)):
            project=container['Config']['Labels'].get('com.docker.compose.project','')
            if project.startswith(('tianji-','vocational-acceptance')):
                raise RuntimeError('Another project-owned deployment is running during formal measurement: '+project)
    reports={}
    for mode,home,offset,project,compose,commit in targets:
        environment={**os.environ,'TJ_COMPACT_HOME':str(home),'TJ_COMPACT_PROJECT':project,'TJ_COMPACT_PORT_OFFSET':str(offset)}
        subprocess.run(compose+['up','-d','--wait','mysql','redis','rabbitmq','elasticsearch'],check=True)
        result=subprocess.run([sys.executable,str(script/'mixed_load.py'),'--protocol','perf-3h-v1','--snapshot',str(snapshot),
                               '--source-commit',commit,'--label','post-'+mode,'--batch-manifest',str(base/'audit-run.json')],env=environment)
        pointer=json.loads((home/('.local/performance-post-'+mode+'.json')).read_text(encoding='utf8'))
        report=json.loads(Path(pointer['path']).read_text(encoding='utf8'))
        if [(r['users'],r['requestedSeconds'],r['repeat']) for r in report['runs']]!=schedule('perf-3h-v1'):
            raise RuntimeError('Post-fix schedule incomplete; partial failed evidence retained')
        reports[mode]={'path':pointer['path'],'processExit':result.returncode,'status':report['status']}
        if mode=='standalone':subprocess.run(compose+['stop','-t','60'],check=True)
    (base/'post-completed.json').write_text(json.dumps(reports,indent=2),encoding='utf8')
    compact=targets[-1];environment={**os.environ,'TJ_COMPACT_HOME':str(compact[1]),'TJ_COMPACT_PROJECT':compact[3],'TJ_COMPACT_PORT_OFFSET':str(compact[2])}
    subprocess.run([sys.executable,str(script/'evidence.py'),'start','--snapshot',str(snapshot),'--audit-batch',str(base/'audit-run.json')],env=environment,check=True)
    result=subprocess.run([sys.executable,str(script/'performance_acceptance.py'),'--standalone',reports['standalone']['path'],
                           '--compact',reports['compact']['path'],'--baseline-standalone',baseline['standalone']['path'],
                           '--baseline-compact',baseline['compact']['path']],env=environment)
    return result.returncode


if __name__=='__main__':raise SystemExit(main())
