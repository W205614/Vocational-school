import requests,json,time
from prepare import local_secrets,LOCAL
from start_service import MODULES
def main():
 token=local_secrets()['ACCEPTANCE_INTERNAL_TOKEN'];checks={}
 for name,(_,_,port,_) in MODULES.items():
  if name=='gateway':continue
  response=requests.get(f'http://127.0.0.1:{port}/actuator/prometheus',headers={'X-Internal-Token':token},timeout=15)
  assert response.ok,(name,response.status_code)
  assert 'jvm_memory_used_bytes' in response.text,name
  assert requests.get(f'http://127.0.0.1:{port}/actuator/prometheus',timeout=5).status_code in [401,403],name
  checks[name]={'processHealth':requests.get(f'http://127.0.0.1:{port}/actuator/health/liveness',timeout=5).status_code,'readiness':requests.get(f'http://127.0.0.1:{port}/actuator/health/readiness',timeout=5).status_code,'protectedMetrics':True}
 end=time.monotonic()+45
 while time.monotonic()<end:
  response=requests.get('http://127.0.0.1:23909/api/v1/targets',timeout=10);response.raise_for_status()
  targets=response.json()['data']['activeTargets']
  if len(targets)==len(checks) and all(t['health']=='up' for t in targets):break
  time.sleep(5)
 else:raise AssertionError('Prometheus scrape unavailable: '+','.join(t['labels']['job'] for t in targets if t['health']!='up'))
 assert requests.get('http://127.0.0.1:23930/api/health',timeout=10).ok
 (LOCAL/'observability.json').write_text(json.dumps({'status':'PASSED','services':checks,'scrapeTargets':len(targets),'grafana':True},indent=2))
 print('Protected Prometheus scraping and Grafana readiness PASSED',flush=True)
if __name__=='__main__':main()
