"""Run both built Nginx frontends against native acceptance peers; clean up only created IDs."""
import subprocess,time,json,os,requests,shutil
from prepare import BASE,LOCAL
from container_apps import DIR
from container_smoke import docker,inspect
ROOT=BASE.parents[1]
def main():
 identities=[];images=json.loads((DIR/'images.json').read_text(encoding='utf8'));result={}
 try:
  for app,port in [('student',23500),('admin',23501)]:
   identity=docker(['run','-d','--name','vocational-acceptance-web-probe-'+app,'--label','com.tianji.acceptance.probe=true','--network','vocational-acceptance_default','--add-host','host.docker.internal:host-gateway','--memory','128m','--cpus','1','-e','GW_UPSTREAM=host.docker.internal:23310','-p','127.0.0.1:'+str(port)+':8080',images[app]['image']])
   identities.append((app,identity))
   deadline=time.monotonic()+90
   while time.monotonic()<deadline:
    state=inspect(identity)['State']
    if state['Status']=='exited':raise RuntimeError('Nginx exited '+app)
    if state.get('Health',{}).get('Status')=='healthy':break
    time.sleep(2)
   else:raise TimeoutError('Nginx readiness '+app)
   response=requests.get('http://127.0.0.1:'+str(port)+'/notes' if app=='student' else 'http://127.0.0.1:'+str(port)+'/media',timeout=10)
   assert response.ok and '<html' in response.text,'SPA refresh fallback'
   result[app]=dict(imageId=images[app]['imageId'],spaRefresh=True,environmentProxy=True)
   print('Nginx '+app+' ready',flush=True)
  env=os.environ.copy();env['PATH']='E:/download/NodeJS;'+env['PATH'];env['ACCEPTANCE_CONTAINER_UI']='1'
  python=shutil.which('python') or 'E:/download/Anaconda/python.exe'
  # Prefer the runtime executing this script, avoiding another Python installation.
  import sys
  with (LOCAL/'container-browser-fixture.log').open('w',encoding='utf8') as log:
   subprocess.run([sys.executable,str(BASE/'browser_fixture.py')],check=True,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
  with (LOCAL/'playwright-container.log').open('w',encoding='utf8') as log:
   subprocess.run([shutil.which('npm',path=env['PATH']) or 'E:/download/NodeJS/npm.cmd','--prefix','frontend','run','test:e2e'],check=True,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
  text=(LOCAL/'playwright-container.log').read_text(encoding='utf8',errors='replace')
  assert '7 passed' in text and '7 skipped' in text
  (LOCAL/'container-browser.json').write_text(json.dumps(dict(status='PASSED',passed=7,expectedOtherProjectSkips=7,failed=0,frontends=result,scope='two built Nginx images; gateway/business peers use immutable native JARs and isolated Docker infrastructure'),indent=2),encoding='utf8')
  print('Container frontends and complete browser business path PASSED',flush=True)
 finally:
  for app,identity in identities:
   docker(['stop','--time','20',identity]);(DIR/(app+'-nginx.log')).write_text(docker(['logs',identity]),encoding='utf8');docker(['rm',identity])
if __name__=='__main__':main()
