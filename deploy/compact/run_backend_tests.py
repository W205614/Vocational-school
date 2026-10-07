import os,runpy,sys
from setup import configure_acceptance,BASE,ACC,OFFSET,secrets_config
configure_acceptance()
os.environ.update(TJ_ENV_FILE=str(BASE/'.env'),ACCEPTANCE_DB_PORT=str(24316+OFFSET),ACCEPTANCE_REDIS_PORT=str(24379+OFFSET),ACCEPTANCE_MQ_PORT=str(24373+OFFSET),ACCEPTANCE_MQ_USERNAME='tianji',ACCEPTANCE_ES_URL='http://127.0.0.1:'+str(24920+OFFSET))
# Initialize dedicated schemas only. Application and historical records are never reset by tests.
runpy.run_path(str(ACC/'initialize_test_schemas.py'),run_name='__main__')
sys.argv=[str(ACC/'run_tests.py'),'--modules','all','--tests','PageQueryTest,ReliabilityDatabaseTest,ResponseConverterTest,CookieBuilderTest,AcceptanceFaultsTest,LearningReliabilityTest,DelayTaskTest,ExamReliabilityTest,ExamDraftReliabilityTest,ObjectiveScoringTest,DiscountServiceTest,ProviderSettlementTest,CourseRepositoryBulkTest,FeignBulkheadTest,ReliabilityMetricsTest,AsyncSmsClientReliabilityTest,VerificationCodeReliabilityTest,LikeConcurrencyReliabilityTest,CourseDeadlineTest,CourseTeacherDraftTest,LocalCourseCoverStoreTest,AccessPolicyTest,SessionStoreTest,JwtSignerPrefixTest,LoginFailureClassificationTest']
runpy.run_path(str(ACC/'run_tests.py'),run_name='__main__')
