"""Measure frozen pre-fix configurations serially, even when baseline targets fail."""
import json
import os
from pathlib import Path
import subprocess
import sys
from setup import ROOT
from performance_acceptance import baseline_complete
from measurement_power import awake_during_measurement
from evidence import digest,tree_digest
from mixed_load import workload_fingerprint
from performance_acceptance import validate_raw
from benchmark_lifecycle import retire

def reusable_report(home,mode,commit,batch,snapshot):
    pointer=home/('.local/performance-pre-'+mode+'.json')
    if not pointer.exists():return None
    previous=json.loads(pointer.read_text(encoding='utf8'))
    path=Path(previous['path']).resolve()
    if not path.is_relative_to((home/'.local/performance').resolve()):raise RuntimeError('Baseline pointer escaped its evidence home')
    report=json.loads(path.read_text(encoding='utf8'))
    if not baseline_complete(report):return None
    if report.get('mode')!=mode or report.get('label')!='pre-'+mode:return None
    if validate_raw(report,batch):return None
    expected=report['evidence']
    if expected['sourceCommit']!=commit or expected['baseSnapshotFingerprint']!=digest(snapshot):return None
    if report['workloadFingerprint']!=workload_fingerprint() or report['fixtureFingerprint']!=digest(home/'.local/load-fixture.json'):return None
    configuration=__import__('hashlib').sha256((tree_digest(home/'.local/configs')+digest(home/'compose.yaml')).encode()).hexdigest()
    if expected['configFingerprint']!=configuration:return None
    build=json.loads((home/'.local/build-source.json').read_text(encoding='utf8'))
    if build['sourceCommit']!=commit or build['imageDigests']!={name:image for name,image in expected['imageDigests'].items() if name.startswith(('app-','web-'))}:return None
    return {'path':str(path),'processExit':0 if report['status']=='PASSED' else 1,'status':report['status']}

@awake_during_measurement
def main():
    base=ROOT/'deploy/compact/.local/optimization';source=base/'pre-compact';target=base/'pre-standalone'
    baseline=json.loads((base/'baseline/manifest.json').read_text(encoding='utf8'))
    batch=json.loads((base/'audit-run.json').read_text(encoding='utf8'))
    snapshot=base/'baseline/data-v2.sql'
    commit=baseline['sourceCommit']
    script=ROOT/'deploy/compact'
    reports={}
    for mode,home,offset in [('compact',source,3000),('standalone',target,5000)]:
        previous=reusable_report(home,mode,commit,batch,snapshot)
        if previous:
            reports[mode]=previous
            retire(home,'tianji-opt-pre-'+mode)
            print('Reused complete bound baseline schedule: '+mode,flush=True)
            continue
        environment={**os.environ,'TJ_COMPACT_HOME':str(home),'TJ_COMPACT_PROJECT':'tianji-opt-pre-'+mode,'TJ_COMPACT_PORT_OFFSET':str(offset)}
        def run(name,*arguments):
            result=subprocess.run([sys.executable,str(script/name),*arguments],env=environment)
            if result.returncode:raise RuntimeError('Baseline initialization failed: '+name)
        if mode=='standalone':
            if not (home/'.local/init-state.json').exists():run('benchmark_clone.py','--from-home',str(source))
            runtime_path=home/'.local/benchmark-runtime.json'
            if not runtime_path.exists() or json.loads(runtime_path.read_text(encoding='utf8')).get('mode')!='standalone':
                run('benchmark_deployment.py','standalone','--tag',commit)
        try:
            subprocess.run(['docker','compose','-p','tianji-opt-pre-'+mode,'-f',str(home/'compose.yaml'),'--env-file',str(home/'.env'),'up','-d','--wait','mysql','redis','rabbitmq','elasticsearch'],check=True)
            result=subprocess.run([sys.executable,str(script/'mixed_load.py'),'--protocol','perf-3h-v1','--snapshot',str(base/'baseline/data-v2.sql'),'--source-commit',commit,'--label','pre-'+mode,'--batch-manifest',str(base/'audit-run.json')],env=environment)
        finally:
            retire(home,'tianji-opt-pre-'+mode)
        pointer=json.loads((home/('.local/performance-pre-'+mode+'.json')).read_text(encoding='utf8'))
        report=json.loads(Path(pointer['path']).read_text(encoding='utf8'))
        if not baseline_complete(report):
            raise RuntimeError('Baseline schedule incomplete or contaminated; failed evidence retained')
        reports[mode]={'path':pointer['path'],'processExit':result.returncode,'status':report['status']}
    (base/'baseline-completed.json').write_text(json.dumps(reports,indent=2),encoding='utf8')
    print('Both baseline schedules completed; failed performance targets remain in their raw reports',flush=True)

if __name__=='__main__':main()
