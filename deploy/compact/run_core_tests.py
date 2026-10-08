"""Exercise core fixes in dedicated test schemas without resetting application data."""
import os
import runpy
import sys
from setup import configure_acceptance, BASE, ACC, OFFSET

configure_acceptance()
os.environ.update(TJ_ENV_FILE=str(BASE/'.env'), ACCEPTANCE_DB_PORT=str(24316+OFFSET),
                  ACCEPTANCE_REDIS_PORT=str(24379+OFFSET), ACCEPTANCE_MQ_PORT=str(24373+OFFSET),
                  ACCEPTANCE_MQ_USERNAME='tianji', ACCEPTANCE_ES_URL='http://127.0.0.1:'+str(24920+OFFSET))
runpy.run_path(str(ACC/'initialize_test_schemas.py'), run_name='__main__')
sys.argv=[str(ACC/'run_tests.py'), '--modules', 'all', '--tests',
          'LearningEntitlementRegressionTest,OrderDeletionRegressionTest,OrderDeletionDatabaseTest,'
          'RefundInitialStateTest,ExamReliabilityTest,ExamDraftReliabilityTest,LearningReliabilityTest,'
          'FeignBulkheadTest,CallDepthPropagationTest,UserInfoInterceptorDepthTest,InternalEntitlementControllerTest,GatewayAdmissionTest,LocalMediaPlaybackTest']
runpy.run_path(str(ACC/'run_tests.py'), run_name='__main__')
