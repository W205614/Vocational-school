"""Recreate only affected services and verify callers recover without restarting."""
from pathlib import Path
import json,subprocess,time,requests,hashlib,ipaddress
from runtime import configure
BASE=Path(__file__).resolve().parent;LOCAL=BASE/'.local'
def main():
 p=configure()
 import api_smoke
 admin=api_smoke.login('admin')
 root='http://127.0.0.1:23501'
 def get(path,authorized=True,origin=None):
  return requests.get((origin or root)+path,headers=admin.headers if authorized else {},timeout=12)
 def ip(service):
  container=subprocess.check_output(p.COMPOSE+['ps','-q',service]).decode().strip()
  return next(iter(json.loads(subprocess.check_output(['docker','inspect',container]))[0]['NetworkSettings']['Networks'].values()))['IPAddress']
 def healthy(path,authorized=True,origin=None):
  end=time.monotonic()+25;start=time.monotonic()
  while time.monotonic()<end:
   try:
    r=get(path,authorized,origin)
    if r.status_code==200:return r,round(time.monotonic()-start,2)
   except requests.RequestException:pass
   time.sleep(1)
  raise RuntimeError('Caller did not recover within 25 seconds after container became healthy')
 checks=[]
 cases=[('app-exam','/api/v2/admin/exam/questions/page?pageNo=1&pageSize=20',True),('app-gateway','/api/v2/admin/exam/questions/page?pageNo=1&pageSize=20',True)]
 cover=json.loads((LOCAL/'admin-ux-fixture.json').read_text(encoding='utf8'))['cover']
 cases.append(('app-media',cover,False))
 for service,path,authorized in cases:
  initial=get(path,authorized);assert initial.status_code==200
  if authorized:assert initial.json()['code']==200
  assert get(path,authorized,"http://127.0.0.1:23500").status_code==200
  old=ip(service)
  with (LOCAL/('dns-recreate-'+service+'.log')).open('wb') as log:
   unavailable=None
   if service=='app-exam':
    try:
     subprocess.run(p.COMPOSE+['stop',service],check=True,stdout=log,stderr=subprocess.STDOUT)
     unavailable=get(path)
    finally:
     subprocess.run(p.COMPOSE+['up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','420',service],check=True,stdout=log,stderr=subprocess.STDOUT)
    assert unavailable.status_code==503 and unavailable.json()['code']==503
   else:
    subprocess.run(p.COMPOSE+['up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','420',service],check=True,stdout=log,stderr=subprocess.STDOUT)
  current=ip(service);result,recovery=healthy(path,authorized)
  # Docker may recycle the same address. Swap the allocation order of two
  # application containers, preserving volumes, to exercise real DNS changes.
  if current==old:
   partner='app-media' if service=='app-exam' else 'app-exam'
   partner_old=ip(partner)
   order=[service,partner] if ipaddress.ip_address(old)>ipaddress.ip_address(partner_old) else [partner,service]
   with (LOCAL/('dns-address-change-'+service+'.log')).open('wb') as log:
    try:
     subprocess.run(p.COMPOSE+['stop',service,partner],check=True,stdout=log,stderr=subprocess.STDOUT)
     subprocess.run(p.COMPOSE+['rm','-f',service,partner],check=True,stdout=log,stderr=subprocess.STDOUT)
    finally:
     results=[subprocess.run(p.COMPOSE+['up','-d','--no-deps','--wait','--wait-timeout','420',name],stdout=log,stderr=subprocess.STDOUT) for name in order]
     if any(result.returncode for result in results):raise RuntimeError('Application recovery failed; inspect private recreate log')
   current=ip(service)
   assert current!=old,'Address did not change; cannot claim DNS change recovery'
   result,recovery=healthy(path,authorized)
  student_result,student_recovery=healthy(path,authorized,"http://127.0.0.1:23500")
  if authorized:assert student_result.json()["code"]==200
  else:assert student_result.content==initial.content
  if authorized:assert result.json()['code']==200
  else:assert result.content==initial.content
  checks.append({'service':service,'oldAddress':old,'newAddress':current,'addressChanged':old!=current,'postHealthRecoverySeconds':recovery,'studentProxyRecoverySeconds':student_recovery,'imageBytesUnchanged':True if not authorized else None})
  print(service+': recovery PASSED; address changed='+str(old!=current),flush=True)
 report={'status':'PASSED','checks':checks,'examUnavailableStatus':503,'persistentCoverSha256':hashlib.sha256(result.content).hexdigest()}
 (LOCAL/'dns-recovery.json').write_text(json.dumps(report,indent=2),encoding='utf8')
if __name__=='__main__':main()
