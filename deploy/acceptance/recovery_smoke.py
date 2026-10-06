"""Crash exact messaging boundaries, then restart only acceptance JVMs."""
import requests,subprocess,sys,json,time,uuid
from api_smoke import call,login,wait
from financial_smoke import eventually,scalar,pay
from prepare import LOCAL,BASE
def service(action,name,*options):
 subprocess.run([sys.executable,str(BASE/(action+'_service.py')),name,*options],check=True,stdout=subprocess.DEVNULL)
def ready(name,port):
 eventually(lambda:health(port),'service readiness '+name,90)
def health(port):
 try:return requests.get('http://127.0.0.1:'+str(port)+'/actuator/health/readiness',timeout=3).status_code==200
 except requests.RequestException:return False
def creation(student,identity):
 op=call(student,'POST','/orders',dict(orderId=identity,courseIds=['1'],couponIds=[]),uuid.uuid4().hex,202)
 assert wait(student,'trade',op)['status']=='SUCCEEDED'
def crash_reached(name):
 return 'ACCEPTANCE_FAULT' in (LOCAL/(name+'-service.log')).read_text(encoding='utf8',errors='replace')
def main():
 student=login('student');evidence=[]
 for name,port,point in [('trade',23888,'after-publish'),('learning',23890,'after-consume')]:
  identity=str(call(student,'GET','/orders/confirmation?courseIds=1')['orderId']);key='order:'+identity+':paid'
  service('stop',name);service('start',name,'--fault',point,'--fault-key',key);ready(name,port)
  creation(student,identity);pay(student,identity);call(student,'POST','/simulator/payments/'+identity+'/confirm')
  eventually(lambda:crash_reached(name),point+' crash',50)
  event=scalar("SELECT event_id FROM reliability_outbox WHERE business_key='"+key+"'")
  detail=scalar('SELECT id FROM order_detail WHERE order_id='+identity)
  eventually(lambda:scalar('SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id='+detail+' AND active=1','tj_learning')=='1','committed grant survives crash')
  before=scalar("SELECT status FROM reliability_outbox WHERE event_id='"+event+"'")
  (LOCAL/(name+'-'+point+'-crash.log')).write_text((LOCAL/(name+'-service.log')).read_text(encoding='utf8',errors='replace'),encoding='utf8')
  service('start',name);ready(name,port)
  eventually(lambda:scalar("SELECT status FROM reliability_outbox WHERE event_id='"+event+"'")=='SENT','publisher lease recovery',50)
  # Give the redelivery a chance, then require exactly one durable receipt and grant.
  time.sleep(3)
  assert scalar("SELECT COUNT(*) FROM reliability_inbox WHERE consumer_name='lesson.grant' AND event_id='"+event+"'",'tj_learning')=='1'
  assert scalar('SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id='+detail,'tj_learning')=='1'
  assert scalar("SELECT COUNT(*) FROM reliability_outbox WHERE business_key='"+key+"'")=='1'
  evidence.append(dict(point=point,order=identity,eventId=event,stateBeforeRestart=before,receiptCount=1,entitlementCount=1))
 (LOCAL/'recovery-smoke.json').write_text(json.dumps(dict(status='PASSED',checks=evidence),indent=2))
 print('Crash recovery and stable-ID redelivery PASSED',flush=True)
if __name__=='__main__':main()
