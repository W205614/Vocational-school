"""Gateway races and reordered, fresh-ID like events against browser-created acceptance data."""
import json,uuid,time,threading,concurrent.futures
from api_smoke import call,login,accounts,BASE
from financial_smoke import order,pay,eventually,scalar
from projection_security_smoke import publish
from prepare import mysql,LOCAL

def payment_races():
 student=login('student');results=[]
 for delay in [0,0,.5,.5,3,3]:
  identity=order(student);pay(student,identity);barrier=threading.Barrier(2)
  def confirm():
   barrier.wait();call(student,'POST','/simulator/payments/'+identity+'/confirm')
  def cancel():
   barrier.wait();time.sleep(delay)
   response=student.put(BASE+'/orders/'+identity+'/cancel',timeout=20)
   assert response.status_code in [200,400,409],response.status_code
   return response.status_code
  with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
   confirmation=pool.submit(confirm);cancellation=pool.submit(cancel);confirmation.result();code=cancellation.result()
  eventually(lambda:scalar('SELECT COUNT(*) FROM payment_fact WHERE order_id='+identity)=='1','race payment fact')
  detail=scalar('SELECT id FROM order_detail WHERE order_id='+identity)
  state=scalar('SELECT status FROM `order` WHERE id='+identity)
  if state=='2':
   assert code in [400,409]
   eventually(lambda:scalar('SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id='+detail+' AND active=1','tj_learning')=='1','race paid grant')
   assert scalar('SELECT COUNT(*) FROM payment_conflict WHERE order_id='+identity)=='0'
  elif state=='3':
   assert code==200
   eventually(lambda:scalar('SELECT COUNT(*) FROM payment_conflict WHERE order_id='+identity)=='1','race cancellation conflict')
   assert scalar('SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id='+detail,'tj_learning')=='0'
   assert scalar('SELECT COUNT(*) FROM refund_apply WHERE order_detail_id='+detail)=='0'
  else:raise AssertionError('Unexpected race terminal '+state)
  results.append(dict(order=identity,state=int(state),cancelHttpStatus=code,grantCount=1 if state=='2' else 0,manualCaseCount=1 if state=='3' else 0,delaySeconds=delay))
 assert {r['state'] for r in results}=={2,3},results
 return results

def likes():
 student=login('student');user=accounts['student']['id']
 reply=mysql('SELECT id FROM interaction_reply WHERE user_id='+user+' ORDER BY id DESC LIMIT 1','tj_learning')
 assert reply,'Run browser fixture and business.spec before this gate'
 liked=mysql("SELECT COUNT(*) FROM liked_record WHERE user_id="+user+" AND biz_type='QA' AND biz_id="+reply,'tj_remark')=='1'
 def write(value):call(student,'POST','/services/remark/likes',dict(bizId=reply,bizType='QA',liked=value))
 def counter():return mysql("SELECT liked_times,version FROM liked_counter WHERE biz_type='QA' AND biz_id="+reply,'tj_remark').split('\t')
 try:
  write(False);base=int(counter()[0])
  with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:list(pool.map(write,[True]*100))
  count,positive=counter();assert int(count)==base+1
  assert mysql("SELECT COUNT(*) FROM liked_record WHERE user_id="+user+" AND biz_type='QA' AND biz_id="+reply,'tj_remark')=='1'
  old=json.loads(mysql("SELECT payload FROM reliability_outbox WHERE business_key='like:QA:"+reply+":"+positive+"'",'tj_remark'))
  with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:list(pool.map(write,[False]*100))
  count,version=counter();assert int(count)==base and int(version)>int(positive)
  eventually(lambda:mysql('SELECT liked_version FROM interaction_reply WHERE id='+reply,'tj_learning')==version,'latest unlike projection')
  identity=publish('like.record.topic','QA.times.changed',old)
  eventually(lambda:mysql("SELECT COUNT(*) FROM reliability_inbox WHERE consumer_name='reply.likes' AND event_id='"+identity+"'",'tj_learning')=='1','fresh-ID stale like consumed')
  assert mysql('SELECT liked_times,liked_version FROM interaction_reply WHERE id='+reply,'tj_learning')==str(base)+'\t'+version
  return dict(reply=reply,duplicateLikes=100,duplicateUnlikes=100,oldVersion=int(positive),currentVersion=int(version),staleFreshIdEventCannotRewind=True)
 finally:
  write(liked);latest=counter()[1]
  eventually(lambda:mysql('SELECT liked_version FROM interaction_reply WHERE id='+reply,'tj_learning')==latest,'original like state restored')

def main():
 result=dict(status='PASSED',paymentCancelRaces=payment_races(),likeProjection=likes())
 (LOCAL/'concurrency-edges.json').write_text(json.dumps(result,indent=2),encoding='utf8')
 print('Concurrent cancel/payment winners and duplicate/reordered like projections PASSED',flush=True)
if __name__=='__main__':main()
