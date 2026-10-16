"""Read-only Gateway/real-DB pagination proof for a >100-history recovery clone."""
from pathlib import Path
import argparse,json,os,re,subprocess,sys
from datetime import datetime,timezone
from run_live_settlement import isolation,ROOT,SOURCE,TASK_OUTPUT,EXPECTED

def need(value,message):
    if not value:raise RuntimeError(message)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--home',type=Path,required=True);parser.add_argument('--project',required=True);parser.add_argument('--expected-source',default=EXPECTED);args=parser.parse_args()
    need(args.expected_source==EXPECTED,'Only the current final source is authorized for this probe')
    home=args.home.resolve();isolation(home,args.project);need(re.fullmatch('[0-9a-f]{40}',args.expected_source),'Exact committed source required')
    local=home/'.local';manifest=json.loads((local/'build-source.json').read_text(encoding='utf8'));need(manifest['sourceCommit']==args.expected_source,'Source manifest mismatch')
    os.environ.update(TJ_COMPACT_HOME=str(home),TJ_COMPACT_PROJECT=args.project,TJ_COMPACT_PORT_OFFSET='1000')
    sys.path.insert(0,str(SOURCE));import setup;import motivation_fixture as fixture
    need(setup.BASE==home and setup.PROJECT==args.project and setup.OFFSET==1000,'Runtime scope mismatch')
    for service,image in manifest['imageDigests'].items():
        container=subprocess.check_output(setup.COMPOSE+['ps','-q',service],text=True).strip();need(container and '\n' not in container,'Unique live container required')
        actual=json.loads(subprocess.check_output(['docker','inspect',container],text=True))[0];labels=actual['Config']['Labels']
        need(actual['Image']==image and labels['com.docker.compose.project']==args.project and Path(labels['com.docker.compose.project.working_dir']).resolve()==home,'Live clone image/ownership differs')
        need(actual['State']['Health']['Status']=='healthy','Live clone must be healthy')
    need(set(manifest['imageDigests'])=={'app-identity','app-commerce','app-education','app-support','app-gateway','web-student','web-admin'},'Complete final seven-image binding required')
    database=subprocess.check_output(setup.COMPOSE+['ps','-q','mysql'],text=True).strip();need(database and '\n' not in database,'Unique recovery MySQL required')
    inspected=json.loads(subprocess.check_output(['docker','inspect',database],text=True))[0];labels=inspected['Config']['Labels']
    need(labels.get('com.docker.compose.project')==args.project and labels.get('com.docker.compose.service')=='mysql' and Path(labels['com.docker.compose.project.working_dir']).resolve()==home,'Database ownership differs')
    ports=inspected['NetworkSettings']['Ports'].get('3306/tcp') or []
    need(len(ports)==1 and ports[0]['HostIp']=='127.0.0.1' and ports[0]['HostPort']=='25316','Database offset must be isolated loopback1000')
    need(inspected['State']['Health']['Status']=='healthy' and all(mount['Name'].startswith(args.project+'_') for mount in inspected['Mounts'] if mount['Type']=='volume'),'Database health/volume ownership differs')
    accounts=json.loads((local/'accounts.json').read_text(encoding='utf8'));user=fixture.identifier(accounts['student']['id']);session=fixture.login(accounts,'student')
    report={'status':'RUNNING','sourceCommit':args.expected_source,'project':args.project,'home':str(home),'databaseWrites':False,'startedAt':datetime.now(timezone.utc).isoformat()}
    path=TASK_OUTPUT/'live-learning-pagination-result.json'
    try:
        overview=fixture.gateway_call(session,'GET','/learning-hub/overview')
        need(overview.get('examReminderError') is None and overview.get('replyReminderError') is None,'Real reminder relay must succeed without degrading to unknown')
        reminders={key:overview.get(key) for key in ('pendingExams','waitingForGradingExams','unreadReplies')}
        need(all(type(value) is int and value>=0 for value in reminders.values()),'Real cross-service reminder counts must be nonnegative integers')
        report['learningReminders']=reminders
        expected=fixture.sql('tj_learning','SELECT id FROM learning_lesson WHERE user_id='+user+' ORDER BY latest_learn_time DESC,id DESC').splitlines();need(len(expected)>100,'Regression requires existing history of more than 100 lessons')
        def check(endpoint,size,identities):
            seen=[];pages=max(1,(len(identities)+size-1)//size)
            for page in range(1,pages+2):
                result=fixture.gateway_call(session,'GET',endpoint+f'?pageNo={page}&pageSize={size}')
                rows=result.get('list') or [];current=[str(row['id']) for row in rows]
                need(int(result['total'])==len(identities) and int(result['pages'])==(len(identities)+size-1)//size,'Real HTTP pagination totals differ from DB')
                need(current==identities[(page-1)*size:page*size] and len(current)<=size,'Real HTTP SQL page boundary/order differs from DB')
                seen.extend(current)
            need(seen==identities and len(set(seen))==len(seen),'Real HTTP pages duplicated or omitted history')
            return {'pageSize':size,'requests':pages+1,'total':len(identities),'uniqueHistoryMatched':True,'emptyBoundaryMatched':True}
        lesson_checks=[check('/services/learning/lessons/page',size,expected) for size in (20,100)]
        plans=fixture.sql('tj_learning','SELECT id FROM learning_lesson WHERE user_id='+user+' AND plan_status=1 AND status IN(0,1) ORDER BY latest_learn_time DESC,id DESC').splitlines()
        plan_checks=[check('/services/learning/lessons/plans',size,plans) for size in (20,100)]
        report.update(status='PASSED',lessons=lesson_checks,plans=plan_checks)
    except Exception as error:
        report.update(status='FAILED',failureType=type(error).__name__);raise
    finally:
        report['completedAt']=datetime.now(timezone.utc).isoformat();path.write_text(json.dumps(report,indent=2),encoding='utf8')
    print('Real Gateway learning pagination PASSED against independently scoped MySQL history')

if __name__=='__main__':main()
