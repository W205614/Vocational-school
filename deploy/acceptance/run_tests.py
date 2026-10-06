from pathlib import Path
import os,subprocess,json,argparse,re,shutil
from prepare import BASE,LOCAL,mysql
REPO=BASE.parents[1]
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--modules',default='tj-common');parser.add_argument('--phase',choices=['test','install'],default='test');parser.add_argument('--tests',default='PageQueryTest,ReliabilityDatabaseTest');args=parser.parse_args()
 env=os.environ.copy()
 for line in (BASE/'.env').read_text().splitlines():
  if '=' in line: key,value=line.split('=',1);env[key]=value
 if 'JAVA_HOME' not in env:env['JAVA_HOME']='E:/Program Files/jdk'
 env['ACCEPTANCE_ES_URL']='http://127.0.0.1:23920'
 mysql("CREATE DATABASE IF NOT EXISTS acceptance_common")
 for script in sorted((BASE/'migrations/common').glob('V*.sql')):
  mysql(script.read_text(encoding='utf8'),'acceptance_common')
 for target,origin,tables in [('acceptance_learning','tj_learning',['learning_lesson','learning_record','points_record','points_daily_quota','points_projection']),('acceptance_exam','tj_exam',['exam_paper_family','exam_paper','exam_paper_question','exam_grader','exam_attempt','exam_answer']),('acceptance_pay','tj_pay',['pay_order','refund_order','provider_payment_fact','provider_refund_fact','provider_refund_conflict','provider_request_guard'])]:
  mysql('CREATE DATABASE IF NOT EXISTS '+target)
  for table in tables:
   if mysql(f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{target}' AND table_name='{table}'")=='0':mysql(f'CREATE TABLE {table} LIKE {origin}.{table}',target)
  for script in sorted((BASE/'migrations/common').glob('V*.sql')):mysql(script.read_text(encoding='utf8'),target)
 mysql("CREATE TABLE IF NOT EXISTS test_counter(id INT PRIMARY KEY,value INT NOT NULL);INSERT IGNORE INTO test_counter VALUES(1,0)",'acceptance_common')
 command=[shutil.which('mvn') or 'E:/download/apache-maven-3.9.4/bin/mvn.cmd','-B',*(['-pl',args.modules,'-am'] if args.modules!='all' else []),'-Dtest='+args.tests,'-Dsurefire.failIfNoSpecifiedTests=false',args.phase]
 with (LOCAL/'tests.log').open('w',encoding='utf8') as output:
  result=subprocess.run(command,cwd=REPO,env=env,stdout=output,stderr=subprocess.STDOUT)
 log=(LOCAL/'tests.log').read_text(encoding='utf8')
 missing=[name for name in args.tests.split(',') if not re.search(r'Running [\w.$]*\.'+re.escape(name)+r'\b',log)]
 if missing:print('Requested tests did not execute: '+','.join(missing))
 success=result.returncode==0 and not missing
 print('Acceptance tests '+('PASSED' if success else 'FAILED')+'; details in isolated .local/tests.log')
 raise SystemExit(0 if success else 1)
if __name__=='__main__':main()
