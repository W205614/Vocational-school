import requests,json
from setup import LOCAL,PORTS,mysql
base='http://127.0.0.1:'+str(PORTS['gateway']);accounts=json.loads((LOCAL/'accounts.json').read_text(encoding='utf8'))
checks=[]
for role in ['student','admin','teacher']:
 a=accounts[role];s=requests.Session();r=s.post(base+'/api/v2/auth/accounts/'+('login' if role=='student' else 'admin/login'),json=dict(type=1,username=a['username'],password=a['password']),timeout=15);r.raise_for_status();s.headers['Authorization']='Bearer '+r.json()['data']
 paths=['/services/user/users/me','/orders','/services/learning/lessons/page','/notes'] if role=='student' else ['/teacher/exam-attempts/page'] if role=='teacher' else ['/admin/audit/trade','/admin/audit/exam','/admin/operation-failures/trade']
 if role=='student':
  attempt=mysql('SELECT id FROM tj_exam.exam_attempt WHERE user_id='+a['id']+' ORDER BY id DESC LIMIT 1')
  if attempt:paths.append('/exam-attempts/'+attempt)
 for path in paths:
  r=s.get(base+'/api/v2'+path,timeout=15)
  if r.status_code!=200 or r.json().get('code')!=200:raise RuntimeError('Recovery business API failed: '+path+' status '+str(r.status_code))
  checks.append(role+':'+path)
(LOCAL/'recovery-business.json').write_text(json.dumps({'status':'PASSED','checks':checks},indent=2),encoding='utf8')
print('Restored identities and business-history endpoints passed: '+str(len(checks)))
