"""Fault only the named acceptance broker/JVM; restore infrastructure in finally."""
import requests,subprocess,sys,json,uuid,concurrent.futures,time
from prepare import LOCAL,BASE,COMPOSE,mysql,local_secrets
from api_smoke import call,login,wait,accounts
from financial_smoke import order,pay,eventually,scalar
from recovery_smoke import service,ready

def broker_outage():
 student=login('student');identity=order(student);pay(student,identity)
 before=int(scalar('SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id IN (SELECT id FROM tj_trade.order_detail WHERE order_id='+identity+')','tj_learning'))
 assert before==0
 try:
  subprocess.run(COMPOSE+['stop','rabbitmq'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  call(student,'POST','/simulator/payments/'+identity+'/confirm')
  assert scalar('SELECT COUNT(*) FROM provider_payment_fact WHERE biz_order_no='+identity,'tj_pay')=='1'
  assert scalar('SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id IN (SELECT id FROM tj_trade.order_detail WHERE order_id='+identity+')','tj_learning')=='0'
  number=scalar("SELECT pay_order_no FROM provider_payment_fact WHERE biz_order_no="+identity,'tj_pay')
  pending=int(scalar("SELECT COUNT(*) FROM reliability_outbox WHERE status<>'SENT' AND business_key='provider:"+number+":paid'",'tj_pay'))
  assert pending>0
 finally:
  subprocess.run(COMPOSE+['up','-d','--wait','rabbitmq'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 eventually(lambda:scalar('SELECT status FROM `order` WHERE id='+identity)=='2','broker recovery payment propagation',90)
 detail=scalar('SELECT id FROM order_detail WHERE order_id='+identity)
 eventually(lambda:scalar('SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id='+detail+' AND active=1','tj_learning')=='1','broker recovery grant',90)
 assert scalar("SELECT COUNT(*) FROM reliability_outbox WHERE business_key='order:"+identity+":paid'")=='1'
 return dict(order=identity,pendingBeforeRecovery=pending,grants=1)

def video_restart():
 fixture=json.loads((LOCAL/'browser-fixture.json').read_text());course=fixture['course'];section=fixture['video']
 assert mysql('SELECT COUNT(*) FROM learning_lesson WHERE user_id='+accounts['student']['id']+' AND course_id='+course,'tj_learning')=='0','requires fresh browser_fixture.py'
 mysql('UPDATE course_catalogue SET media_duration=120 WHERE id='+section,'tj_course')
 lesson=str(820000000000000000+int(time.time()))
 mysql('INSERT INTO learning_lesson(id,user_id,course_id,expire_time,status,learned_sections) VALUES('+lesson+','+accounts['student']['id']+','+course+',NOW()+INTERVAL 1 YEAR,0,0)','tj_learning')
 headers={'X-Internal-Token':local_secrets()['ACCEPTANCE_INTERNAL_TOKEN'],'user-info':accounts['student']['id'],'user-role':'2'}
 def report(moment,section_type=1):
  response=requests.post('http://127.0.0.1:23890/learning-records',headers=headers,json=dict(lessonId=lesson,sectionId=section,sectionType=section_type,moment=moment),timeout=20)
  assert response.status_code==(400 if section_type==2 else 200),(response.status_code,response.text)
 try:
  service('stop','learning');service('start','learning','--progress-interval-ms','600000');ready('learning',23890)
  report(40)
  record=scalar('SELECT id FROM learning_record WHERE lesson_id='+lesson+' AND section_id='+section,'tj_learning')
  assert scalar('SELECT moment FROM learning_record WHERE id='+record,'tj_learning')=='0'
  assert scalar('SELECT moment FROM learning_progress_pending WHERE record_id='+record,'tj_learning')=='40'
 finally:
  service('stop','learning');service('start','learning');ready('learning',23890)
 eventually(lambda:scalar('SELECT moment FROM learning_record WHERE id='+record,'tj_learning')=='40','persisted snapshot survives process restart')
 with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:list(pool.map(report,[100]*100))
 report(20);report(20,2)
 eventually(lambda:scalar('SELECT version=processed_version FROM learning_progress_pending WHERE record_id='+record,'tj_learning')=='1','progress projection completes')
 assert scalar('SELECT learned_sections FROM learning_lesson WHERE id='+lesson,'tj_learning')=='1'
 assert scalar('SELECT COUNT(*) FROM learning_record WHERE lesson_id='+lesson+' AND section_id='+section,'tj_learning')=='1'
 assert scalar('SELECT moment FROM learning_record WHERE id='+record,'tj_learning')=='100'
 return dict(lesson=lesson,completionReports=100,learnedSections=1,finalMoment=100,oldMomentCannotRewind=True,studentExamReportRejected=True)

def main():
 evidence=dict(status='PASSED',brokerOutage=broker_outage(),videoRestart=video_restart())
 (LOCAL/'runtime-faults.json').write_text(json.dumps(evidence,indent=2))
 print('Broker outage recovery and durable video progress restart PASSED',flush=True)
if __name__=='__main__':main()
