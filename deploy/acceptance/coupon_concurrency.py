"""Real HTTP + MySQL acceptance test on isolated ports/data only."""
from prepare import mysql,LOCAL,local_secrets
import requests,time,uuid,json,concurrent.futures,collections
BASE_URL='http://127.0.0.1:23892';COUPON=900000000000000000+int(time.time())
def request(user,key):
 response=requests.post(BASE_URL+f'/api/v2/coupons/{COUPON}/claims',headers={'X-Internal-Token':local_secrets()['ACCEPTANCE_INTERNAL_TOKEN'],'user-info':str(user),'user-role':'2','Idempotency-Key':key},timeout=15)
 assert response.status_code==202,(response.status_code,response.text[:200])
 envelope=response.json();assert envelope['code']==200,envelope
 return envelope['data']['operationId']
def status(user,operation):
 response=requests.get(BASE_URL+'/api/v2/operations/'+operation,headers={'X-Internal-Token':local_secrets()['ACCEPTANCE_INTERNAL_TOKEN'],'user-info':str(user),'user-role':'2'},timeout=10)
 assert response.status_code==200,(response.status_code,response.text[:200])
 return response.json()['data']
def main():
 if mysql(f'SELECT COUNT(*) FROM coupon WHERE id={COUPON}','tj_promotion')!='0':
  raise RuntimeError('Fixture already exists; use a fresh fixture instead of deleting data')
 mysql(f"INSERT INTO coupon(id,name,discount_type,discount_value,obtain_way,issue_begin_time,issue_end_time,term_days,status,total_num,user_limit,creater,updater) VALUES({COUPON},'Acceptance concurrency',3,100,1,NOW()-INTERVAL 1 HOUR,NOW()+INTERVAL 1 DAY,30,3,100,1,1,1)",'tj_promotion')
 users=list(range(900000001,900000201));keys={u:str(uuid.uuid4()) for u in users}
 started=time.monotonic()
 with concurrent.futures.ThreadPoolExecutor(max_workers=50) as pool:operations=dict(zip(users,pool.map(lambda u:request(u,keys[u]),users)))
 # Concurrent original requests: HTTP acceptance retries return the same operation.
 first=users[0]
 with concurrent.futures.ThreadPoolExecutor(max_workers=50) as pool:repeated=list(pool.map(lambda _:request(first,keys[first]),range(100)))
 assert set(repeated)=={operations[first]}
 terminal={};deadline=time.monotonic()+90
 while len(terminal)<len(users) and time.monotonic()<deadline:
  for user,operation in operations.items():
   if user not in terminal:
    current=status(user,operation)
    if current['status']!='PENDING':terminal[user]=current
  if len(terminal)<len(users):time.sleep(.5)
 assert len(terminal)==200,'Operations did not reach terminal states'
 counts=collections.Counter(v['status'] for v in terminal.values())
 assert counts=={'SUCCEEDED':100,'FAILED':100},counts
 issued=int(mysql(f'SELECT issue_num FROM coupon WHERE id={COUPON}','tj_promotion'))
 records=int(mysql(f'SELECT COUNT(*) FROM user_coupon WHERE coupon_id={COUPON}','tj_promotion'))
 assert issued==records==100,(issued,records)
 assert mysql(f'SELECT COUNT(*) FROM (SELECT user_id FROM user_coupon WHERE coupon_id={COUPON} GROUP BY user_id HAVING COUNT(*)>1) t','tj_promotion')=='0'
 outsider=requests.get(BASE_URL+'/api/v2/operations/'+operations[first],headers={'X-Internal-Token':local_secrets()['ACCEPTANCE_INTERNAL_TOKEN'],'user-info':'899999999','user-role':'2'},timeout=10)
 assert outsider.status_code in (400,403,404),outsider.status_code
 result={'scenario':'stock 100, 200 users, concurrency 50, 100 identical request retries','status':'PASSED','operations':dict(counts),'stock_issued':issued,'coupon_records':records,'elapsed_seconds':round(time.monotonic()-started,3),'scope':'isolated upgraded promotion HTTP service; no gateway or real third parties'}
 (LOCAL/'coupon-concurrency.json').write_text(json.dumps(result,indent=2),encoding='utf8')
 print(json.dumps(result))
if __name__=='__main__':main()
