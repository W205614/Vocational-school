import requests,json,time
from setup import LOCAL,PORTS,mysql,OFFSET
base='http://127.0.0.1:'+str(PORTS['gateway']);a=json.loads((LOCAL/'accounts.json').read_text(encoding='utf8'))['admin'];s=requests.Session()
r=s.post(base+'/api/v2/auth/accounts/admin/login',json=dict(type=1,username=a['username'],password=a['password']),timeout=15);r.raise_for_status();s.headers['Authorization']='Bearer '+r.json()['data']
ids=mysql("SELECT id FROM tj_course.course WHERE status IN(2,4) AND deleted=0").splitlines()
for start in range(0,len(ids),100):
 r=s.post(base+'/api/v2/admin/search/courses/up',params={'courseIds':','.join(ids[start:start+100])},timeout=20);r.raise_for_status()
end=time.monotonic()+120
while time.monotonic()<end:
 if mysql('SELECT COUNT(*) FROM tj_search.course_metadata_projection WHERE version>processed_version')=='0':break
 time.sleep(1)
else:raise RuntimeError('Search rebuild did not converge')
requests.post('http://127.0.0.1:'+str(24920+OFFSET)+'/course/_refresh',timeout=10).raise_for_status()
print('Restored search projection rebuilt: '+str(len(ids))+' courses')
