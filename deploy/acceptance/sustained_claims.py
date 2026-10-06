"""Closed-loop isolated claim windows. Writes only test coupons/users, never the current deployment."""
import argparse,requests,time,uuid,json,threading,concurrent.futures
from load_claims import snapshot,percentile,BASE
from prepare import mysql,LOCAL,local_secrets
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--seconds',type=int,default=60);args=parser.parse_args()
 if args.seconds<60:parser.error('sustained windows require at least 60 seconds')
 run=int(time.time());stages=[];seed=20261006
 for concurrency in [1,10,50,100,200]:
  stage_index=[1,10,50,100,200].index(concurrency);coupons=[930000000000000000+run*20+stage_index*3+i for i in range(3)]
  for coupon in coupons:
   if mysql('SELECT COUNT(*) FROM coupon WHERE id='+str(coupon),'tj_promotion')!='0':raise RuntimeError('Fixture collision')
   mysql(f"INSERT INTO coupon(id,name,discount_type,discount_value,obtain_way,issue_begin_time,issue_end_time,term_days,status,total_num,user_limit,creater,updater) VALUES({coupon},'Sustained fixture {seed}',3,100,1,NOW()-INTERVAL 1 HOUR,NOW()+INTERVAL 1 DAY,30,3,5000,1,1,1)",'tj_promotion')
  results=[];observations=[];lock=threading.Lock();counter=0;before=snapshot();started=time.monotonic();deadline=started+args.seconds;stop=threading.Event()
  def sample():
   while not stop.wait(5):
    observation=snapshot();observation['offsetSeconds']=time.monotonic()-started;observations.append(observation)
  def worker():
   nonlocal counter
   session=requests.Session()
   try:
    while time.monotonic()<deadline:
     with lock:counter+=1;sequence=counter
     if sequence>15000:raise RuntimeError('Fixture inventory budget reached')
     coupon=coupons[(sequence-1)//5000];user=940000000000000000+run*100000+stage_index*20000+sequence
     session.headers.update({'X-Internal-Token':local_secrets()['ACCEPTANCE_INTERNAL_TOKEN'],'user-info':str(user),'user-role':'2','Idempotency-Key':str(uuid.uuid5(uuid.NAMESPACE_URL,str(coupon)+':'+str(user)))})
     request_start=time.monotonic();admission=None
     try:
      response=session.post(BASE+'/api/v2/coupons/'+str(coupon)+'/claims',timeout=15);response.raise_for_status();assert response.status_code==202
      op=response.json()['data'];admission=time.monotonic()-request_start;op_deadline=time.monotonic()+90
      while op['status']=='PENDING' and time.monotonic()<op_deadline:
       time.sleep(.1);response=session.get(BASE+'/api/v2/operations/'+op['operationId'],timeout=15);response.raise_for_status();op=response.json()['data']
      if op['status']=='PENDING':raise TimeoutError('operation completion deadline')
      item={'status':op['status'],'elapsed':time.monotonic()-request_start,'admission':admission,'offset':time.monotonic()-started}
     except Exception as error:
      item={'status':'ERROR','elapsed':time.monotonic()-request_start,'admission':admission,'offset':time.monotonic()-started,'error':type(error).__name__};time.sleep(.5)
     with lock:results.append(item)
   finally:session.close()
  sampler=threading.Thread(target=sample,daemon=True);sampler.start()
  with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:list(pool.map(lambda _:worker(),range(concurrency)))
  stop.set();sampler.join(timeout=15);elapsed=time.monotonic()-started;after=snapshot()
  completed=[r for r in results if r['offset']<=args.seconds]
  counts={s:sum(r['status']==s for r in results) for s in ['SUCCEEDED','FAILED','ERROR']}
  identities=','.join(map(str,coupons))
  issued=int(mysql('SELECT SUM(issue_num) FROM coupon WHERE id IN('+identities+')','tj_promotion'))
  records=int(mysql('SELECT COUNT(*) FROM user_coupon WHERE coupon_id IN('+identities+')','tj_promotion'))
  duplicate=int(mysql('SELECT COUNT(*) FROM (SELECT coupon_id,user_id FROM user_coupon WHERE coupon_id IN('+identities+') GROUP BY coupon_id,user_id HAVING COUNT(*)>1) x','tj_promotion'))
  stage={'concurrency':concurrency,'windowSeconds':args.seconds,'drainSeconds':max(0,elapsed-args.seconds),'requests':len(results),'windowCompleted':len(completed),'windowThroughput':len(completed)/args.seconds,'p95Seconds':percentile([r['elapsed'] for r in results],.95),'p99Seconds':percentile([r['elapsed'] for r in results],.99),'unexpectedErrorRate':counts['ERROR']/max(1,len(results)),'counts':counts,'issued':issued,'records':records,'duplicate':duplicate,'tenSecondCompletions':[sum(i<=r['offset']<i+10 for r in completed) for i in range(0,args.seconds,10)],'before':before,'after':after,'observations':observations}
  stages.append(stage)
  (LOCAL/'sustained-claims.json').write_text(json.dumps({'seed':seed,'run':run,'scope':'60-second closed-loop private promotion HTTP admission and durable database completion, including client polling; simulated fixture users, 8 database connections; excludes gateway, real vendors and long-duration saturation','stages':stages},indent=2))
  print(json.dumps({k:stage[k] for k in ['concurrency','windowThroughput','p95Seconds','p99Seconds','unexpectedErrorRate','counts']}),flush=True)
  if counts['FAILED'] or counts['ERROR'] or issued!=records or records!=counts['SUCCEEDED'] or duplicate:raise AssertionError('Sustained claim invariant failed; inspect private evidence')
 print('Five bounded sustained windows PASSED',flush=True)
if __name__=='__main__':main()
