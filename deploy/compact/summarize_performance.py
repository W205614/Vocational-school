"""Export non-sensitive measurements; keep raw reports and failed outcomes intact."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re
from statistics import median


def bytes_used(value):
    amount,unit=re.fullmatch(r'([0-9.]+)(B|KiB|MiB|GiB|TiB)',value.strip()).groups()
    return float(amount)*1024**('B','KiB','MiB','GiB','TiB').index(unit)


def summarize(path):
    raw=json.loads(Path(path).read_text(encoding='utf8'))
    evidence=raw.get('evidence',{})
    value={key:raw.get(key) for key in ('protocol','label','mode','status','fixtureFingerprint','workloadFingerprint','workload','scope')}
    value['binding']={key:evidence.get(key) for key in ('releaseRunId','sourceCommit','imageDigests','configFingerprint','baseSnapshotFingerprint','startedAt','finishedAt')}
    value['runs']=[]
    for run in raw['runs']:
        samples=run.get('samples',[]);peaks={};resources={};gc={}
        for sample in samples:
            total_memory=total_cpu=0
            for container in sample['containers']:
                total_memory+=bytes_used(container['MemUsage'].split('/')[0])
                total_cpu+=float(container['CPUPerc'].rstrip('%'))
            resources.setdefault('environmentMemoryBytes',[]).append(total_memory)
            resources.setdefault('environmentCpuPercent',[]).append(total_cpu)
            for module,lines in sample['metrics'].items():
                for line in lines:
                    name,number=line.rsplit(' ',1);key=module+':'+name
                    if name.startswith(('hikaricp_connections_pending','executor_queued_tasks','tj_events_','tj_operations_pending')):
                        peaks[key]=max(peaks.get(key,0),float(number))
                    if name.startswith('jvm_gc_pause_seconds_sum'):
                        gc.setdefault(key,[]).append(float(number))
        measured=run['elapsedSeconds']
        item={key:run.get(key) for key in ('users','repeat','requestedSeconds','elapsedSeconds','sampleStartedAt','sampleFinishedAt','status','profiles','workflowCounts','counts','latencyMs','invariants','queuesDrained','contaminated','javaRssMedianBytes')}
        item['warmupUnexpected']={key:count for key,count in run.get('warmup',{}).get('counts',{}).items() if not key.endswith(':ok') and count}
        item['workflowThroughputPerSecond']={key:count/measured for key,count in run['workflowCounts'].items()}
        item['resourceSamples']=len(samples)
        item['telemetryErrorTypes']=dict(Counter(error['type'] for error in run.get('telemetryErrors',[])))
        item['environmentResourceMedian']={key:median(numbers) for key,numbers in resources.items()}
        item['metricPeaks']={key:peak for key,peak in peaks.items() if peak}
        item['gcPauseSecondsDuringSampling']={key:max(0,numbers[-1]-numbers[0]) for key,numbers in gc.items()}
        value['runs'].append(item)
    rss=[run['javaRssMedianBytes'] for run in raw['runs'] if run['users']==200]
    value['javaRssThreeRunMedianBytes']=median(rss) if len(rss)==3 and all(rss) else None
    value['resourceDefinitions']={'javaRss':'Application JVM RSS including gateway; last six minutes per 200-user round, then median of three rounds.',
                                  'environmentMemory':'Sum of Docker stats memory usage for all running containers; differs from JVM RSS.',
                                  'environmentCpu':'Docker CPU percent; 100 percent represents one fully occupied core.',
                                  'workflowThroughput':'Completed business workflows per sampling second; not HTTP request throughput.',
                                  'metricPeaks':'Observed telemetry maxima; sampling may miss brief waits. Scheduled task queue size includes future scheduled tasks.'}
    return value


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('reports',nargs='+',type=Path)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    value={Path(path).parent.name:summarize(path) for path in args.reports}
    text=json.dumps(value,ensure_ascii=False,indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(text,encoding='utf8')
        print('Sanitized measurements exported: '+str(args.output))
    else:print(text)


if __name__=='__main__':main()
