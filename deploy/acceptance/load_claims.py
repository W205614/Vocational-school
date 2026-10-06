"""Finite isolated coupon workloads; reports observed capacity, not production promises."""
import argparse,random,uuid,requests,time,json,statistics,concurrent.futures
from prepare import LOCAL,mysql,local_secrets
BASE='http://127.0.0.1:23892'
def percentile(values,p):return sorted(values)[min(len(values)-1,max(0,int((len(values)-1)*p)))]
def snapshot():
 result={'db':mysql("SHOW GLOBAL STATUS WHERE Variable_name IN ('Innodb_row_lock_waits','Innodb_row_lock_time','Threads_running')")}
 try:
  from prepare import local_secrets
  r=requests.get(BASE+'/actuator/prometheus',headers={'X-Internal-Token':local_secrets()['ACCEPTANCE_INTERNAL_TOKEN']},timeout=5)
  if r.ok:
   result['prometheus']=[line for line in r.text.splitlines() if not line.startswith('#') and any(line.startswith(x) for x in ['managed_executor','tj_events','tj_operations','jvm_memory_used','jvm_threads','hikaricp_'])]
 except requests.RequestException:pass
 return result
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--requests',type=int,default=200);args=parser.parse_args()
 if args.requests<200:parser.error('at least 200 requests required')
 run=int(time.time());seed=20261006;randomizer=random.Random(seed);stages=[]
 for concurrency in [1,10,50,100,200]:
  coupon=910000000000000000+run*10+[1,10,50,100,200].index(concurrency)
  if mysql('SELECT COUNT(*) FROM coupon WHERE id='+str(coupon),'tj_promotion')!='0':raise RuntimeError('Refusing to overwrite fixture')
  mysql(f"INSERT INTO coupon(id,name,discount_type,discount_value,obtain_way,issue_begin_time,issue_end_time,term_days,status,total_num,user_limit,creater,updater) VALUES({coupon},'Load fixture {seed}',3,100,1,NOW()-INTERVAL 1 HOUR,NOW()+INTERVAL 1 DAY,30,3,100,1,1,1)",'tj_promotion')
  users=list(range(920000000000000000+run*1000,920000000000000000+run*1000+args.requests));randomizer.shuffle(users)
  def execute(user):
   session=requests.Session();session.headers.update({'X-Internal-Token':local_secrets()['ACCEPTANCE_INTERNAL_TOKEN'],'user-info':str(user),'user-role':'2','Idempotency-Key':str(uuid.uuid5(uuid.NAMESPACE_URL,str(coupon)+':'+str(user)))})
   started=time.monotonic();admission=None
   try:
    response=session.post(BASE+'/api/v2/coupons/'+str(coupon)+'/claims',timeout=15);response.raise_for_status();op=response.json()['data'];admission=time.monotonic()-started
    assert response.status_code==202
    deadline=time.monotonic()+90
    while op['status']=='PENDING' and time.monotonic()<deadline:
     time.sleep(.1);response=session.get(BASE+'/api/v2/operations/'+op['operationId'],timeout=10);response.raise_for_status();op=response.json()['data']
    if op['status']=='PENDING':raise TimeoutError('operation did not finish')
    return dict(status=op['status'],admission=admission,elapsed=time.monotonic()-started,error=op.get('errorMessage'))
   except Exception as error:return dict(status='ERROR',admission=admission,elapsed=time.monotonic()-started,error=type(error).__name__+': '+str(error))
   finally:session.close()
  before=snapshot();started=time.monotonic()
  with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:results=list(pool.map(execute,users))
  elapsed=time.monotonic()-started;after=snapshot();latencies=[r['elapsed'] for r in results]
  counts={s:sum(r['status']==s for r in results) for s in ['SUCCEEDED','FAILED','ERROR']}
  issued=int(mysql('SELECT issue_num FROM coupon WHERE id='+str(coupon),'tj_promotion'));records=int(mysql('SELECT COUNT(*) FROM user_coupon WHERE coupon_id='+str(coupon),'tj_promotion'))
  duplicates=mysql('SELECT COUNT(*) FROM (SELECT user_id FROM user_coupon WHERE coupon_id='+str(coupon)+' GROUP BY user_id HAVING COUNT(*)>1) x','tj_promotion')
  stage=dict(concurrency=concurrency,requests=args.requests,durationSeconds=elapsed,terminalThroughput=args.requests/elapsed,p95Seconds=percentile(latencies,.95),p99Seconds=percentile(latencies,.99),admissionP95Seconds=percentile([r['admission'] for r in results if r['admission'] is not None],.95),unexpectedErrorRate=counts['ERROR']/args.requests,counts=counts,issued=issued,records=records,duplicates=int(duplicates),before=before,after=after)
  assert counts=={'SUCCEEDED':100,'FAILED':args.requests-100,'ERROR':0} and issued==records==100 and duplicates=='0',stage
  stages.append(stage);print(json.dumps({k:stage[k] for k in ['concurrency','terminalThroughput','p95Seconds','p99Seconds','unexpectedErrorRate']}),flush=True)
  (LOCAL/'load-claims.json').write_text(json.dumps(dict(seed=seed,run=run,scope='finite stock-contention workload, isolated promotion service, client includes polling; no gateway or real vendors',sustainedCapacityVerified=False,stages=stages),indent=2))
 print('All five contention stages preserved stock and uniqueness',flush=True)
if __name__=='__main__':main()
