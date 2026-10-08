"""Fail closed on cutover readiness; diagnostic existence alone never passes a gate."""
import argparse,json
from setup import LOCAL,ROOT,COMPOSE
from evidence import validate,digest,manifest,verify_publication
from pathlib import Path
from performance_acceptance import compare,validate_raw,baseline_complete,pair_binding_errors
parser=argparse.ArgumentParser();parser.add_argument('--historical',action='store_true');parser.add_argument('--benchmark-home',type=Path);args=parser.parse_args()
checks={}
manifest_path=LOCAL/'release-run.json'
expected=json.loads(manifest_path.read_text(encoding='utf8')) if manifest_path.exists() else None
checks['bound-release-manifest']=expected is not None
checks['live-build-config-snapshot']=False
checks['publication-source-equivalence']=False
if expected:
 try:
  live=manifest(LOCAL,COMPOSE,ROOT,expected['snapshotPath'])
  checks['live-build-config-snapshot']=all(live.get(key)==expected.get(key) for key in ('sourceCommit','imageDigests','configFingerprint','baseSnapshotFingerprint'))
  checks['publication-source-equivalence']=verify_publication(ROOT,expected['sourceCommit'])
 except (ValueError,KeyError,TypeError,OSError):pass
directory=LOCAL/'evidence'/expected['releaseRunId'] if expected else LOCAL/'missing-evidence'
for name,status in [('security-smoke','PASSED'),('browser-result','PASSED'),('backend-result','PASSED'),('audit-smoke','PASSED'),('redis-recovery-proof','PASSED'),('recovery-acceptance','PASSED')]:
 path=directory/(name+'.json')
 try: checks[name]=expected is not None and path.exists() and validate(json.loads(path.read_text(encoding='utf8')),expected)
 except (ValueError,KeyError,TypeError):checks[name]=False
performance=directory/'performance-acceptance.json';checks['formal-performance']=False
performance_expected=expected
checks['performance-deployment-equivalence']=not args.benchmark_home
if args.benchmark_home and expected:
 try:
  from config_equivalence import config_fingerprint,equivalent
  benchmark=args.benchmark_home.resolve()
  if (ROOT/'deploy/compact/.local/optimization').resolve() not in benchmark.parents:raise ValueError('Benchmark is outside owned optimization evidence')
  performance_expected=json.loads((benchmark/'.local/release-run.json').read_text(encoding='utf8'))
  performance=benchmark/'.local/evidence'/performance_expected['releaseRunId']/'performance-acceptance.json'
  checks['performance-deployment-equivalence']=performance_expected['releaseRunId']==expected['releaseRunId'] and performance_expected['sourceCommit']==expected['sourceCommit'] and performance_expected['imageDigests']==expected['imageDigests'] and config_fingerprint(benchmark)==performance_expected['configFingerprint'] and equivalent(benchmark,LOCAL.parent)
 except (ValueError,KeyError,TypeError,OSError):checks['performance-deployment-equivalence']=False
if performance.exists():
 try:
  p=json.loads(performance.read_text(encoding='utf8'))
  checks['formal-performance']=validate(p,performance_expected) and p.get('protocol')=='perf-3h-v1' and p.get('completedRunsPerConfiguration')==6 and p.get('javaMemoryReduction',0)>=.2 and not p.get('failures')
  raw={name:json.loads(Path(path).read_text(encoding='utf8')) for name,path in p.get('rawReports',{}).items()}
  required={'standalone','compact','baseline_standalone','baseline_compact'}
  checks['formal-performance']=checks['formal-performance'] and set(raw)==required
  for name,report in raw.items():
   checks['formal-performance']=checks['formal-performance'] and digest(p['rawReports'][name])==p.get('rawReportHashes',{}).get(name) and not validate_raw(report,performance_expected)
  if set(raw)==required:checks['formal-performance']=checks['formal-performance'] and compare(raw['standalone'],raw['compact'])['status']=='PASSED' and all(baseline_complete(raw[name]) for name in required)
  if set(raw)==required:
   checks['formal-performance']=checks['formal-performance'] and not pair_binding_errors(raw['baseline_standalone'],raw['baseline_compact'])
   checks['formal-performance']=checks['formal-performance'] and all(raw[name].get('evidence',{}).get('sourceCommit')==performance_expected.get('baselineSourceCommit') for name in ('baseline_standalone','baseline_compact'))
   checks['formal-performance']=checks['formal-performance'] and all(report.get('evidence',{}).get('baseSnapshotFingerprint')==performance_expected.get('baseSnapshotFingerprint') for report in raw.values())
   checks['formal-performance']=checks['formal-performance'] and all(all(report.get(key)==raw['compact'].get(key) for key in ('protocol','fixtureFingerprint','workload','workloadFingerprint')) for report in raw.values())
 except (ValueError,KeyError,TypeError,OSError):checks['formal-performance']=False
if args.historical:
 parity=directory/'historical-parity.json';checks['historical-data-parity']=expected is not None and parity.exists() and validate(json.loads(parity.read_text(encoding='utf8')),expected)
from evidence import save_report
if expected:save_report(LOCAL,'release-gate',{'status':'PASSED' if all(checks.values()) else 'FAILED','checks':checks,'benchmarkHome':str(args.benchmark_home) if args.benchmark_home else None,'publicationCommit':__import__('subprocess').check_output(['git','-C',str(ROOT),'rev-parse','HEAD'],text=True).strip()})
print(json.dumps(checks,indent=2));raise SystemExit(0 if all(checks.values()) else 1)
