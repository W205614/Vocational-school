"""Restore the captured backup into separate audit schemas; exact counts and index evidence."""
from prepare import BASE,LOCAL,DATABASES,COMPOSE,mysql
import subprocess,json,hashlib,re
def main():
 snapshot=LOCAL/'baseline.sql'
 if not snapshot.exists():raise RuntimeError('Initial clone backup missing')
 data=snapshot.read_text(encoding='utf8')
 names=['audit_baseline_'+db for db in DATABASES]
 existing=mysql("SELECT schema_name FROM information_schema.schemata WHERE schema_name IN("+','.join("'"+db+"'" for db in names)+")")
 if not existing:
  for db in DATABASES:data=data.replace('`'+db+'`','`audit_baseline_'+db+'`')
  result=subprocess.run(COMPOSE+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot'],input=data.encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  if result.returncode:(LOCAL/'audit-restore-error.log').write_bytes(result.stderr);raise RuntimeError('Private audit backup restore failed')
 elif len(existing.splitlines())!=len(names):raise RuntimeError('Incomplete audit restore requires inspection; refusing automatic replacement')
 tables={}
 for db in DATABASES:
  source='audit_baseline_'+db
  for table in mysql("SHOW TABLES",source).splitlines():
   if not re.fullmatch('[a-zA-Z0-9_]+',table):raise ValueError('Unexpected table')
   old=int(mysql('SELECT COUNT(*) FROM `'+table+'`',source));new=int(mysql('SELECT COUNT(*) FROM `'+table+'`',db))
   tables[db+'.'+table]={'before':old,'after':new,'delta':new-old}
 checks={
 'duplicate_learning_records':("SELECT COUNT(*) FROM (SELECT lesson_id,section_id FROM learning_record GROUP BY lesson_id,section_id HAVING COUNT(*)>1) x",'tj_learning'),
 'duplicate_favorites':("SELECT COUNT(*) FROM (SELECT user_id,course_id FROM course_favorite GROUP BY user_id,course_id HAVING COUNT(*)>1) x",'tj_learning'),
 'duplicate_likes':("SELECT COUNT(*) FROM (SELECT user_id,biz_id,biz_type FROM liked_record GROUP BY user_id,biz_id,biz_type HAVING COUNT(*)>1) x",'tj_remark'),
 'active_exam_conflicts':("SELECT COUNT(*) FROM (SELECT user_id,paper_id FROM exam_attempt WHERE status IN('IN_PROGRESS','WAIT_GRADING') GROUP BY user_id,paper_id HAVING COUNT(*)>1) x",'tj_exam'),
 'coupon_oversell':("SELECT COUNT(*) FROM coupon WHERE issue_num>total_num",'tj_promotion'),
 'dangling_entitlements':("SELECT COUNT(*) FROM learning_entitlement e LEFT JOIN tj_trade.order_detail d ON d.id=e.order_detail_id WHERE d.id IS NULL",'tj_learning'),
 'cancelled_orders_with_entitlements':("SELECT COUNT(*) FROM learning_entitlement e JOIN tj_trade.order_detail d ON d.id=e.order_detail_id JOIN tj_trade.`order` o ON o.id=d.order_id WHERE o.status=3 AND e.active=1",'tj_learning')}
 verified={label:int(mysql(sql,db)) for label,(sql,db) in checks.items()}
 if any(verified.values()):raise AssertionError('Migration/business relation audit failed: '+str(verified))
 user=json.loads((LOCAL/'accounts.json').read_text())['student']['id']
 explains={}
 for label,sql,db in [
 ('order_page',f'EXPLAIN SELECT id FROM `order` WHERE user_id={user} ORDER BY create_time DESC LIMIT 20','tj_trade'),
 ('user_coupon',f'EXPLAIN SELECT id FROM user_coupon WHERE user_id={user} AND coupon_id=1','tj_promotion'),
 ('daily_points',f'EXPLAIN SELECT points FROM points_record WHERE user_id={user} AND create_time>=CURDATE()','tj_learning')]:
  explains[label]=mysql(sql,db)
 result={'status':'PASSED','backupSha256':hashlib.sha256(snapshot.read_bytes()).hexdigest(),'exactTableCounts':tables,'businessChecks':verified,'explain':explains,'scope':'private audit schemas restored from initial snapshot; fixture writes account for changes, original deployment untouched'}
 (LOCAL/'migration-audit.json').write_text(json.dumps(result,indent=2),encoding='utf8')
 print('Backup restore and migration relation audit PASSED; exact counts retained privately')
if __name__=='__main__':main()
