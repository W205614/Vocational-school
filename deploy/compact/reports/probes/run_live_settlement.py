"""Opt-in live settlement proof. No containers are started; only a validated recovery clone is writable."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import time
import uuid
import zipfile
from datetime import datetime,timedelta,timezone
import yaml

PROBE_SOURCE=Path(__file__).resolve().parent;ROOT=PROBE_SOURCE.parents[3];SOURCE=ROOT/'deploy/compact'
TASK_OUTPUT=SOURCE/'.local/learning-upgrade-20261015'
EXPECTED='8dcf8e7d44c6048318ad1e306e1425c8fd40fb70'
SHANGHAI=timezone(timedelta(hours=8))

def require(value,message):
    if not value:raise RuntimeError(message)

def read(path):return json.loads(path.read_text(encoding='utf8'))

def coupon_fixture_form(run_id):
    require(re.fullmatch('[0-9a-f]{32}',run_id),'A fresh UUID hex identity is required for the coupon fixture')
    name='Settle '+run_id[:12]
    require(4<=len(name)<=20,'Coupon fixture name must satisfy the production DTO length contract')
    return dict(name=name,specific=False,discountType=3,thresholdAmount=0,discountValue=10,maxDiscountAmount=10,totalNum=2,userLimit=1,obtainWay=1)

def validate_browser(report,modified,started):
    require(report.get('status')=='PASSED' and all(report.get(key)==value for key,value in {'passed':42,'failed':0,'skipped':30}.items()),'Current final full browser requires 42 passed, 0 failed, 30 role skips')
    require(started and modified>max(started),'Browser proof must postdate the current seven containers starting')

def reward_quote(confirmation,ticket,coupon,name):
    """Require the exact newly earned ticket, never an automatic alternative."""
    require(not confirmation.get('couponSelectionRequired') and not confirmation.get('discountError'),'Explicit reward-only quote must be complete')
    choice=next((item for item in confirmation.get('availableCoupons',[]) if str(item['id'])==str(ticket) and str(item['couponId'])==str(coupon)),None)
    require(choice is not None and choice['name']==name and choice.get('termEndTime'),'Real student order confirmation cannot use the reward ticket')
    schemes=[item for item in confirmation.get('discounts',[]) if [str(v) for v in item.get('ids',[])]==[str(ticket)]]
    require(len(schemes)==1 and schemes[0]['discountAmount']==10,'Quote must apply exactly the new ten-cent reward ticket')
    require(len(confirmation.get('courses',[]))==1 and int(confirmation['totalAmount'])>10,'One paid course with a positive simulated payment required')
    return choice

def purchase_reward(fixture,learner,student,ticket,coupon,name,preferred,query,wait_for):
    """Only public gateway commands write purchase/payment; SQL below only observes."""
    call=lambda method,path,body=None,key=None:fixture.gateway_call(learner,method,path,body,key)
    require(call('GET','/environment').get('payment')=='SIMULATED','Only the explicit local payment simulator is authorized')
    require(any(item.get('channelCode')=='mockPay' for item in call('GET','/services/trade/pay/channels')),'Local simulated payment channel missing')
    preferred=fixture.identifier(preferred)
    course=fixture.identifier(query('tj_course',f"SELECT c.id FROM course c WHERE c.deleted=0 AND c.status=2 AND c.price>10 AND (c.purchase_end_time IS NULL OR c.purchase_end_time>NOW()) AND NOT EXISTS (SELECT 1 FROM tj_learning.learning_entitlement e WHERE e.user_id={student} AND e.course_id=c.id AND e.active=1 AND (e.expires_at IS NULL OR e.expires_at>NOW())) AND NOT EXISTS (SELECT 1 FROM tj_trade.order_detail d JOIN tj_trade.`order` o ON o.id=d.order_id WHERE d.user_id={student} AND d.course_id=c.id AND o.status=1) ORDER BY (c.id={preferred}) DESC,c.id LIMIT 1"))
    confirmation=call('GET',f'/orders/confirmation?courseIds={course}&couponIds={ticket}')
    choice=reward_quote(confirmation,ticket,coupon,name)
    require(str(confirmation['courses'][0]['id'])==course,'Quote returned a different course')
    expiry=query('tj_promotion',f"SELECT DATE_FORMAT(term_end_time,'%Y-%m-%d %H:%i:%s') FROM user_coupon WHERE id={ticket} AND user_id={student}")
    require(str(choice['termEndTime']).replace('T',' ')[:19]==expiry,'Real order confirmation expiry differs from the owned ticket')
    order=fixture.identifier(confirmation['orderId']);total=int(confirmation['totalAmount']);discount=10;paid=total-discount
    body=dict(orderId=order,courseIds=[course],couponIds=[str(ticket)]);key=str(uuid.uuid4())
    operation=call('POST','/orders',body,key)
    require(operation.get('operationId') and operation.get('status') in {'PENDING','SUCCEEDED'},'Order command was not accepted')
    repeated=call('POST','/orders',body,key)
    require(repeated.get('operationId')==operation['operationId'],'Purchase retry created a different operation')
    def created():
        value=call('GET','/operations/trade/'+operation['operationId'])
        require(value.get('status') in {'PENDING','SUCCEEDED'},'Reward purchase operation failed')
        return value['status']=='SUCCEEDED'
    wait_for(created,'Reward purchase operation did not finish')
    require(query('tj_trade',f"SELECT COUNT(*) FROM `order` WHERE id={order} AND user_id={student} AND status=1 AND total_amount={total} AND discount_amount={discount} AND real_amount={paid} AND JSON_LENGTH(coupon_ids)=1 AND JSON_UNQUOTE(JSON_EXTRACT(coupon_ids,'$[0]'))='{ticket}'")=='1','Persisted unpaid order amount or exact reward ticket differs')
    require(query('tj_promotion',f"SELECT COUNT(*) FROM coupon_reservation r JOIN user_coupon u ON u.reserved_order_id=r.order_id WHERE r.order_id={order} AND r.user_id={student} AND r.status='HELD' AND JSON_LENGTH(r.coupon_ids)=1 AND JSON_UNQUOTE(JSON_EXTRACT(r.coupon_ids,'$[0]'))='{ticket}' AND u.id={ticket} AND u.user_id={student} AND u.coupon_id={coupon} AND u.status=1")=='1','Exact earned ticket was not reserved by this order')
    require(call('POST','/services/trade/pay/order',dict(orderId=order,payChannelCode='mockPay'))=='/simulate-payment/'+order,'Payment request must return the owned local simulator path')
    simulated=call('POST','/simulator/payments/'+order+'/confirm')
    require(simulated.get('mode')=='SIMULATED' and simulated.get('status')=='PAID','Local simulator did not persist its success fact')
    wait_for(lambda:query('tj_trade',f"SELECT COUNT(*) FROM `order` o JOIN payment_fact p ON p.order_id=o.id AND p.pay_order_no=o.pay_order_no WHERE o.id={order} AND o.user_id={student} AND o.status=2 AND o.total_amount={total} AND o.discount_amount={discount} AND o.real_amount={paid} AND p.pay_channel='mockPay'")=='1','Trade did not persist one paid reward order and payment fact')
    detail=fixture.identifier(query('tj_trade',f'SELECT id FROM order_detail WHERE order_id={order} AND user_id={student} AND course_id={course}'))
    require(query('tj_trade',f'SELECT COUNT(*) FROM order_detail WHERE order_id={order} AND id={detail} AND status=2 AND price={total} AND discount_amount={discount} AND real_pay_amount={paid}')=='1','Order detail discount allocation was not persisted')
    wait_for(lambda:query('tj_trade',f"SELECT COUNT(*) FROM reliability_outbox WHERE business_key='order:{order}:paid' AND status='SENT'")=='1','Paid outbox event was not broker-confirmed')
    wait_for(lambda:query('tj_learning',f'SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id={detail} AND order_id={order} AND user_id={student} AND course_id={course} AND active=1 AND (expires_at IS NULL OR expires_at>NOW())')=='1','Paid reward purchase did not grant its actual learning entitlement')
    wait_for(lambda:query('tj_promotion',f"SELECT COUNT(*) FROM user_coupon u JOIN coupon c ON c.id=u.coupon_id JOIN coupon_reservation r ON r.order_id=u.reserved_order_id WHERE u.id={ticket} AND u.user_id={student} AND u.coupon_id={coupon} AND u.status=2 AND u.used_time IS NOT NULL AND u.reserved_order_id={order} AND r.status='CONFIRMED' AND c.issue_num=1 AND c.used_num=1")=='1','Paid event did not consume exactly the new reward ticket')
    require(query('tj_pay',f"SELECT COUNT(*) FROM pay_order p JOIN provider_payment_fact f ON f.pay_order_no=p.pay_order_no JOIN simulated_provider_payment s ON s.pay_order_no=p.pay_order_no WHERE p.biz_order_no={order} AND p.biz_user_id={student} AND p.pay_channel_code='mockPay' AND p.status=3 AND p.amount={paid} AND f.biz_order_no={order} AND f.amount={paid} AND s.amount={paid} AND s.status=3")=='1','Actual local provider and payment facts do not match the discounted amount')
    wait_for(lambda:query('tj_trade',f"SELECT COUNT(*) FROM reliability_inbox i JOIN tj_pay.reliability_outbox o ON CAST(o.event_id AS BINARY)=CAST(i.event_id AS BINARY) JOIN tj_pay.pay_order p ON o.business_key=CONCAT('provider:',p.pay_order_no,':paid') WHERE i.consumer_name='trade.payment' AND o.status='SENT' AND p.biz_order_no={order} AND p.biz_user_id={student}")=='1','Trade did not consume the actual local provider broker event')
    require(query('tj_learning',f"SELECT COUNT(*) FROM reliability_inbox i JOIN tj_trade.reliability_outbox o ON CAST(o.event_id AS BINARY)=CAST(i.event_id AS BINARY) WHERE i.consumer_name='lesson.grant' AND o.business_key='order:{order}:paid'")=='1','Learning grant did not consume the actual paid broker event')
    require(query('tj_promotion',f"SELECT COUNT(*) FROM reliability_inbox i JOIN tj_trade.reliability_outbox o ON CAST(o.event_id AS BINARY)=CAST(i.event_id AS BINARY) WHERE i.consumer_name='coupon.confirm' AND o.business_key='order:{order}:paid'")=='1','Coupon consumption did not consume the actual paid broker event')
    detail_http=call('GET','/orders/'+order)
    require(detail_http.get('status')==2 and int(detail_http['totalAmount'])==total and int(detail_http['realAmount'])==paid and float(detail_http['discountAmount'])==discount,'Real student order detail differs from persisted payment')
    require(len(detail_http.get('details',[]))==1 and any(str(d.get('id'))==detail and str(d.get('courseId'))==course and int(d['realPayAmount'])==paid for d in detail_http.get('details',[])),'Student cannot read the single paid discounted course detail')
    return dict(actualRewardPurchased=True,actualRewardUsed=True,paymentMode='LOCAL_SIMULATOR',orderId=order,orderDetailId=detail,courseId=course,userCouponId=str(ticket),couponId=str(coupon),totalAmount=total,discountAmount=discount,paidAmount=paid,
                httpChecks=['explicit one-ticket quote/name/expiry','idempotent async order','local mockPay confirmation','student paid order detail'],
                sqlChecks=['exact reserved ticket and persisted order/detail discount','simulated provider/payment facts','trade paid outbox SENT','actual broker inbox receipts for coupon and learning','reward ticket USED and stock usage once','active order-detail entitlement'])

def isolation(home,project):
    require(home.parent in {ROOT/'.local',SOURCE/'.local'} and re.fullmatch(r'(upgrade-rehearsal|final-upgrade)-[a-z0-9-]+',home.name),'Only explicitly named ignored recovery homes are allowed')
    require(re.fullmatch(r'tianji-recovery-[a-z0-9-]+',project),'Only separately named recovery projects allowed')
    require(home.is_dir() and (home/'compose.yaml').is_file(),'Existing recovery clone required')
    require(not home.is_symlink() and not any(p.is_symlink() for p in home.parents if p.is_relative_to(SOURCE)), 'Symlink recovery homes are forbidden')

def compile_probe(jar,work):
    libs=work/'lib';classes=work/'classes';libs.mkdir(parents=True,exist_ok=True);classes.mkdir(exist_ok=True)
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                require(Path(name).name==name.removeprefix('BOOT-INF/lib/'),'Unsafe nested dependency path')
                (libs/Path(name).name).write_bytes(archive.read(name))
            elif name.startswith('BOOT-INF/classes/') and not name.endswith('/'):
                relative=Path(name.removeprefix('BOOT-INF/classes/'));require('..' not in relative.parts,'Unsafe application class path')
                target=classes/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.read(name))
    with (work/'javac.log').open('wb') as log:
        result=subprocess.run([shutil.which('javac') or 'javac','--release','21','-cp',str(classes)+os.pathsep+str(libs/'*'),'-d',str(classes),str(PROBE_SOURCE/'LiveSettlementProbe.java')],stdout=log,stderr=subprocess.STDOUT)
    require(result.returncode==0,'Ignored Java probe compilation failed; private javac log retained')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--home',type=Path);parser.add_argument('--project');parser.add_argument('--compile-only',type=Path)
    args=parser.parse_args()
    TASK_OUTPUT.mkdir(parents=True,exist_ok=True)
    if args.compile_only:
        work=TASK_OUTPUT/'live-settlement-compile';compile_probe(args.compile_only.resolve(),work);print('Ignored settlement probe compiled; no runtime exercised');return
    require(args.home is not None and args.project,'Explicit recovery home and project required')
    home=args.home.resolve();project=args.project;isolation(home,project)
    local=home/'.local';report_path=TASK_OUTPUT/'live-settlement-result.json'
    report={'status':'RUNNING','sourceCommit':EXPECTED,'project':project,'home':str(home),'offset':1000,'startedAt':datetime.now(timezone.utc).isoformat(),
            'syntheticSeeds':['unoccupied future month reward rule only'],'productionExecution':['points.award','season.settle','application outbox dispatch','actual broker','promotion stock/ticket','learning ACK','explicit reward-ticket quote','real order creation','local simulated payment','coupon consumption','paid order entitlement'],
            'hostClockChanged':False,'snapshotRequestAckSeeded':False,'actualRewardPurchased':False}
    report_path.write_text(json.dumps(report,indent=2),encoding='utf8')
    commerce_stopped=False;command=None;work=None
    try:
        manifest=read(local/'build-source.json');require(manifest['sourceCommit']==EXPECTED,'Probe requires the final exact source manifest')
        recovery=read(local/'recovery-result.json');require(recovery['rawRestoreProof']['status']=='PASSED','Raw recovery byte/content proof must already pass')
        report.update(rawRecoverySource=recovery['rawRestoreProof']['sourceCommit'],rawRecoveryStatus='PASSED',previousRecoveryOverallStatus=recovery['status'],executedSource=EXPECTED)
        browser=read(local/'browser-result.json')
        compose=yaml.safe_load((home/'compose.yaml').read_text(encoding='utf8'))
        require(compose.get('name')==project,'Compose ownership differs')
        require('127.0.0.1:25316:3306' in compose['services']['mysql']['ports'],'Recovery database must have exact loopback offset1000')
        command=['docker','compose','-p',project,'-f',str(home/'compose.yaml'),'--env-file',str(home/'.env')]
        def capture(argv):return subprocess.check_output(argv,stderr=subprocess.DEVNULL).decode().strip()
        containers={};app_started=[]
        for service,definition in compose['services'].items():
            container=capture(command+['ps','-q',service]);require(container and '\n' not in container,'Exactly one live container per service required')
            inspected=json.loads(capture(['docker','inspect',container]))[0];labels=inspected['Config'].get('Labels',{})
            require(labels.get('com.docker.compose.project')==project and labels.get('com.docker.compose.service')==service,'Live container ownership differs')
            require(Path(labels.get('com.docker.compose.project.working_dir','')).resolve()==home,'Live project home differs')
            require(inspected['State'].get('Health',{}).get('Status')=='healthy','Every recovery service must be healthy')
            if service in manifest['imageDigests']:
                require(inspected['Image']==manifest['imageDigests'][service] and definition['image']==inspected['Image'],'Live image/source binding differs')
                app_started.append(datetime.fromisoformat(inspected['State']['StartedAt'].replace('Z','+00:00')).timestamp())
            require(all(network.startswith(project+'_') for network in inspected['NetworkSettings']['Networks']),'Recovery container joined a shared network')
            for mount in inspected['Mounts']:
                if mount['Type']=='volume':require(mount['Name'].startswith(project+'_'),'Recovery container uses a foreign volume')
                elif mount['Type']=='bind':require(Path(mount['Source']).resolve().is_relative_to(home),'Recovery bind mount escapes its home')
            containers[service]=container
        validate_browser(browser,(local/'browser-result.json').stat().st_mtime,app_started)
        require(set(manifest['imageDigests'])=={'app-identity','app-commerce','app-education','app-support','app-gateway','web-student','web-admin'},'Complete seven-image manifest required')
        os.environ.update(TJ_COMPACT_HOME=str(home),TJ_COMPACT_PROJECT=project,TJ_COMPACT_PORT_OFFSET='1000',TJ_MOTIVATION_FIXTURE='1')
        sys.path.insert(0,str(SOURCE));import setup;import motivation_fixture as fixture
        require(setup.BASE==home and setup.PROJECT==project and setup.OFFSET==1000,'Imported runtime scope differs')
        accounts=read(local/'accounts.json');original=read(local/'browser-fixture.json')
        require((local/'motivation-fixture.json').exists(),'Authorized advanced fixture must exist before the probe')
        student,teacher,admin=[int(fixture.identifier(accounts[role]['id'])) for role in ('student','teacher','admin')]
        # Only the running image supplies the probe's production classes/dependencies.
        run_id=uuid.uuid4().hex;work=TASK_OUTPUT/('live-settlement-'+run_id);work.mkdir()
        jar=work/'education.jar';subprocess.run(['docker','cp',containers['app-education']+':/app/app.jar',str(jar)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        images=read(local/'images.json');require(hashlib.sha256(jar.read_bytes()).hexdigest()==images['education']['jarSha256'],'Extracted deployed JAR differs from frozen manifest')
        compile_probe(jar,work)
        remote='/tmp/live-settlement-'+run_id
        subprocess.run(['docker','exec',containers['app-education'],'mkdir','-p',remote],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        for folder in ('classes','lib'):
            subprocess.run(['docker','cp',str(work/folder),containers['app-education']+':'+remote+'/'+folder],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        db=yaml.safe_load((local/'configs/learning.yml').read_text(encoding='utf8'))['spring']['datasource']
        require(db['username']=='app_learning' and db['url'].startswith('jdbc:mysql://mysql:3306/tj_learning?'),'Learning DB credentials are not clone-local')
        # Read-only selection. No historical rows are replaced or removed.
        month=None;year=datetime.now(SHANGHAI).year+5
        for n in range(1,13):
            candidate=f'{year:04d}{n:02d}'
            occupied=fixture.sql('tj_learning',"SELECT (SELECT COUNT(*) FROM learning_season WHERE board_month='"+candidate+"')+(SELECT COUNT(*) FROM points_projection WHERE board_month='"+candidate+"')+(SELECT COUNT(*) FROM learning_reward_rule WHERE board_month='"+candidate+"')+(SELECT COUNT(*) FROM learning_reward_request WHERE board_month='"+candidate+"')")
            if occupied=='0':month=candidate;break
        require(month,'No unoccupied future month available')
        admin_session=fixture.login(accounts,'admin');learner=fixture.login(accounts,'student');coupon_form=coupon_fixture_form(run_id);name=coupon_form['name']
        fixture.gateway_call(admin_session,'POST','/admin/promotion/coupons',coupon_form,str(uuid.uuid4()))
        listed=fixture.gateway_call(admin_session,'GET','/admin/promotion/coupons/page?name='+__import__('urllib.parse',fromlist=['quote']).quote(name)+'&pageNo=1&pageSize=20')['list']
        require(len(listed)==1 and listed[0]['name']==name,'Unique newly created coupon required');coupon=int(fixture.identifier(listed[0]['id']))
        fixture.gateway_call(admin_session,'PUT',f'/admin/promotion/coupons/{coupon}/issue',dict(id=coupon,issueEndTime=(datetime.now(SHANGHAI)+timedelta(days=2)).replace(tzinfo=None).isoformat(timespec='seconds'),termDays=30),str(uuid.uuid4()))
        require(fixture.sql('tj_promotion',f'SELECT issue_num FROM coupon WHERE id={coupon}')=='0','New coupon already has deliveries')
        rule=850000000000000000+int(time.time()*1000)*1000+secrets.randbelow(500)
        data=dict(project=project,offset='1000',month=month,student=student,teacher=teacher,admin=admin,coupon=coupon,rule=rule,runId=run_id,dbPassword=db['password'])
        def java(action,**extra):
            with (work/(action+'.log')).open('wb') as log:
                result=subprocess.run(['docker','exec','-i',containers['app-education'],'java','-Xms16m','-Xmx160m','-cp',remote+'/classes:'+remote+'/lib/*','LiveSettlementProbe'],input=json.dumps(dict(data,action=action,**extra)).encode(),stdout=log,stderr=subprocess.STDOUT)
            text=(work/(action+'.log')).read_text(encoding='utf8');matches=[line.split('=',1)[1] for line in text.splitlines() if line.startswith('LIVE_SETTLEMENT_RESULT=')]
            require(result.returncode==0 and len(matches)==1,'Production Java settlement probe failed; private log retained')
            value=json.loads(matches[0]);require(value['status']=='PASSED','Java probe did not pass');return value
        prepared=java('prepare')
        report['prepared']=prepared
        def service(action):
            with (work/('commerce-'+action+'.log')).open('wb') as log:
                operation=command+(['stop','-t','30','app-commerce'] if action=='stop' else ['start','--wait','--wait-timeout','180','app-commerce'])
                result=subprocess.run(operation,stdout=log,stderr=subprocess.STDOUT)
            require(result.returncode==0,'Owned clone commerce '+action+' failed; private log retained')
        # Stop only the previously verified service in this separately owned clone.
        commerce_stopped=True;service('stop')
        settlement=java('settle');request=settlement['requestId'];require(re.fullmatch(r'[0-9a-f-]{36}',request),'Invalid generated request identity')
        require(settlement['examHttpCalls']>=2 and settlement['userHttpCalls']>=1,'Real remote dependency calls not evidenced')
        report.update(month=month,ruleId=str(rule),couponId=str(coupon),requestId=request,settlement=settlement)
        def query(database,sql):
            require(sql.startswith('SELECT '),'Probe SQL observations must be read-only')
            if database in {'tj_trade','tj_pay'}:return setup.mysql('USE `'+database+'`;\n'+sql)
            return fixture.sql(database,sql)
        def wait_for(predicate,message):
            deadline=time.monotonic()+90
            while time.monotonic()<deadline:
                if predicate():return
                time.sleep(.5)
            raise RuntimeError(message)
        def reward_queue_messages():
            text=capture(command+['exec','-T','rabbitmq','rabbitmqctl','list_queues','-q','name','messages'])
            rows=[line.split() for line in text.splitlines() if line.split() and line.split()[0]=='season.reward.delivery']
            require(len(rows)==1 and len(rows[0])==2,'Dedicated durable reward queue missing')
            return int(rows[0][1])
        escaped=fixture.literal(request)
        first=fixture.literal('season-reward:'+request+':1')
        wait_for(lambda:query('tj_learning','SELECT status FROM reliability_outbox WHERE business_key='+first)=='SENT','Real settlement outbox was not broker-confirmed during consumer outage')
        require(query('tj_learning','SELECT status FROM learning_reward_request WHERE request_id='+escaped)=='PENDING','Consumer outage must leave the actual request pending')
        require(query('tj_promotion',f'SELECT COUNT(*) FROM user_coupon WHERE user_id={student} AND coupon_id={coupon}')=='0','Consumer outage unexpectedly issued a coupon')
        require(query('tj_promotion',f'SELECT issue_num FROM coupon WHERE id={coupon}')=='0','Consumer outage unexpectedly debited stock')
        require(reward_queue_messages()>=1,'Confirmed reward was not retained in durable broker queue')
        report['consumerOutage']={'service':'app-commerce','requestPending':True,'outboxSent':True,'durableBrokerQueued':True,'stockDebited':False,'tickets':0}
        service('start');commerce_stopped=False
        wait_for(lambda:query('tj_learning','SELECT status FROM learning_reward_request WHERE request_id='+escaped)=='FULFILLED','Real broker/promotion/learning ACK did not fulfill settlement after recovery')
        ticket=int(fixture.identifier(query('tj_learning','SELECT user_coupon_id FROM learning_reward_request WHERE request_id='+escaped)))
        def actual_reward():
            return query('tj_promotion',"SELECT COUNT(*) FROM season_reward_delivery d JOIN user_coupon u ON u.id=d.user_coupon_id JOIN coupon c ON c.id=u.coupon_id WHERE d.request_id="+escaped+f" AND d.status='SUCCEEDED' AND d.user_id={student} AND d.coupon_id={coupon} AND d.rule_id={rule} AND u.id={ticket} AND u.user_id={student} AND u.coupon_id={coupon} AND c.issue_num=1 AND u.status=1 AND u.term_begin_time<=NOW() AND u.term_end_time>NOW()")=='1'
        require(actual_reward(),'Real stock/ticket/time/ownership proof failed')
        replay=java('replay',requestId=request,snapshotSha256=settlement['snapshotSha256'])
        # The second production outbox event must be sent and reached the successful receipt consumer without another stock debit.
        second=fixture.literal('season-reward:'+request+':2')
        wait_for(lambda:query('tj_learning','SELECT status FROM reliability_outbox WHERE business_key='+second)=='SENT','Production duplicate event was not dispatched')
        # Rabbit queue drain + a stable observation interval establish processing of the duplicate success path.
        wait_for(lambda:reward_queue_messages()==0,'Reward queue did not drain')
        time.sleep(1)
        require(actual_reward(),'Business replay duplicated or invalidated the actual reward')
        require(query('tj_promotion',f'SELECT COUNT(*) FROM user_coupon WHERE user_id={student} AND coupon_id={coupon}')=='1','Business replay created duplicate tickets')
        require(query('tj_learning',"SELECT COUNT(*) FROM learning_reward_request WHERE board_month='"+month+"'")=='1','Settlement replay created duplicate requests')
        require(query('tj_learning',"SELECT COUNT(*) FROM reliability_inbox i JOIN tj_promotion.reliability_outbox o ON CAST(i.event_id AS BINARY)=CAST(o.event_id AS BINARY) WHERE i.consumer_name='learning.reward.fulfilled' AND JSON_UNQUOTE(JSON_EXTRACT(o.payload,'$.payload.requestId'))="+escaped)=='1','Actual promotion ACK inbox receipt missing')
        purchase=purchase_reward(fixture,learner,student,ticket,coupon,name,original['course'],query,wait_for)
        report.update(actualRewardPurchased=True,rewardPurchase=purchase)
        report.update(status='PASSED',replay=replay,stockDebitedOnce=True,ticketCount=1,actualCouponAvailable=True,userCouponId=str(ticket),brokerAckObserved=True,
                      imageId=manifest['imageDigests']['app-education'],jarSha256=images['education']['jarSha256'],probeSha256=hashlib.sha256((PROBE_SOURCE/'LiveSettlementProbe.java').read_bytes()).hexdigest())
    except Exception as error:
        report.update(status='FAILED',failureType=type(error).__name__)
        raise
    finally:
        if commerce_stopped and command is not None:
            # No DB rollback and no service outside the verified clone is touched.
            try:
                with (work/'commerce-finally-start.log').open('wb') as log:
                    restarted=subprocess.run(command+['start','--wait','--wait-timeout','180','app-commerce'],stdout=log,stderr=subprocess.STDOUT)
                report['commerceRestoredInFinally']=restarted.returncode==0
                if restarted.returncode:report.update(status='FAILED',serviceRestoreFailed=True)
            except Exception:
                report.update(status='FAILED',serviceRestoreFailed=True)
        report['completedAt']=datetime.now(timezone.utc).isoformat();report_path.write_text(json.dumps(report,indent=2),encoding='utf8')
    require(report['status']=='PASSED','Probe or isolated-service restoration failed')
    print('Live production settlement PASSED on the independently owned recovery clone')

if __name__=='__main__':main()
