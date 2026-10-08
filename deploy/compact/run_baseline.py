"""Measure frozen pre-fix configurations serially, even when baseline targets fail."""
import json
import os
from pathlib import Path
import subprocess
import sys
from setup import ROOT
from performance_acceptance import baseline_complete

def main():
    base=ROOT/'deploy/compact/.local/optimization';source=base/'pre-compact';target=base/'pre-standalone'
    baseline=json.loads((base/'baseline/manifest.json').read_text(encoding='utf8'))
    commit=baseline['sourceCommit']
    script=ROOT/'deploy/compact'
    reports={}
    for mode,home,offset in [('compact',source,3000),('standalone',target,5000)]:
        environment={**os.environ,'TJ_COMPACT_HOME':str(home),'TJ_COMPACT_PROJECT':'tianji-opt-pre-'+mode,'TJ_COMPACT_PORT_OFFSET':str(offset)}
        def run(name,*arguments):
            result=subprocess.run([sys.executable,str(script/name),*arguments],env=environment)
            if result.returncode:raise RuntimeError('Baseline initialization failed: '+name)
        if mode=='standalone':
            if not (home/'.local/init-state.json').exists():run('benchmark_clone.py','--from-home',str(source))
            run('benchmark_deployment.py','standalone','--tag',commit)
        subprocess.run(['docker','compose','-p','tianji-opt-pre-'+mode,'-f',str(home/'compose.yaml'),'--env-file',str(home/'.env'),'up','-d','--wait','mysql','redis','rabbitmq','elasticsearch'],check=True)
        result=subprocess.run([sys.executable,str(script/'mixed_load.py'),'--protocol','perf-3h-v1','--snapshot',str(base/'baseline/data-v2.sql'),'--source-commit',commit,'--label','pre-'+mode,'--batch-manifest',str(base/'audit-run.json')],env=environment)
        pointer=json.loads((home/('.local/performance-pre-'+mode+'.json')).read_text(encoding='utf8'))
        report=json.loads(Path(pointer['path']).read_text(encoding='utf8'))
        if not baseline_complete(report):
            raise RuntimeError('Baseline schedule incomplete or contaminated; failed evidence retained')
        reports[mode]={'path':pointer['path'],'processExit':result.returncode,'status':report['status']}
        subprocess.run(['docker','compose','-p','tianji-opt-pre-'+mode,'-f',str(home/'compose.yaml'),'--env-file',str(home/'.env'),'stop','-t','60'],check=True)
    (base/'baseline-completed.json').write_text(json.dumps(reports,indent=2),encoding='utf8')
    print('Both baseline schedules completed; failed performance targets remain in their raw reports',flush=True)

if __name__=='__main__':main()
