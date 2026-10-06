"""Exercise current-state projections and cache publication via the real acceptance gateway/broker."""
import requests,json,uuid,concurrent.futures,subprocess,time
from api_smoke import call,login,accounts
from financial_smoke import eventually
from prepare import mysql,LOCAL,COMPOSE,local_secrets
ES='http://127.0.0.1:23920'
def scalar(sql,db):return mysql(sql,db)
def cached(identity):
 r=subprocess.run(COMPOSE+['exec','-T','redis','redis-cli','--raw','HGET','auth:{privileges}:snapshot',str(identity)],stdout=subprocess.PIPE,check=True)
 value=r.stdout.decode().strip();return json.loads(value) if value else None
def publish(exchange,key,payload,event=None):
 identity=event or uuid.uuid4().hex
 if isinstance(payload,dict) and 'eventId' in payload:payload=dict(payload,eventId=identity)
 else:payload=dict(eventId=identity,businessKey='acceptance:'+identity,eventType=key,schemaVersion=1,occurredAt='2026-10-06T00:00:00Z',payload=payload)
 r=requests.post('http://127.0.0.1:23372/api/exchanges/%2F/'+exchange+'/publish',auth=('acceptance',local_secrets()['ACCEPTANCE_MQ_PASSWORD']),json=dict(properties=dict(delivery_mode=2,message_id=identity,content_type='application/json'),routing_key=key,payload=json.dumps(payload),payload_encoding='string'),timeout=10);r.raise_for_status();assert r.json()['routed']
 return identity
def doc(identity):
 r=requests.get(ES+'/course/_doc/'+str(identity),timeout=5)
 return r.json().get('_source') if r.status_code==200 else None
def main():
 admin=login('admin');student=login('student');run=uuid.uuid4().hex[:12]
 call(admin,'GET','/admin/auth/privileges?sortBy=unsafe_sort',expected=400)
 call(admin,'GET','/admin/auth/privileges?pageSize=101',expected=400)
 p=call(admin,'POST','/admin/auth/privileges',dict(intro='Acceptance '+run,method='GET',uri='/acceptance/'+run,internal=False))
 identity=p['id']
 try:
  with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
   for f in [pool.submit(call,admin,'POST','/admin/auth/privileges/role/3',[identity]) for _ in range(30)]:f.result()
  assert scalar('SELECT COUNT(*) FROM role_privilege WHERE role_id=3 AND privilege_id='+identity,'tj_auth')=='1'
  eventually(lambda:cached(identity) is not None and '3' in list(map(str,cached(identity)['roles'])),'permission event publication')
  call(admin,'PUT','/admin/auth/privileges/'+identity,dict(p,intro='Updated '+run,uri='/acceptance/'+run+'/updated'))
  eventually(lambda:cached(identity)['antPath']=='GET:/acceptance/'+run+'/updated','updated permission cache')
  call(admin,'DELETE','/admin/auth/privileges/role/3',[identity])
  eventually(lambda:'3' not in list(map(str,cached(identity)['roles'])),'permission revocation publication')
  direct=requests.get('http://127.0.0.1:23884/privileges',headers={'user-info':accounts['admin']['id'],'user-role':'1'},timeout=10)
  assert direct.status_code==403
 finally:
  call(admin,'DELETE','/admin/auth/privileges/'+identity)
  eventually(lambda:cached(identity) is None,'deleted permission cache')
 fixture=json.loads((LOCAL/'browser-fixture.json').read_text());course=fixture['course']
 # Fixture-only status changes. The production worker must always query the current authoritative state.
 try:
  call(admin,'POST','/admin/search/courses/up?courseIds='+course)
  eventually(lambda:doc(course) is not None and doc(course)['available'] is True,'published metadata')
  scalar('UPDATE course SET status=3 WHERE id='+course,'tj_course')
  publish('course.topic','course.down',course)
  eventually(lambda:doc(course)['available'] is False,'down shelf must disappear')
  scalar('UPDATE course SET status=2 WHERE id='+course,'tj_course')
  publish('course.topic','course.up',course)
  eventually(lambda:doc(course)['available'] is True,'republished metadata')
  event=publish('course.topic','course.down',course)
  eventually(lambda:scalar("SELECT COUNT(*) FROM reliability_inbox WHERE consumer_name='search.course' AND event_id='"+event+"'",'tj_search')=='1','stale down receipt')
  expected=scalar('SELECT version FROM course_metadata_projection WHERE course_id='+course,'tj_search')
  eventually(lambda:scalar('SELECT processed_version FROM course_metadata_projection WHERE course_id='+course,'tj_search')==expected,'stale down event processed')
  assert doc(course)['available'] is True
  absent=str(int(course)+777)
  assert scalar('SELECT COUNT(*) FROM course WHERE id='+absent,'tj_course')=='0'
  publish('course.topic','course.delete',absent)
  eventually(lambda:doc(absent) is not None and doc(absent)['available'] is False,'delete listener durable tombstone')
  scalar("UPDATE course_metadata_projection SET status='DEAD',version=version+1,attempts=10,lease_token=NULL,lease_until=NULL WHERE course_id="+absent,'tj_search')
  version=scalar('SELECT version FROM course_metadata_projection WHERE course_id='+absent,'tj_search')
  call(admin,'POST','/admin/search-projections/'+absent+'/replay?version='+version+'&kind=metadata')
  call(admin,'POST','/admin/search-projections/'+absent+'/replay?version='+version+'&kind=metadata',expected=409)
  eventually(lambda:scalar('SELECT version=processed_version FROM course_metadata_projection WHERE course_id='+absent,'tj_search')=='1','metadata task replay')
 finally:
  scalar('UPDATE course SET status=2 WHERE id='+course,'tj_course')
 # Re-deliver a paid event after its detail was refunded. Provenance must not resurrect the sale.
 evidence=json.loads((LOCAL/'financial-smoke.json').read_text());detail=evidence['refundedDetail'];order=evidence['paidOrder']
 payload=json.loads(scalar("SELECT payload FROM reliability_outbox WHERE business_key='order:"+order+":paid'",'tj_trade'))
 before=scalar('SELECT sold FROM course_sales_projection WHERE course_id=1','tj_search')
 with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
  events=[f.result() for f in [pool.submit(publish,'order.topic','order.pay',payload) for _ in range(20)]]
 for event in events:eventually(lambda:scalar("SELECT COUNT(*) FROM reliability_inbox WHERE consumer_name='search.sales.true' AND event_id='"+event+"'",'tj_search')=='1','paid projection receipt')
 eventually(lambda:scalar('SELECT version=processed_version FROM course_sales_projection WHERE course_id=1','tj_search')=='1','absolute sales projection')
 assert scalar('SELECT active FROM course_sale_detail WHERE order_detail_id='+detail,'tj_search')=='0'
 assert scalar('SELECT sold FROM course_sales_projection WHERE course_id=1','tj_search')==before
 assert doc('1')['sold']==int(before)
 (LOCAL/'projection-security.json').write_text(json.dumps(dict(status='PASSED',checks=['pagination and sorting rejection','30 concurrent role bindings are unique','permission update/revoke/delete publish immutable cache','direct forged backend identity rejected','down shelf hidden','stale down event uses current metadata','course delete durable tombstone','versioned failed projection replay','late duplicate paid events cannot resurrect refunded sales']),indent=2))
 print('Projection and permission publication smoke PASSED',flush=True)
if __name__=='__main__':main()
