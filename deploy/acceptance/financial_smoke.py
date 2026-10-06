"""Real gateway, transactions and RabbitMQ; explicit isolated payment simulator."""
import json,time,uuid,concurrent.futures
from api_smoke import call,login,wait,accounts
from prepare import mysql,LOCAL
def eventually(check,description,seconds=45):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  if check():return
  time.sleep(.3)
 raise AssertionError(description)
def scalar(sql,db='tj_trade'):return mysql(sql,db)
def order(student,courses=None):
 courses=courses or ['1']
 confirm=call(student,'GET','/orders/confirmation?courseIds='+','.join(courses))
 body=dict(orderId=confirm['orderId'],courseIds=courses,couponIds=[])
 key=uuid.uuid4().hex
 first=call(student,'POST','/orders',body,key,202)
 repeated=call(student,'POST','/orders',body,key,202)
 assert first['operationId']==repeated['operationId']
 result=wait(student,'trade',first)
 assert result['status']=='SUCCEEDED',result
 return str(confirm['orderId'])
def pay(student,identity):
 result=call(student,'POST','/services/trade/pay/order',dict(orderId=identity,payChannelCode='mockPay'))
 assert result=='/simulate-payment/'+identity
def main():
 student=login('student');admin=login('admin')
 environment=call(student,'GET','/environment')
 assert environment['payment']=='SIMULATED'
 identity=order(student);pay(student,identity)
 with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
  futures=[pool.submit(call,student,'POST','/simulator/payments/'+identity+'/confirm') for _ in range(20)]
  for future in futures:future.result()
 eventually(lambda:scalar('SELECT status FROM `order` WHERE id='+identity)=='2','payment status')
 detail=scalar('SELECT id FROM order_detail WHERE order_id='+identity)
 eventually(lambda:scalar('SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id='+detail+' AND active=1','tj_learning')=='1','grant')
 assert scalar("SELECT COUNT(*) FROM reliability_outbox WHERE business_key='order:"+identity+":paid'")=='1'
 assert scalar('SELECT COUNT(*) FROM payment_fact WHERE order_id='+identity)=='1'
 # Repeated asynchronous refund command shares operation and result.
 key=uuid.uuid4().hex;body=dict(orderDetailId=detail,refundReason='Acceptance',questionDesc='Isolated verification')
 request=call(student,'POST','/services/trade/refund-apply',body,key,202)
 assert request['operationId']==call(student,'POST','/services/trade/refund-apply',body,key,202)['operationId']
 assert wait(student,'trade',request)['status']=='SUCCEEDED'
 refund=scalar('SELECT id FROM refund_apply WHERE order_detail_id='+detail+' ORDER BY id DESC LIMIT 1')
 approved=call(admin,'PUT','/services/trade/refund-apply/approval',dict(id=refund,approveType=1,approveOpinion='Acceptance'),uuid.uuid4().hex,202)
 assert wait(admin,'trade',approved)['status']=='SUCCEEDED'
 eventually(lambda:scalar('SELECT status FROM refund_apply WHERE id='+refund)=='5','refund terminal')
 eventually(lambda:scalar('SELECT status FROM `order` WHERE id='+identity)=='7','refunded order terminal')
 eventually(lambda:scalar('SELECT active FROM learning_entitlement WHERE order_detail_id='+detail,'tj_learning')=='0','target entitlement revoked')
 assert scalar('SELECT COUNT(*) FROM learning_lesson WHERE user_id='+accounts['student']['id']+' AND course_id=1','tj_learning')=='1'
 # Late payment is a persisted manual case and must not grant an entitlement.
 closed=order(student);pay(student,closed);call(student,'PUT','/orders/'+closed+'/cancel')
 call(student,'POST','/simulator/payments/'+closed+'/confirm')
 eventually(lambda:scalar('SELECT COUNT(*) FROM payment_conflict WHERE order_id='+closed)=='1','late payment case')
 assert scalar('SELECT status FROM `order` WHERE id='+closed)=='3'
 closed_detail=scalar('SELECT id FROM order_detail WHERE order_id='+closed)
 assert scalar('SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id='+closed_detail,'tj_learning')=='0'
 assert scalar('SELECT COUNT(*) FROM refund_apply WHERE order_detail_id='+closed_detail)=='0'
 # Two distinct refund callbacks compete for the same parent order. Duplicate approvals retain their operation.
 fixture=json.loads((LOCAL/'browser-fixture.json').read_text())
 dual=order(student,['1',fixture['course']]);pay(student,dual);call(student,'POST','/simulator/payments/'+dual+'/confirm')
 eventually(lambda:scalar('SELECT status FROM `order` WHERE id='+dual)=='2','two-detail payment')
 details=scalar('SELECT id FROM order_detail WHERE order_id='+dual).splitlines();assert len(details)==2
 requests=[]
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
  for result in [pool.submit(call,student,'POST','/services/trade/refund-apply',dict(orderDetailId=d,refundReason='Concurrent acceptance',questionDesc='Two details'),uuid.uuid4().hex,202) for d in details]:requests.append(result.result())
 for result in requests:assert wait(student,'trade',result)['status']=='SUCCEEDED'
 refunds=[scalar('SELECT id FROM refund_apply WHERE order_detail_id='+d+' ORDER BY id DESC LIMIT 1') for d in details]
 with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
  approvals=[]
  for r in refunds:
   key=uuid.uuid4().hex;body=dict(id=r,approveType=1,approveOpinion='Concurrent acceptance')
   approvals.extend([pool.submit(call,admin,'PUT','/services/trade/refund-apply/approval',body,key,202) for _ in range(10)])
  results=[result.result() for result in approvals]
 assert len(set(result['operationId'] for result in results))==2
 for result in results:assert wait(admin,'trade',result)['status']=='SUCCEEDED'
 eventually(lambda:scalar('SELECT status FROM `order` WHERE id='+dual)=='7','concurrent callbacks aggregate refunded terminal',90)
 for d in details:
  assert scalar('SELECT status FROM order_detail WHERE id='+d)=='7'
  eventually(lambda:scalar('SELECT active FROM learning_entitlement WHERE order_detail_id='+d,'tj_learning')=='0','target entitlement revoked')
 evidence=dict(status='PASSED',paidOrder=identity,refundedDetail=detail,latePaidOrder=closed,concurrentRefundOrder=dual,checks=['idempotent order','20 repeated payment confirmations','one payment fact and paid event','grant by order detail','idempotent refund command','refund terminal and entitlement revoke','learning history preserved','concurrent two-detail refunds and 20 repeated approvals aggregate one terminal order','cancelled late payment manual case without automatic grant or refund'])
 (LOCAL/'financial-smoke.json').write_text(json.dumps(evidence,indent=2))
 print('Financial gateway + RabbitMQ smoke PASSED',flush=True)
if __name__=='__main__':main()
