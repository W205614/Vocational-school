"""Exercise actual gateway/identity/DB invalidation. Touch only generated fixture identities."""
import json,time,requests
from setup import LOCAL,PORTS,mysql,configure_acceptance
BASE='http://127.0.0.1:'+str(PORTS['gateway']);accounts=json.loads((LOCAL/'accounts.json').read_text(encoding='utf8'));checks=[]
def check(condition,name):
 if not condition:raise AssertionError(name)
 checks.append(name);print('PASS '+name,flush=True)
def login(role='student'):
 a=accounts[role];session=requests.Session();r=session.post(BASE+'/api/v2/auth/accounts/'+('admin/login' if role!='student' else 'login'),json=dict(type=1,username=a['username'],password=a['password'],rememberMe=False),timeout=10)
 check(r.status_code==200,'login '+role);session.headers['Authorization']='Bearer '+r.json()['data'];return session
s=login();a=login('admin');uid=accounts['student']['id'];admin=accounts['admin']['id']
check(requests.get('http://127.0.0.1:'+str(PORTS['identity'])+'/_modules/user/users/list?ids=0',timeout=10).status_code==403,'direct internal API rejects anonymous')
check(s.get(BASE+'/api/v2/services/user/users/list?ids=0',timeout=10).status_code==403,'hidden user API not exposed through gateway')
check(s.get(BASE+'/api/v2/admin/dashboard',headers={'user-info':admin,'user-role':'1'},timeout=10).status_code==403,'forged identity cannot escalate role')
check(s.post(BASE+'/api/v2/services/user/unconfigured-action',timeout=10).status_code==403,'unknown permission defaults to deny')
check(s.get(BASE+'/api/v2/services/user/users/me',timeout=10).status_code==200,'known student API works')
# Exercise real host HTTP dispatch with mixed callers and forged forwarded headers.
import concurrent.futures
teacher=login('teacher')
roles=[('student',s),('admin',a),('teacher',teacher)]
def profile(index):
 role,client=roles[index%len(roles)]
 headers={'Authorization':client.headers['Authorization'],'user-info':admin,'user-role':'1','X-User-Id':admin,'X-TJ-Call-Depth':'4'}
 response=requests.get(BASE+'/api/v2/services/user/users/me',headers=headers,timeout=10)
 return response.status_code==200 and str(response.json().get('data',{}).get('id'))==str(accounts[role]['id'])
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
 check(all(pool.map(profile,range(60))),'60 real concurrent HTTP requests keep caller identities isolated')
# User profile changes are authoritative via DB triggers. Old signed tokens must fail within ten seconds.
for label,statement,restore in [('disable',f'UPDATE tj_user.user SET status=0 WHERE id={uid}',f'UPDATE tj_user.user SET status=1 WHERE id={uid}'),('password',f'UPDATE tj_user.user SET password=CONCAT(password,"x") WHERE id={uid}',None),('role',f'UPDATE tj_user.user_detail SET role_id=1 WHERE id={uid}',f'UPDATE tj_user.user_detail SET role_id=2 WHERE id={uid}')]:
 s=login();old=mysql(f'SELECT password FROM tj_user.user WHERE id={uid}') if label=='password' else None
 try:
  mysql(statement);start=time.monotonic();time.sleep(5.2);r=s.get(BASE+'/api/v2/services/user/users/me',timeout=10);check(r.status_code in (401,403) and time.monotonic()-start<=10,label+' invalidates old access <=10s')
  r=s.get(BASE+'/api/v2/auth/accounts/refresh?audience=student',timeout=10);check(r.status_code in (401,403),label+' invalidates old refresh')
 finally:mysql(restore or f"UPDATE tj_user.user SET password='{old}' WHERE id={uid}")
s=login();r=s.get(BASE+'/api/v2/auth/accounts/refresh?audience=student',timeout=10);check(r.status_code==200,'refresh rotates live session');s.headers['Authorization']='Bearer '+r.json()['data']
r=s.get(BASE+'/api/v2/auth/accounts/sessions',timeout=10);check(r.status_code==200,'own session listing');sid=json.loads(__import__('base64').urlsafe_b64decode(s.headers['Authorization'].split('.')[1]+'=='))['user']['sessionId'];check(s.delete(BASE+'/api/v2/auth/accounts/sessions/'+sid,timeout=10).status_code==200,'owner session revocation');time.sleep(5.2);check(s.get(BASE+'/api/v2/services/user/users/me',timeout=10).status_code in (401,403),'revoked access rejected')
for i in range(11):
 r=requests.post(BASE+'/api/v2/auth/accounts/login',json=dict(type=1,username=accounts['student']['username'],password='invalid-qa-password',rememberMe=False),timeout=10)
check(r.status_code==429,'account brute force temporarily limited')
# Run a negative privilege probe using the actual runtime account, never root.
from setup import COMPOSE,secrets_config
import subprocess
passwords=secrets_config()[1]
for sql,label in [('SELECT COUNT(*) FROM tj_user.user','runtime account cannot read another schema'),('CREATE TABLE tj_auth.__runtime_ddl_probe(id INT)','runtime account cannot execute DDL')]:
 r=subprocess.run(COMPOSE+['exec','-T','-e','MYSQL_PWD='+passwords['auth'],'mysql','mysql','-uapp_auth','-e',sql],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 if r.returncode==0 and sql.startswith('CREATE'):mysql('DROP TABLE tj_auth.__runtime_ddl_probe')
 check(r.returncode!=0,label)
(LOCAL/'security-smoke.json').write_text(json.dumps({'status':'PASSED','checks':checks},ensure_ascii=False,indent=2),encoding='utf8')
print('Actual security regression passed: '+str(len(checks)),flush=True)
