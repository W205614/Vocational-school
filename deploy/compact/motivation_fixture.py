"""Advanced motivation fixtures for an explicitly disposable compact project.

Seeded: a dedicated synthetic course/enrollment, catalogue/question-bank rows,
teacher assignment, a read-only
legacy rank for presentation, and one PENDING reward request/outbox event.
Executed by the current application: exam publication, failed/correct submissions,
learning completion/first-exam badge, discussion creation, coupon creation/issue,
reward stock debit, ticket creation and acknowledgement consumption.

This is an event replay integration scenario, not proof of natural month-end
settlement. Clock-controlled database tests verify natural settlement separately.
No successful delivery, earned badge, or user coupon is written by this script.
"""
from datetime import datetime, timedelta, timezone
import json
import os
import re
import secrets
import time
from urllib.parse import quote
import uuid

import requests
import setup


SHANGHAI = timezone(timedelta(hours=8))


def require_isolated():
    explicit_clone = os.environ.get('TJ_MOTIVATION_FIXTURE') == '1' and setup.PROJECT != 'tianji-compact'
    ephemeral_ci = os.environ.get('GITHUB_ACTIONS') == 'true'
    if not (explicit_clone or ephemeral_ci):
        raise RuntimeError('Motivation fixtures require TJ_MOTIVATION_FIXTURE=1 and a non-primary project, or ephemeral GitHub Actions')


def identifier(value):
    result = str(value)
    if not re.fullmatch(r'[1-9][0-9]{0,18}', result) or int(result) > 9223372036854775807:
        raise ValueError('Invalid fixture identifier')
    return result


def literal(value):
    """A UTF-8 SQL literal without shell interpolation or backslash assumptions."""
    # A character-set introducer keeps this a literal (coercibility 4). CONVERT
    # creates a coercibility-2 expression, which conflicts with legacy columns
    # when their utf8mb4 collation differs from the current connection default.
    return "_utf8mb4 X'" + str(value).encode('utf8').hex() + "'"


def sql(database, statement):
    if database not in {'tj_course', 'tj_exam', 'tj_learning', 'tj_promotion'}:
        raise ValueError('Unexpected fixture schema')
    return setup.mysql('USE `' + database + '`;\n' + statement)


def reward_diagnostics(request_id, ticket, student, coupon):
    """Only predicate flags and time metadata; never identities or credentials."""
    statement = "SELECT JSON_OBJECT('receiptSucceeded',d.status='SUCCEEDED','ticketMatches',u.id=" + identifier(ticket)
    statement += ",'ownerMatches',u.user_id=" + identifier(student) + ",'couponMatches',u.coupon_id=" + identifier(coupon)
    statement += ",'stockMatches',c.issue_num=1,'unused',u.status=1,'started',u.term_begin_time<=NOW(),'unexpired',u.term_end_time>NOW(),"
    statement += "'validFrom',u.term_begin_time,'validUntil',u.term_end_time,'serverNow',NOW(6),'shanghaiNow',UTC_TIMESTAMP(6)+INTERVAL 8 HOUR,'sessionTimeZone',@@session.time_zone) "
    statement += 'FROM season_reward_delivery d LEFT JOIN user_coupon u ON u.id=d.user_coupon_id LEFT JOIN coupon c ON c.id=u.coupon_id WHERE d.request_id=' + literal(request_id) + ' LIMIT 1'
    return sql('tj_promotion', statement) or '{"receiptMissing":true}'


def gateway_call(session, method, endpoint, body=None, key=None):
    # Derive the endpoint from this compact project's configured port, never an
    # arbitrary external base URL supplied by another tool or the fixture file.
    url = 'http://127.0.0.1:' + str(setup.PORTS['gateway']) + '/api/v2' + endpoint
    response = session.request(method, url, json=body,
                               headers={'Idempotency-Key': key} if key else {}, timeout=20)
    try:
        envelope = response.json()
    except ValueError as error:
        raise RuntimeError('Fixture API returned non-JSON: ' + endpoint) from error
    if not response.ok or envelope.get('code') != 200:
        raise RuntimeError('Fixture API rejected ' + method + ' ' + endpoint + ': HTTP ' + str(response.status_code) + ', code ' + str(envelope.get('code')))
    return envelope.get('data')


def login(accounts, role):
    session = requests.Session()
    account = accounts[role]
    token = gateway_call(session, 'POST', '/auth/accounts/' + ('login' if role == 'student' else 'admin/login'),
                         dict(type=1, username=account['username'], password=account['password'], rememberMe=False))
    session.headers['Authorization'] = 'Bearer ' + token
    return session


def command(session, service, method, endpoint, body):
    operation = gateway_call(session, method, endpoint, body, str(uuid.uuid4()))
    deadline = time.monotonic() + 60
    while operation['status'] == 'PENDING' and time.monotonic() < deadline:
        time.sleep(.25)
        operation = gateway_call(session, 'GET', '/operations/' + service + '/' + operation['operationId'])
    if operation['status'] != 'SUCCEEDED':
        raise RuntimeError('Fixture command did not succeed: ' + endpoint + ', status ' + operation['status'] + ', code ' + str(operation.get('errorCode')))
    return operation['result']


def previous_month(value):
    return (value.replace(day=1) - timedelta(days=1)).replace(day=1)


def create_legacy_read_case(student):
    """Use an otherwise unoccupied month; never overwrite restored history."""
    month = previous_month(datetime.now(SHANGHAI))
    for _ in range(240):
        key = month.strftime('%Y%m')
        occupied = sql('tj_learning', "SELECT (SELECT COUNT(*) FROM learning_season WHERE board_month='" + key + "')+(SELECT COUNT(*) FROM points_projection WHERE board_month='" + key + "')+(SELECT COUNT(*) FROM learning_reward_request WHERE board_month='" + key + "')")
        if occupied == '0':
            break
        month = previous_month(month)
    else:
        raise RuntimeError('No unoccupied month for the isolated legacy presentation fixture')
    following = (month.replace(day=28) + timedelta(days=4)).replace(day=1)
    name = '合成历史界面 ' + str(month.year) + '年' + str(month.month) + '月'
    sql('tj_learning', "START TRANSACTION;INSERT INTO learning_season(board_month,name,state,freeze_at,settled_at) VALUES('" + key + "'," + literal(name) + ",'LEGACY_READONLY','" + (following + timedelta(days=1)).strftime('%Y-%m-%d 00:00:00') + "',CURRENT_TIMESTAMP(3));"
        "INSERT INTO learning_season_rank(board_month,user_id,rank_no,points,reached_at,display_name) VALUES('" + key + "'," + student + ",1,37,'" + month.strftime('%Y-%m-%d 12:00:00') + "'," + literal('合成学习者') + ");COMMIT;")
    return key, name


def enqueue_reward_request(student, coupon, rule, month):
    request_id = str(uuid.uuid4())
    event_id = str(uuid.uuid4())
    business = 'season-reward:' + request_id + ':1'
    routing = 'learning.reward.requested'
    payload = dict(requestId=request_id, userId=int(student), couponId=int(coupon), seasonMonth=month, ruleId=int(rule))
    envelope = dict(eventId=event_id, businessKey=business, eventType=routing, schemaVersion=1,
                    occurredAt=datetime.now(timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z'), payload=payload)
    # This is the only synthetic producer fact. No receipt, ticket, stock counter
    # or FULFILLED value is populated. Production consumers must do that work.
    sql('tj_learning', "START TRANSACTION;INSERT INTO learning_reward_request(request_id,board_month,user_id,rank_no,rule_id,coupon_id,status) VALUES(" + literal(request_id) + ",'" + month + "'," + student + ',1,' + rule + ',' + coupon + ",'PENDING');"
        "INSERT INTO reliability_outbox(event_id,business_key,exchange_name,routing_key,event_type,schema_version,payload,delay_ms,status,next_attempt_at) VALUES(" + literal(event_id) + ',' + literal(business) + ",'learning.topic','" + routing + "','" + routing + "',1," + literal(json.dumps(envelope, ensure_ascii=False, separators=(',', ':'))) + ",0,'PENDING',CURRENT_TIMESTAMP(3));COMMIT;")
    return request_id, event_id


def main():
    require_isolated()
    fixture_path = setup.LOCAL / 'motivation-fixture.json'
    # A failed preparation must never leave an earlier success manifest for the
    # browser runner to mistake for the current run's evidence.
    if fixture_path.exists():
        fixture_path.unlink()
    original = json.loads((setup.LOCAL / 'browser-fixture.json').read_text(encoding='utf8'))
    accounts = json.loads((setup.LOCAL / 'accounts.json').read_text(encoding='utf8'))
    student, admin_id, teacher = [identifier(accounts[role]['id']) for role in ['student', 'admin', 'teacher']]
    template_course = identifier(original['notesCourse'])
    base = 850000000000000000 + int(time.time() * 1000) * 1000 + secrets.randbelow(500)
    chapter, section, question, assignment, replay_rule = [identifier(base + n) for n in range(1, 6)]
    course, lesson = identifier(base + 7), identifier(base + 8)
    marker = uuid.uuid4().hex[:12]
    review_name = 'Synthetic motivation question ' + marker
    question_title = 'Synthetic teacher discussion ' + marker
    name = 'Synthetic motivation course ' + marker
    # The shared notesCourse deliberately has no published exam. Keep it intact
    # for its own historical-access tests; all advanced data gets a new identity.
    columns = [line.split('\t')[0] for line in sql('tj_course', 'SHOW COLUMNS FROM course').splitlines()]
    replacements = {'id': course, 'name': literal(name), 'status': '2', 'free': '0', 'price': '100',
                    'section_num': '2', 'media_duration': '0', 'creater': admin_id, 'updater': admin_id,
                    'deleted': '0', 'purchase_start_time': 'NOW()-INTERVAL 1 DAY',
                    'purchase_end_time': 'NOW()+INTERVAL 1 YEAR', 'valid_duration': '12'}
    fields = ','.join('`' + column + '`' for column in columns)
    values = ','.join(replacements.get(column, '`' + column + '`') for column in columns)
    sql('tj_course', 'INSERT INTO course(' + fields + ') SELECT ' + values + ' FROM course WHERE id=' + template_course)
    if sql('tj_course', 'SELECT COUNT(*) FROM course WHERE id=' + course) != '1':
        raise RuntimeError('The isolated synthetic course template was unavailable')
    sql('tj_learning', 'START TRANSACTION;INSERT INTO learning_lesson(id,user_id,course_id,expire_time,status,learned_sections) VALUES(' + lesson + ',' + student + ',' + course + ',NOW()+INTERVAL 1 YEAR,0,0);'
        'INSERT INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active,expires_at) VALUES(' + lesson + ',' + lesson + ',' + student + ',' + course + ',1,NOW()+INTERVAL 1 YEAR);COMMIT;')
    sql('tj_course', 'START TRANSACTION;INSERT INTO course_teacher(id,course_id,teacher_id,is_show,c_index,dep_id,create_time,update_time,creater,updater,deleted) VALUES(' + assignment + ',' + course + ',' + teacher + ',1,1,0,NOW(),NOW(),' + admin_id + ',' + admin_id + ',0);'
        'INSERT INTO course_catalogue(id,name,course_id,type,parent_catalogue_id,media_duration,c_index,dep_id,creater,updater) VALUES(' + chapter + ',' + literal('Synthetic motivation chapter') + ',' + course + ',1,0,0,1,0,' + admin_id + ',' + admin_id + '),(' + section + ',' + literal('Synthetic motivation exam') + ',' + course + ',3,' + chapter + ',0,1,0,' + admin_id + ',' + admin_id + ');COMMIT;')
    sql('tj_exam', 'START TRANSACTION;INSERT INTO question(id,name,type,cate_id1,cate_id2,cate_id3,difficulty,score,creater,updater) VALUES(' + question + ',' + literal(review_name) + ',2,1,2,3,1,10,' + admin_id + ',' + admin_id + ');'
        'INSERT INTO question_detail(id,options,answer,analysis) VALUES(' + question + ',' + literal('["A","B","C"]') + ',' + literal('1,2') + ',' + literal('Motivation snapshot analysis') + ');INSERT INTO question_biz(biz_id,question_id) VALUES(' + section + ',' + question + ');COMMIT;')
    sessions = []
    try:
        admin = login(accounts, 'admin');sessions.append(admin)
        learner = login(accounts, 'student');sessions.append(learner)
        publication = command(admin, 'exam', 'POST', '/admin/exam-papers', dict(courseId=course, sectionId=section, questionIds=[question], passPercent=60, graders=[teacher]))
        paper = identifier(publication['id'])
        wrong = command(learner, 'exam', 'POST', '/exam-papers/' + paper + '/attempts', {})
        wrong_id = identifier(wrong['id'])
        finished = command(learner, 'exam', 'POST', '/exam-attempts/' + wrong_id + '/submit', dict(answers={question: '3'}))
        if finished['status'] != 'FINISHED' or int(finished['score']) != 0:
            raise RuntimeError('The real failed submission did not produce a graded mistake')
        passed = command(learner, 'exam', 'POST', '/exam-papers/' + paper + '/attempts', {})
        passed_id = identifier(passed['id'])
        passed = command(learner, 'exam', 'POST', '/exam-attempts/' + passed_id + '/submit', dict(answers={question: '1,2'}))
        if passed['status'] != 'FINISHED' or not passed['passed']:
            raise RuntimeError('The real correct submission did not pass')
        passed_event = sql('tj_exam', 'SELECT event_id FROM reliability_outbox WHERE business_key=' + literal('exam:' + passed_id + ':passed'))
        if not passed_event:
            raise RuntimeError('The successful exam did not persist its producer event')
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            badges = gateway_call(learner, 'GET', '/learning-hub/achievements')['list']
            consumed = sql('tj_learning', 'SELECT COUNT(*) FROM learning_milestone m WHERE m.user_id=' + student + ' AND m.course_id=' + course + ' AND m.section_id=' + section + " AND m.kind='EXAM' AND EXISTS(SELECT 1 FROM reliability_inbox i WHERE i.consumer_name='learning.exam' AND i.event_id=" + literal(passed_event) + ')')
            if consumed == '1' and any(badge['name'] == '首次通过考试' for badge in badges):
                break
            time.sleep(.5)
        else:
            raise RuntimeError('The exam completion consumer did not persist its first-pass badge')
        command(learner, 'learning', 'POST', '/services/learning/questions', dict(courseId=course, chapterId=chapter, sectionId=section, title=question_title, description='Synthetic discussion for the isolated teacher browser scenario', anonymity=False))
        questions = gateway_call(learner, 'GET', '/services/learning/questions/page?courseId=' + course + '&pageNo=1&pageSize=100')['list']
        discussion = next((row for row in questions if row['title'] == question_title), None)
        if not discussion:
            raise RuntimeError('The real discussion command did not persist the fixture question')
        unassigned = sql('tj_course', 'SELECT id FROM course c WHERE c.deleted=0 AND c.id<>' + course + ' AND NOT EXISTS(SELECT 1 FROM course_teacher t WHERE t.course_id=c.id AND t.teacher_id=' + teacher + ' AND t.deleted=0) ORDER BY c.id LIMIT 1')
        unassigned = identifier(unassigned)
        # A retired assignment must grant neither discovery nor teaching access.
        # Insert only this synthetic retired row; preserve every active mapping.
        sql('tj_course', 'INSERT INTO course_teacher(id,course_id,teacher_id,is_show,c_index,dep_id,create_time,update_time,creater,updater,deleted) VALUES(' + identifier(base + 6) + ',' + unassigned + ',' + teacher + ',1,1,0,NOW(),NOW(),' + admin_id + ',' + admin_id + ',1)')
        history_month, history_name = create_legacy_read_case(student)
        coupon_name = 'Syn ' + marker
        gateway_call(admin, 'POST', '/admin/promotion/coupons', dict(name=coupon_name, specific=False, discountType=3, thresholdAmount=0, discountValue=10, maxDiscountAmount=10, totalNum=2, userLimit=1, obtainWay=1), str(uuid.uuid4()))
        listed = gateway_call(admin, 'GET', '/admin/promotion/coupons/page?name=' + quote(coupon_name) + '&pageNo=1&pageSize=20')['list']
        if len(listed) != 1:
            raise RuntimeError('The real coupon creation did not return its unique fixture')
        coupon = identifier(listed[0]['id'])
        gateway_call(admin, 'PUT', '/admin/promotion/coupons/' + coupon + '/issue', dict(id=coupon, issueEndTime=(datetime.now(SHANGHAI) + timedelta(days=2)).replace(tzinfo=None).isoformat(timespec='seconds'), termDays=30), str(uuid.uuid4()))
        reward_month = datetime.now(SHANGHAI).strftime('%Y%m')
        request_id, event_id = enqueue_reward_request(student, coupon, replay_rule, reward_month)
        deadline = time.monotonic() + 90
        receipt = None
        while time.monotonic() < deadline:
            results = gateway_call(admin, 'GET', '/learning-hub/admin/rewards?month=' + reward_month + '&pageNo=1&pageSize=100')['list']
            receipt = next((row for row in results if row['requestId'] == request_id), None)
            if receipt and receipt['status'] == 'FULFILLED':
                break
            if receipt and receipt['status'] == 'FAILED':
                raise RuntimeError('Actual reward delivery failed: ' + str(receipt.get('lastError')))
            time.sleep(.5)
        else:
            raise RuntimeError('The actual reward request did not receive a fulfilled acknowledgement')
        ticket = identifier(receipt['userCouponId'])
        verified = sql('tj_promotion', 'SELECT COUNT(*) FROM season_reward_delivery d JOIN user_coupon u ON u.id=d.user_coupon_id JOIN coupon c ON c.id=u.coupon_id WHERE d.request_id=' + literal(request_id) + " AND d.status='SUCCEEDED' AND u.id=" + ticket + ' AND u.user_id=' + student + ' AND u.coupon_id=' + coupon + ' AND c.issue_num=1 AND u.status=1 AND u.term_begin_time<=NOW() AND u.term_end_time>NOW()')
        if verified != '1':
            raise RuntimeError('Fulfilled receipt is not backed by a real valid student-owned coupon; predicates=' + reward_diagnostics(request_id, ticket, student, coupon))
        confirmation = gateway_call(learner, 'GET', '/orders/confirmation?courseIds=' + identifier(original['course']))
        candidate = next((item for item in confirmation['availableCoupons'] if str(item['id']) == ticket and str(item['couponId']) == coupon), None)
        if candidate is None:
            raise RuntimeError('Actual reward ticket is unavailable to its owner at order confirmation')
        actual_expiry = sql('tj_promotion', "SELECT DATE_FORMAT(term_end_time,'%Y-%m-%d %H:%i:%s') FROM user_coupon WHERE id=" + ticket + ' AND user_id=' + student)
        if candidate.get('name') != coupon_name or str(candidate.get('termEndTime', '')).replace('T', ' ')[:19] != actual_expiry:
            raise RuntimeError('Coupon choice metadata must match the real template name and owned ticket expiry')
        result = dict(synthetic=True, course=course, name=name, lesson=lesson, reviewAttempt=wrong_id,
                      reviewQuestion=question, reviewName=review_name, question=identifier(discussion['id']),
                      questionTitle=question_title, unassignedCourse=unassigned, historyMonth=history_month,
                      historyName=history_name, historyStatus='LEGACY_READONLY', historyPoints=37,
                      badgeName='首次通过考试', rewardRequestId=request_id, rewardCouponId=coupon, rewardMonth=reward_month,
                      evidence=dict(project=setup.PROJECT, seeded=['dedicated synthetic course and enrollment without a payment claim', 'catalogue/question bank/active teaching assignment and separate deleted assignment', 'legacy read-only UI snapshot', 'PENDING replay reward request and outbox envelope'],
                                    executed=['paper publication', 'failed and correct exam submissions', 'exam-to-learning completion and badge', 'discussion creation', 'coupon creation and issue', 'reward outbox dispatch, stock debit, ticket issuance and acknowledgement'],
                                    naturalSettlement=False, rewardEventId=event_id, rewardUserCouponId=ticket, passedAttempt=passed_id))
        fixture_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf8')
        print('Synthetic motivation fixture ready: actual exam/learning/reward consumers verified; natural settlement is outside this fixture', flush=True)
    finally:
        for session in sessions:
            session.close()


if __name__ == '__main__':
    main()
