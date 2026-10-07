"""Fail closed on cutover readiness; diagnostic existence alone never passes a gate."""
import argparse,json
from setup import LOCAL
from evidence import validate,digest
from pathlib import Path
from performance_acceptance import compare,validate_raw
parser=argparse.ArgumentParser();parser.add_argument('--historical',action='store_true');args=parser.parse_args()
checks={}
manifest_path=LOCAL/'release-run.json'
expected=json.loads(manifest_path.read_text(encoding='utf8')) if manifest_path.exists() else None
checks['bound-release-manifest']=expected is not None
directory=LOCAL/'evidence'/expected['releaseRunId'] if expected else LOCAL/'missing-evidence'
for name,status in [('security-smoke','PASSED'),('browser-result','PASSED'),('backend-result','PASSED'),('audit-smoke','PASSED'),('redis-recovery-proof','PASSED'),('recovery-acceptance','PASSED')]:
 path=directory/(name+'.json')
 try: checks[name]=expected is not None and path.exists() and validate(json.loads(path.read_text(encoding='utf8')),expected)
 except (ValueError,KeyError,TypeError):checks[name]=False
performance=directory/'performance-acceptance.json';checks['formal-performance']=False
if performance.exists():
 try:
  p=json.loads(performance.read_text(encoding='utf8'))
  checks['formal-performance']=validate(p,expected) and p.get('protocol')=='perf-3h-v1' and p.get('completedRunsPerConfiguration')==6 and p.get('javaMemoryReduction',0)>=.2 and not p.get('failures')
  raw={name:json.loads(Path(path).read_text(encoding='utf8')) for name,path in p.get('rawReports',{}).items()}
  required={'standalone','compact','baseline_standalone','baseline_compact'}
  checks['formal-performance']=checks['formal-performance'] and set(raw)==required
  for name,report in raw.items():
   checks['formal-performance']=checks['formal-performance'] and digest(p['rawReports'][name])==p.get('rawReportHashes',{}).get(name) and not validate_raw(report,expected)
  if set(raw)==required:checks['formal-performance']=checks['formal-performance'] and compare(raw['standalone'],raw['compact'])['status']=='PASSED' and all(len(raw[name].get('runs',[]))==6 for name in required)
 except (ValueError,KeyError,TypeError,OSError):checks['formal-performance']=False
if args.historical:
 parity=directory/'historical-parity.json';checks['historical-data-parity']=expected is not None and parity.exists() and validate(json.loads(parity.read_text(encoding='utf8')),expected)
print(json.dumps(checks,indent=2));raise SystemExit(0 if all(checks.values()) else 1)
