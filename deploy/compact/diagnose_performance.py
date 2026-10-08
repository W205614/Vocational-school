"""Compare bounded tuning candidates on an owned clone; these are not formal acceptance runs."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import yaml
from setup import BASE,LOCAL,ROOT,COMPOSE,prepare,mysql
from tuning_profile import profile
from evidence import utcnow


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--snapshot',required=True,type=Path)
    parser.add_argument('--candidates',nargs='+',default=['4:1:750','4:2:250'])
    args=parser.parse_args()
    import benchmark_snapshot
    benchmark_snapshot.guard()
    runtime=json.loads((LOCAL/'benchmark-runtime.json').read_text(encoding='utf8'))
    if runtime['mode']!='compact':raise RuntimeError('Tune on the explicitly owned compact diagnostic clone')
    original=yaml.safe_load((BASE/'compose.yaml').read_text(encoding='utf8'))
    source=json.loads((LOCAL/'build-source.json').read_text(encoding='utf8'))['sourceCommit']
    plans=[]
    for candidate in args.candidates:
        pool,workers,interval=candidate.split(':')
        values=dict(TJ_PERF_POOL_SIZE=pool,TJ_PERF_OPERATION_THREADS=workers,TJ_PERF_INTERVAL_MS=interval)
        # setup.prepare applies the real module count and enforces its total budget again.
        profile(12,values);plans.append((candidate,values))
    directory=LOCAL/'performance-diagnostics'/str(time.time_ns());directory.mkdir(parents=True)
    summary={'status':'DIAGNOSTIC','sourceCommit':source,'startedAt':utcnow(),'formalAcceptance':False,'candidates':[]}
    try:
        for candidate,values in plans:
            os.environ.update(values);prepare()
            # Only module tuning changes: preserve frozen images, ports, resource
            # limits and the existing owned volumes, including a migrated index.
            (BASE/'compose.yaml').write_text(yaml.safe_dump(original,sort_keys=False),encoding='utf8')
            benchmark_snapshot.restore(args.snapshot)
            mysql('TRUNCATE TABLE performance_schema.events_statements_summary_by_digest')
            before_locks={name:int(value) for name,value in (line.split('\t') for line in mysql("SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_%'").splitlines())}
            label='diagnostic-'+candidate.replace(':','-')
            result=subprocess.run([sys.executable,str(ROOT/'deploy/compact/mixed_load.py'),'--protocol','smoke',
                                   '--snapshot',str(args.snapshot),'--source-commit',source,'--label',label,
                                   '--users','200','--seconds','120','--warm-seconds','60','--skip-reset'])
            pointer=json.loads((LOCAL/('performance-'+label+'.json')).read_text(encoding='utf8'))
            report=json.loads(Path(pointer['path']).read_text(encoding='utf8'))
            sql=mysql("SELECT SCHEMA_NAME,DIGEST_TEXT,COUNT_STAR,ROUND(AVG_TIMER_WAIT/1000000000,3),ROUND(SUM_LOCK_TIME/1000000000,3),SUM_ROWS_EXAMINED FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME LIKE 'tj\\_%' OR (SCHEMA_NAME IS NULL AND DIGEST_TEXT LIKE '%tj\\_%') ORDER BY SUM_TIMER_WAIT DESC LIMIT 20")
            after_locks={name:int(value) for name,value in (line.split('\t') for line in mysql("SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_%'").splitlines())}
            actual=yaml.safe_load((LOCAL/'configs/learning.yml').read_text(encoding='utf8'))
            item={'candidate':candidate,'parameters':values,'schedulerThreads':actual['spring']['task']['scheduling']['pool']['size'],
                  'outboxThreads':actual['tj']['reliability']['outbox-core'],'progressIntervalMs':actual['tj']['learning']['progress-interval-ms'],
                  'processExit':result.returncode,'rawReport':pointer['path'],
                  'sqlTimingScope':'Login, one-minute query warm-up and two-minute mixed workload; picoseconds converted to milliseconds.',
                  'slowSqlSummary':sql.splitlines(),'currentLockWaits':int(mysql('SELECT COUNT(*) FROM performance_schema.data_lock_waits')),
                  'rowLockWaitsDuringDiagnostic':after_locks['Innodb_row_lock_waits']-before_locks['Innodb_row_lock_waits'],
                  'rowLockWaitMillisecondsDuringDiagnostic':after_locks['Innodb_row_lock_time']-before_locks['Innodb_row_lock_time'],
                  'rowLockLifetimeMaxMilliseconds':after_locks['Innodb_row_lock_time_max'],
                  'rawStatus':report['status']}
            if report['runs']:
                run=report['runs'][0]
                item['businessInvariants']=run['invariants']
                item['latencyTargetFailures']={name:values['p95'] for name,values in run['latencyMs'].items() if name.endswith(':ok') and values['p95']>(500 if name.startswith('query/') else 3000)}
                item['warmupUnexpected']={name:count for name,count in run['warmup']['counts'].items() if count and not name.endswith(':ok')}
            summary['candidates'].append(item)
            (directory/'report.json').write_text(json.dumps(summary,indent=2),encoding='utf8')
            print('Diagnostic candidate completed: '+candidate+'; failed samples retained',flush=True)
    finally:
        summary['finishedAt']=utcnow()
        (directory/'report.json').write_text(json.dumps(summary,indent=2),encoding='utf8')
        # The final candidate remains explicitly visible for inspection. Formal homes
        # are generated afresh after choosing and committing the final defaults.
        print('Independent diagnostic evidence: '+str(directory),flush=True)


if __name__=='__main__':main()
