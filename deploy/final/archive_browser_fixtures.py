"""Unpublish only isolated browser fixtures through the ordinary course API.

Retain orders, notes, exam results and course drafts for verification/recovery.
"""
import json,time,uuid,requests
from runtime import configure

def main():
 p=configure()
 from api_smoke import login,call
 accounts=json.loads((p.LOCAL/'accounts.json').read_text(encoding='utf8'))
 admin_id=accounts['admin']['id']
 if not admin_id.isdigit():raise ValueError('Invalid fixture owner')
 rows=[row.split('\t') for row in p.mysql("SELECT id,status FROM course WHERE id BETWEEN 810000000000000000 AND 811000000000000000 AND creater="+admin_id+" AND name REGEXP '^Browser (course|free) [0-9a-f]{12}$' AND status IN(2,3) AND deleted=0",'tj_course').splitlines()]
 ids=[row[0] for row in rows]
 admin=login('admin')
 for identity,status in rows:
  if not identity.isdigit():raise ValueError('Invalid fixture identity')
  if status=='2':call(admin,'POST','/services/course/courses/downShelf',{'id':identity},uuid.uuid4().hex)
 deadline=time.monotonic()+90
 while ids:
  requests.post('http://127.0.0.1:23920/course/_refresh',timeout=10).raise_for_status()
  result=requests.post('http://127.0.0.1:23920/course/_count',json={'query':{'bool':{'filter':[{'ids':{'values':ids}},{'term':{'available':True}}]}}},timeout=10);result.raise_for_status()
  if result.json()['count']==0:break
  if time.monotonic()>deadline:raise RuntimeError('Fixture removal projection did not finish')
  time.sleep(1)
 (p.LOCAL/'browser-fixture-archive.json').write_text(json.dumps({'status':'PASSED','unpublishedCourses':len(ids),'courseIds':ids,'historyRetained':True},indent=2),encoding='utf8')
 print('Unpublished '+str(len(ids))+' browser-only courses; history and drafts retained',flush=True)

if __name__=='__main__':main()
