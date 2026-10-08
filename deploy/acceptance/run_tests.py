from pathlib import Path
import os,subprocess,json,argparse,re,shutil
from prepare import BASE,LOCAL,mysql
REPO=BASE.parents[1]
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--modules',default='tj-common');parser.add_argument('--phase',choices=['test','install'],default='test');parser.add_argument('--tests',default='PageQueryTest,ReliabilityDatabaseTest');args=parser.parse_args()
 env=os.environ.copy()
 for line in (Path(os.environ.get('TJ_ENV_FILE',str(BASE/'.env')))).read_text().splitlines():
  if '=' in line: key,value=line.split('=',1);env[key]=value
 if 'JAVA_HOME' not in env:env['JAVA_HOME']='E:/Program Files/jdk'
 env.setdefault('ACCEPTANCE_ES_URL','http://127.0.0.1:23920')
 env.setdefault('ACCEPTANCE_AUTH_DB_URL','jdbc:mysql://127.0.0.1:'+env.get('ACCEPTANCE_DB_PORT','23316')+'/acceptance_auth?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true')
 mysql("CREATE DATABASE IF NOT EXISTS acceptance_auth")
 from migrate import apply_migrations
 apply_migrations('acceptance_auth',list((BASE/'migrations/tj_auth').glob('V003*.sql')))
 mysql("CREATE DATABASE IF NOT EXISTS acceptance_common")
 from migrate import apply_migrations
 apply_migrations('acceptance_common',list((BASE/'migrations/common').glob('V*.sql')))
 for target,origin,tables in [('acceptance_learning','tj_learning',['learning_lesson','learning_record','learning_entitlement','learning_entitlement_guard','points_record','points_daily_quota','points_projection']),('acceptance_exam','tj_exam',['exam_paper_family','exam_paper','exam_paper_question','exam_grader','exam_attempt','exam_answer']),('acceptance_pay','tj_pay',['pay_order','refund_order','provider_payment_fact','provider_refund_fact','provider_refund_conflict','provider_request_guard'])]:
  mysql('CREATE DATABASE IF NOT EXISTS '+target)
  for table in tables:
   if mysql(f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{target}' AND table_name='{table}'")=='0':mysql(f'CREATE TABLE {table} LIKE {origin}.{table}',target)
  apply_migrations(target,list((BASE/'migrations/common').glob('V*.sql')))
 apply_migrations('acceptance_exam',list((BASE/'migrations/tj_exam').glob('V003*.sql')))
 mysql('CREATE DATABASE IF NOT EXISTS acceptance_trade')
 if mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='tj_trade' AND table_name='order'")=='1':
  mysql('CREATE TABLE IF NOT EXISTS `order` LIKE tj_trade.`order`','acceptance_trade')
 else:
  # CI has no application clone. Use the retained-order schema from the public synthetic bootstrap.
  bootstrap=(REPO/'deploy/compact/schema.sql').read_text(encoding='utf8')
  schema=re.search(r'CREATE TABLE `order` \(.*?\) ENGINE=.*?;',bootstrap,re.S)
  if schema is None:raise RuntimeError('Missing financial regression schema')
  mysql(schema.group().replace('CREATE TABLE','CREATE TABLE IF NOT EXISTS',1),'acceptance_trade')
 for table in ('payment_fact','payment_conflict'):
  if mysql(f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='acceptance_trade' AND table_name='{table}'")=='0':
   if mysql(f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='tj_trade' AND table_name='{table}'")=='1':mysql(f'CREATE TABLE {table} LIKE tj_trade.{table}','acceptance_trade')
   else:
    source=(BASE/'migrations/tj_trade/V002__payment_integrity.sql').read_text(encoding='utf8')
    definition=re.search(r'CREATE TABLE '+table+r'\s*\(.*?\) ENGINE=.*?;',source,re.S)
    if definition is None:raise RuntimeError('Missing retained financial fact schema')
    mysql(definition.group(),'acceptance_trade')
 mysql("CREATE TABLE IF NOT EXISTS test_counter(id INT PRIMARY KEY,value INT NOT NULL);INSERT IGNORE INTO test_counter VALUES(1,0)",'acceptance_common')
 command=[shutil.which('mvn') or 'E:/download/apache-maven-3.9.4/bin/mvn.cmd','-B',*(['-pl',args.modules,'-am'] if args.modules!='all' else []),'-Dtest='+args.tests,'-Dsurefire.failIfNoSpecifiedTests=false',args.phase]
 with (LOCAL/'tests.log').open('w',encoding='utf8') as output:
  result=subprocess.run(command,cwd=REPO,env=env,stdout=output,stderr=subprocess.STDOUT)
 log=(LOCAL/'tests.log').read_text(encoding='utf8')
 missing=[name for name in args.tests.split(',') if not re.search(r'Running [\w.$]*\.'+re.escape(name)+r'\b',log)]
 if missing:print('Requested tests did not execute: '+','.join(missing))
 summaries=re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+).* -- in ([\w.$]+)',log)
 skipped=sum(int(r[3]) for r in summaries)
 success=result.returncode==0 and not missing and skipped==0 and bool(summaries)
 if skipped:print('Required backend cases were skipped: '+str(skipped))
 (LOCAL/'backend-result.json').write_text(json.dumps({'status':'PASSED' if success else 'FAILED','requestedClasses':args.tests.split(','),'executedClasses':[r[4] for r in summaries],'tests':sum(int(r[0]) for r in summaries),'failures':sum(int(r[1]) for r in summaries),'errors':sum(int(r[2]) for r in summaries),'skipped':sum(int(r[3]) for r in summaries)},indent=2),encoding='utf8')
 print('Acceptance tests '+('PASSED' if success else 'FAILED')+'; details in isolated .local/tests.log')
 raise SystemExit(0 if success else 1)
if __name__=='__main__':main()
