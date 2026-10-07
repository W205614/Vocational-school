"""Fail closed on cutover readiness; diagnostic existence alone never passes a gate."""
import argparse,json
from setup import LOCAL
parser=argparse.ArgumentParser();parser.add_argument('--historical',action='store_true');args=parser.parse_args()
checks={}
for name,status in [('security-smoke','PASSED'),('browser-result','PASSED'),('backend-result','PASSED'),('audit-smoke','PASSED'),('redis-recovery-proof','PASSED'),('recovery-acceptance','PASSED')]:
 path=LOCAL/(name+'.json');checks[name]=path.exists() and json.loads(path.read_text(encoding='utf8')).get('status')==status
performance=LOCAL/'performance-acceptance.json';checks['formal-performance']=False
if performance.exists():
 p=json.loads(performance.read_text(encoding='utf8'));checks['formal-performance']=p.get('status')=='PASSED' and p.get('sameMachineDataImages') is True and p.get('completedRunsPerConfiguration')==12 and p.get('javaMemoryReduction',0)>=.2
if args.historical:
 parity=LOCAL/'historical-parity.json';checks['historical-data-parity']=parity.exists() and json.loads(parity.read_text(encoding='utf8')).get('status')=='PASSED'
print(json.dumps(checks,indent=2));raise SystemExit(0 if all(checks.values()) else 1)
