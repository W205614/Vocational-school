"""Export only allowlisted, credential-free gate reports. Private logs/backups stay local."""
from pathlib import Path
from datetime import datetime,timezone
import json,re,hashlib,argparse
from prepare import LOCAL,BASE
from start_service import MODULES
REPO=BASE.parents[1]
DEST=REPO/'docs/evidence/2026-10-06'
FILES=['api-smoke.json','coupon-concurrency.json','financial-smoke.json','auxiliary-smoke.json','projection-security.json','recovery-smoke.json','runtime-faults.json','observability.json','migration-audit.json','ci-schema-validation.json','load-claims-before.json','sustained-claims-before.json','load-claims-after.json','sustained-claims-after.json','sustained-claims-repeat.json','concurrency-edges.json','container-smoke.json','container-browser.json']
def read(name):return json.loads((LOCAL/name).read_text(encoding='utf8'))
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--build-log',default='final-all62.log');parser.add_argument('--browser-log',default='playwright-container.log');parser.add_argument('--expected-tests',type=int,default=49);args=parser.parse_args()
 for name in [args.build_log,args.browser_log]:
  if Path(name).name!=name:raise ValueError('Log must be a private .local basename')
 DEST.mkdir(parents=True,exist_ok=True)
 exported=[]
 for name in FILES:
  source=LOCAL/name
  if not source.exists():raise FileNotFoundError('Missing allowlisted report '+name)
  value=read(name)
  encoded=json.dumps(value,ensure_ascii=False,indent=2)
  # No environment, account, full config, raw SQL dump or cookie/token file is accepted.
  if re.search(r'(?i)"(?:password|accessToken|refreshToken|secretKey|Authorization|X-Internal-Token)"\s*:',encoded):raise ValueError('Sensitive field in '+name)
  (DEST/name).write_text(encoded+'\n',encoding='utf8');exported.append(name)
 log=(LOCAL/args.build_log).read_text(encoding='utf8',errors='replace')
 tests=[dict(tests=int(m[0]),failures=int(m[1]),errors=int(m[2]),skipped=int(m[3]),name=m[4]) for m in re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+).*? -- in ([\w.]+)',log)]
 assert 'BUILD SUCCESS' in log and sum(x['tests'] for x in tests)==args.expected_tests and not any(x['failures'] or x['errors'] or x['skipped'] for x in tests)
 browser=(LOCAL/args.browser_log).read_text(encoding='utf8',errors='replace')
 assert '7 passed' in browser and '7 skipped' in browser
 artifacts={name:json.loads((LOCAL/(name+'-pid.json')).read_text(encoding='utf8'))['artifactSha256'] for name in MODULES}
 for name in ['concurrency-edges.json','container-smoke.json','container-browser.json']:
  assert read(name)['status']=='PASSED',name
 containers=read('container-smoke.json')['services']
 assert set(containers)==set(MODULES)
 assert all(v['user']=='app' and v['exitCode'] in [0,143] and v['gracefulShutdownLogged'] and v['sentinelLogDirectoryHealthy'] and not v['oomKilled'] for v in containers.values())
 images=json.loads((LOCAL/'containers/images.json').read_text(encoding='utf8'))
 assert all(images[name]['jarSha256']==artifacts[name] for name in MODULES)
 evidence=dict(generatedAt=datetime.now(timezone.utc).isoformat(),baseline='c1b7df1',branch='codex/production-hardening',framework=dict(java='21.0.1',linuxContainerJava='Temurin 21.0.12.1+1-LTS',springBoot='4.0.8',springCloud='2025.1.3',springCloudAlibaba='2025.1.0.0',mybatisPlus='3.5.17'),backend=dict(moduleCount=27,testCount=args.expected_tests,tests=tests,buildLogSha256=hashlib.sha256((LOCAL/args.build_log).read_bytes()).hexdigest()),browser=dict(passed=7,expectedOtherProjectSkips=7,failed=0,logSha256=hashlib.sha256((LOCAL/args.browser_log).read_bytes()).hexdigest()),immutableRuntimeArtifacts=artifacts,linuxImages=json.loads((LOCAL/'containers/images.json').read_text(encoding='utf8')),reports=exported,environment=dict(os='Windows',cpu='AMD Ryzen 5 6600H',physicalCores=6,logicalProcessors=12,ramBytes=33492590592,jvmHeapPerServiceMiB=dict(initial=128,maximum=512),linuxContainerHeapMiB=dict(initial=96,maximum=384),linuxContainerMemoryLimitMiB=768,promotionDatabasePool=8,dockerEngine='29.8.0',node='24.19.0'),boundaries=['No original deployment switch or real vendor calls','CI workflow authored; remote run not performed','Sequential Linux image boot and Nginx browser path tested; no simultaneous full-container stack, multi-node HA or long-duration production saturation claim','Before is upgraded batch-20 checkpoint, not original commit; batch-100 comparison is observational, not a controlled causal result'])
 (DEST/'gates.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 print('Credential-free evidence exported to '+str(DEST))
if __name__=='__main__':main()
