"""Run payment reliability tests in acceptance_pay, then package the affected reactor."""
from pathlib import Path
import os,re,shutil,subprocess,json
from runtime import configure
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[1];LOCAL=BASE/'.local'
def main():
 prepare=configure();prepare.mysql('CREATE DATABASE IF NOT EXISTS acceptance_pay')
 for table in ['pay_order','refund_order','provider_payment_fact','provider_refund_fact','provider_refund_conflict','provider_request_guard']:
  if prepare.mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='acceptance_pay' AND table_name='"+table+"'")=='0':prepare.mysql('CREATE TABLE '+table+' LIKE tj_pay.'+table,'acceptance_pay')
 for migration in sorted((BASE.parent/'acceptance/migrations/common').glob('V*.sql')):prepare.mysql(migration.read_text(encoding='utf8'),'acceptance_pay')
 env=os.environ.copy();env['ACCEPTANCE_DB_PASSWORD']=prepare.local_secrets()['ACCEPTANCE_DB_PASSWORD'];env.setdefault('JAVA_HOME','E:/Program Files/jdk')
 env.pop('TJ_REAL_PAYMENT_TESTS',None)
 tests=['AcceptanceFaultsTest','CookieBuilderTest','PageQueryTest','ResponseConverterTest','ProviderSettlementTest']
 command=[shutil.which('mvn') or 'E:/download/apache-maven-3.9.4/bin/mvn.cmd','-B','-Dmaven.repo.local='+env.get('MAVEN_LOCAL_REPO','E:/download/apache-maven-3.9.4/mvn_repo'),'-pl','tj-pay/tj-pay-service','-am','-Dtest='+','.join(tests),'-Dsurefire.failIfNoSpecifiedTests=false','package']
 path=LOCAL/'ux-pay-tests.log'
 with path.open('wb') as output:result=subprocess.run(command,cwd=ROOT,env=env,stdout=output,stderr=subprocess.STDOUT)
 log=path.read_text(encoding='utf8');missing=[name for name in tests if not re.search(r'Running [\w.$]*\.'+name+r'\b',log)]
 report={'status':'PASSED' if result.returncode==0 and not missing else 'FAILED','testClasses':tests,'missingTests':missing,'testDatabase':'acceptance_pay','realProviderTestsEnabled':False,'exitCode':result.returncode}
 (LOCAL/'ux-pay-tests.json').write_text(json.dumps(report,indent=2),encoding='utf8')
 print('Payment reliability reactor '+report['status']+'; details in private .local/ux-pay-tests.log')
 if report['status']!='PASSED':raise SystemExit(1)
if __name__=='__main__':main()
