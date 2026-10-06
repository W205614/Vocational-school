"""Verify one candidate Linux application at a time, bounded to 768 MiB; original containers untouched."""
import requests,subprocess,json,time,argparse
from prepare import LOCAL,local_secrets
from start_service import MODULES
from container_apps import DIR
def docker(args):
 result=subprocess.run(['docker']+args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
 return (result.stdout+(result.stderr if args[0]=='logs' else b'')).decode(errors='replace').strip()
def inspect(identity):return json.loads(docker(['inspect',identity]))[0]
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--only');args=parser.parse_args();names=args.only.split(',') if args.only else list(MODULES)
 if any(n not in MODULES for n in names):raise ValueError('Unknown service')
 images=json.loads((DIR/'images.json').read_text());records={}
 for index,name in enumerate(names):
  port=MODULES[name][2];published=24300+list(MODULES).index(name)
  identity=None
  try:
   identity=docker(['run','-d','--name','vocational-acceptance-probe-'+name,'--label','com.tianji.acceptance.probe=true','--network','vocational-acceptance_default','--add-host','host.docker.internal:host-gateway','--memory','768m','--cpus','2','--oom-score-adj','500','--env-file',str(DIR/'env'),'-e','APP_PORT='+str(port),'-p','127.0.0.1:'+str(published)+':'+str(port),'-v',str(DIR/'hybrid'/(name+'.yml'))+':/run/acceptance/application.yml:ro','-v',str(DIR/'signing.jks')+':/run/acceptance/signing.jks:ro','-v',str(LOCAL/'objects')+':/run/objects', '--health-cmd','java -Xms16m -Xmx32m -cp /app/health HealthProbe','--health-interval','5s','--health-timeout','4s','--health-start-period','20s','--health-retries','30',images[name]['image']])
   end=time.monotonic()+180
   while time.monotonic()<end:
    state=inspect(identity)['State']
    if state['Status']=='exited':raise RuntimeError('Candidate exited: '+name)
    if state.get('Health',{}).get('Status')=='healthy':break
    time.sleep(2)
   else:raise TimeoutError('Candidate readiness '+name)
   if name!='gateway':
    r=requests.get('http://127.0.0.1:'+str(published)+'/v3/api-docs',headers={'X-Internal-Token':local_secrets()['ACCEPTANCE_INTERNAL_TOKEN'],'user-info':'1','user-role':'1'},timeout=20)
    assert r.ok and 'paths' in r.json(),(name,r.status_code)
   else:
    r=requests.get('http://127.0.0.1:'+str(published)+'/api/v2/environment',timeout=20)
    assert r.ok and r.json()['data']['payment']=='SIMULATED',r.status_code
   records[name]=dict(imageId=images[name]['imageId'],jarSha256=images[name]['jarSha256'],linuxReadiness=True,openApiOrGateway=True,user=inspect(identity)['Config']['User'])
   print('Linux candidate '+name+' ready and responding',flush=True)
  finally:
   if identity:
    docker(['stop','--time','30',identity])
    log=docker(['logs',identity]);(DIR/(name+'-container.log')).write_text(log,encoding='utf8')
    final=inspect(identity)['State']
    if name in records:
     records[name].update(exitCode=final['ExitCode'],gracefulShutdownLogged='graceful shutdown' in log.lower(),oomKilled=final['OOMKilled'],sentinelLogDirectoryHealthy='create Sentinel log base directory error' not in log)
    docker(['rm',identity])
    if name in records and (final['OOMKilled'] or final['ExitCode'] not in [0,143] or 'create Sentinel log base directory error' in log):raise RuntimeError('Candidate resource/logging failure '+name)
    (LOCAL/'container-smoke.json').write_text(json.dumps(dict(status='PASSED' if len(records)==len(names) else 'INCOMPLETE',scope='sequential Linux JVM containers with native acceptance peers, same isolated databases/broker; not a simultaneous all-container stack or HA test',services=records),indent=2),encoding='utf8')
 print('All selected Linux application candidates PASSED',flush=True)
if __name__=='__main__':main()
