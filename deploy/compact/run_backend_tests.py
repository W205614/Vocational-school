import json,os,runpy,shutil,sys,uuid
from setup import configure_acceptance,BASE,ACC,OFFSET,LOCAL,SOURCE,secrets_config
configure_acceptance()
os.environ.update(TJ_ENV_FILE=str(BASE/'.env'),ACCEPTANCE_DB_PORT=str(24316+OFFSET),ACCEPTANCE_REDIS_PORT=str(24379+OFFSET),ACCEPTANCE_MQ_PORT=str(24373+OFFSET),ACCEPTANCE_MQ_USERNAME='tianji',ACCEPTANCE_ES_URL='http://127.0.0.1:'+str(24920+OFFSET))
# Initialize dedicated schemas only. Application and historical records are never reset by tests.
runpy.run_path(str(ACC/'initialize_test_schemas.py'),run_name='__main__')
sys.argv=[str(ACC/'run_tests.py'),'--modules','all','--tests','PageQueryTest,ReliabilityDatabaseTest,ResponseConverterTest,CookieBuilderTest,AcceptanceFaultsTest,LearningReliabilityTest,DelayTaskTest,ExamReliabilityTest,ExamDraftReliabilityTest,ObjectiveScoringTest,DiscountServiceTest,ProviderSettlementTest,CourseRepositoryBulkTest,FeignBulkheadTest,ReliabilityMetricsTest,AsyncSmsClientReliabilityTest,VerificationCodeReliabilityTest,LikeConcurrencyReliabilityTest,CourseDeadlineTest,CourseTeacherDraftTest,LocalCourseCoverStoreTest,AccessPolicyTest,SessionStoreTest,JwtSignerPrefixTest,LoginFailureClassificationTest']
sys.argv[-1]+=',LearningEntitlementRegressionTest,OrderDeletionRegressionTest,OrderDeletionDatabaseTest,RefundInitialStateTest,SearchCompatibilityTest,CallDepthPropagationTest,UserInfoInterceptorDepthTest,InternalEntitlementControllerTest,GatewayAdmissionTest,LocalMediaPlaybackTest'
module_code=0
try:
 runpy.run_path(str(ACC/'run_tests.py'),run_name='__main__')
except SystemExit as result:
 module_code=result.code or 0
module_report=json.loads((LOCAL/'backend-result.json').read_text(encoding='utf8'))
folder=LOCAL/'backend-tests'/uuid.uuid4().hex;folder.mkdir(parents=True,exist_ok=False)
(folder/'modules.json').write_text(json.dumps(module_report,indent=2),encoding='utf8')
shutil.copyfile(LOCAL/'tests.log',folder/'modules.log')
transport=runpy.run_path(str(SOURCE/'run_transport_tests.py'),run_name='transport_acceptance')
transport_code=transport['main']()
transport_report=json.loads((LOCAL/'transport-result.json').read_text(encoding='utf8'))
combined={**module_report,'transportChecks':transport_report,
 'status':'PASSED' if module_code==0 and transport_code==0 and module_report['status']=='PASSED' else 'FAILED'}
for field in ('tests','failures','errors','skipped'):combined[field]+=transport_report[field]
for field in ('requestedClasses','executedClasses'):combined[field]+=transport_report[field]
(folder/'combined.json').write_text(json.dumps(combined,indent=2),encoding='utf8')
(LOCAL/'backend-result.json').write_text(json.dumps(combined,indent=2),encoding='utf8')
print('Complete backend and socket checks '+combined['status']+'; tests: '+str(combined['tests']))
raise SystemExit(0 if combined['status']=='PASSED' else 1)
