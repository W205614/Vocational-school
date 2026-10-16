"""Opt-in HTTP/DB/broker acceptance on an already verified recovery clone.

Synthetic seeds: one new identity, one free single-exam course and question bank.
All enrollment, discussion, adoption, completion, points, achievements and notices
are executed by the deployed application. Never starts containers or changes clocks.
"""
from pathlib import Path
import argparse
from datetime import datetime, timezone, timedelta
import json
import os
import re
import secrets
import subprocess
import sys
import time
import traceback
import uuid

import bcrypt
import requests
import yaml

PROBE_SOURCE = Path(__file__).resolve().parent
ROOT = next(p for p in [PROBE_SOURCE, *PROBE_SOURCE.parents] if (p / 'pom.xml').is_file() and (p / 'deploy/compact/setup.py').is_file())
SOURCE = ROOT / 'deploy/compact'
TASK = SOURCE / '.local/learning-upgrade-20261015'
EXPECTED = '8dcf8e7d44c6048318ad1e306e1425c8fd40fb70'
SHANGHAI = timezone(timedelta(hours=8))
SEVEN = {'app-identity', 'app-commerce', 'app-education', 'app-support', 'app-gateway', 'web-student', 'web-admin'}


def require(value, message):
    if not value:
        raise RuntimeError(message)


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def preflight(raw_home, project, offset):
    require(offset == 1000, 'Only recovery offset 1000 is allowed')
    require(re.fullmatch(r'tianji-recovery-[a-z0-9-]+', project or ''), 'Primary projects are forbidden')
    raw = raw_home.absolute()
    require(not raw.is_symlink() and not any(p.is_symlink() for p in raw.parents), 'Symlink homes are forbidden')
    home = raw.resolve()
    require(home.parent in {ROOT / '.local', SOURCE / '.local'}, 'Recovery home must be directly inside an ignored local directory')
    require(home.name == 'upgrade-rehearsal-20261015b' or re.fullmatch(r'final-upgrade-[a-z0-9-]+', home.name), 'Only clone b or a named final recovery home is allowed')
    require(home.is_dir() and (home / 'compose.yaml').is_file(), 'Existing recovery home required')
    local = home / '.local'
    manifest = read(local / 'build-source.json')
    require(manifest['sourceCommit'] == EXPECTED and set(manifest['imageDigests']) == SEVEN, 'Exact final seven-image manifest required')
    recovery = read(local / 'recovery-result.json')
    require(recovery['rawRestoreProof']['status'] == 'PASSED', 'Raw recovery must already be verified')
    browser = read(local / 'browser-result.json')
    require(browser['status'] == 'PASSED' and browser['passed'] == 42 and browser['failed'] == 0 and browser['skipped'] == 30, 'Current complete 42-case advanced browser proof required')
    compose = yaml.safe_load((home / 'compose.yaml').read_text(encoding='utf8'))
    require(compose.get('name') == project, 'Compose project ownership differs')
    require('127.0.0.1:25316:3306' in compose['services']['mysql']['ports'], 'Exact isolated database port is required')
    command = ['docker', 'compose', '-p', project, '-f', str(home / 'compose.yaml'), '--env-file', str(home / '.env')]
    started = []
    observed = set()
    for service, definition in compose['services'].items():
        container = subprocess.check_output(command + ['ps', '-q', service], stderr=subprocess.DEVNULL, text=True).strip()
        require(container and '\n' not in container, 'Exactly one live container per service required')
        info = json.loads(subprocess.check_output(['docker', 'inspect', container], stderr=subprocess.DEVNULL))[0]
        labels = info['Config'].get('Labels', {})
        require(labels.get('com.docker.compose.project') == project and labels.get('com.docker.compose.service') == service, 'Live container ownership differs')
        require(Path(labels.get('com.docker.compose.project.working_dir', '')).resolve() == home, 'Live project home differs')
        require(info['State'].get('Health', {}).get('Status') == 'healthy', 'Every recovery container must already be healthy')
        require(all(n.startswith(project + '_') for n in info['NetworkSettings']['Networks']), 'Shared networks are forbidden')
        for mount in info['Mounts']:
            if mount['Type'] == 'volume':
                require(mount['Name'].startswith(project + '_'), 'Foreign data volume is forbidden')
            elif mount['Type'] == 'bind':
                require(Path(mount['Source']).resolve().is_relative_to(home), 'Foreign bind mount is forbidden')
        if service in SEVEN:
            require(info['Image'] == manifest['imageDigests'][service] == definition['image'], 'Live image differs from final frozen source')
            started.append(datetime.fromisoformat(info['State']['StartedAt'].replace('Z', '+00:00')).timestamp())
            observed.add(service)
    require(observed == SEVEN, 'All seven final application images must be running')
    require((local / 'browser-result.json').stat().st_mtime > max(started), 'Browser evidence predates the current application containers')
    os.environ.update(TJ_COMPACT_HOME=str(home), TJ_COMPACT_PROJECT=project, TJ_COMPACT_PORT_OFFSET='1000', TJ_MOTIVATION_FIXTURE='1')
    sys.path.insert(0, str(SOURCE))
    import setup
    import motivation_fixture as fixture
    require(setup.BASE == home and setup.PROJECT == project and setup.OFFSET == 1000, 'Imported runtime scope differs')
    return home, setup, fixture


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--home', type=Path, required=True)
    parser.add_argument('--project', required=True)
    parser.add_argument('--offset', type=int, required=True)
    args = parser.parse_args()
    home, setup, fixture = preflight(args.home, args.project, args.offset)
    TASK.mkdir(parents=True, exist_ok=True)
    run_id = uuid.uuid4().hex
    work = TASK / ('interaction-probe-' + run_id)
    work.mkdir()
    private = dict(status='RUNNING', project=args.project, home=str(home), sourceCommit=EXPECTED, startedAt=datetime.now(timezone.utc).isoformat(),
                   seeded=['synthetic user/user_detail identity', 'free one-exam course/catalogue/teacher/question-bank'],
                   pointsAchievementsCompletionSeeded=False, hostClockChanged=False, browserCaseCountAdded=0)
    public = dict(passed=False, sourceAndIsolationVerified=True, browserCaseCountAdded=0)
    sessions = []
    try:
        accounts = read(setup.LOCAL / 'accounts.json')
        original = read(setup.LOCAL / 'browser-fixture.json')
        uid = fixture.identifier
        lit = fixture.literal

        def sql(database, statement):
            require(database in {'tj_user', 'tj_course', 'tj_exam', 'tj_learning', 'tj_message'}, 'Unexpected schema')
            return setup.mysql('USE `' + database + '`;\n' + statement)

        def wait_for(predicate, message, seconds=45):
            deadline = time.monotonic() + seconds
            while time.monotonic() < deadline:
                value = predicate()
                if value:
                    return value
                time.sleep(.4)
            raise RuntimeError(message)

        def operation(session, service, endpoint, body, key=None):
            view = fixture.gateway_call(session, 'POST', endpoint, body, key or str(uuid.uuid4()))
            ident = view['operationId']
            deadline = time.monotonic() + 60
            while view['status'] == 'PENDING' and time.monotonic() < deadline:
                time.sleep(.25)
                view = fixture.gateway_call(session, 'GET', '/operations/' + service + '/' + ident)
            require(view['status'] == 'SUCCEEDED', 'Real HTTP operation failed: ' + endpoint + ', ' + str(view.get('errorCode')))
            return view

        day = datetime.now(SHANGHAI).date()
        base = 860000000000000000 + int(time.time() * 1000) * 1000 + secrets.randbelow(400)
        course, chapter, section, bank_question, assignment, student = [uid(base + i) for i in [1, 2, 3, 4, 5, 20]]
        teacher, admin_id, author_id = [uid(accounts[role]['id']) for role in ['teacher', 'admin', 'student']]
        marker = run_id[:12]
        username, password = 'probe_' + marker, 'P9x!' + secrets.token_hex(6)
        phone = '197' + str(secrets.randbelow(100000000)).zfill(8)
        require(sql('tj_user', 'SELECT COUNT(*) FROM user WHERE id=' + student + ' OR username=' + lit(username) + ' OR cell_phone=' + lit(phone)) == '0', 'Synthetic identity collided')
        hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
        sql('tj_user', 'START TRANSACTION;INSERT INTO user(id,username,cell_phone,password,type,status) VALUES(' + student + ',' + lit(username) + ',' + lit(phone) + ',' + lit(hashed) + ',2,1);INSERT INTO user_detail(id,type,name,role_id) VALUES(' + student + ',2,' + lit('Synthetic interaction learner') + ',2);COMMIT;')
        fresh = dict(id=student, username=username, password=password, role=2)
        (work / 'synthetic-identity.json').write_text(json.dumps(fresh, indent=2), encoding='utf8')
        require(sql('tj_learning', 'SELECT COUNT(*) FROM points_record WHERE user_id=' + student) == '0', 'Clean learner already has points')
        require(sql('tj_learning', 'SELECT COUNT(*) FROM learning_achievement WHERE user_id=' + student) == '0', 'Clean learner already has achievements')
        name = 'Synthetic interaction course ' + marker
        columns = [line.split('\t')[0] for line in sql('tj_course', 'SHOW COLUMNS FROM course').splitlines()]
        replacements = {'id': course, 'name': lit(name), 'status': '2', 'free': '1', 'price': '0', 'section_num': '1', 'media_duration': '0',
                        'creater': admin_id, 'updater': admin_id, 'deleted': '0', 'purchase_start_time': 'NOW()-INTERVAL 1 DAY', 'purchase_end_time': 'NOW()+INTERVAL 1 YEAR', 'valid_duration': '12'}
        sql('tj_course', 'INSERT INTO course(' + ','.join('`' + c + '`' for c in columns) + ') SELECT ' + ','.join(replacements.get(c, '`' + c + '`') for c in columns) + ' FROM course WHERE id=' + uid(original['freeCourse']))
        require(sql('tj_course', 'SELECT COUNT(*) FROM course WHERE id=' + course + ' AND section_num=1 AND free=1 AND deleted=0') == '1', 'Synthetic free course unavailable')
        sql('tj_course', 'START TRANSACTION;INSERT INTO course_teacher(id,course_id,teacher_id,is_show,c_index,dep_id,create_time,update_time,creater,updater,deleted) VALUES(' + assignment + ',' + course + ',' + teacher + ',1,1,0,NOW(),NOW(),' + admin_id + ',' + admin_id + ',0);INSERT INTO course_catalogue(id,name,course_id,type,parent_catalogue_id,media_duration,c_index,dep_id,creater,updater) VALUES(' + chapter + ',' + lit('Synthetic interaction chapter') + ',' + course + ',1,0,0,1,0,' + admin_id + ',' + admin_id + '),(' + section + ',' + lit('Synthetic completion exam') + ',' + course + ',3,' + chapter + ',0,1,0,' + admin_id + ',' + admin_id + ');COMMIT;')
        sql('tj_exam', 'START TRANSACTION;INSERT INTO question(id,name,type,cate_id1,cate_id2,cate_id3,difficulty,score,creater,updater) VALUES(' + bank_question + ',' + lit('Synthetic completion objective ' + marker) + ',1,1,2,3,1,10,' + admin_id + ',' + admin_id + ');INSERT INTO question_detail(id,options,answer,analysis) VALUES(' + bank_question + ',' + lit('["A","B"]') + ',' + lit('1') + ',' + lit('Synthetic completion answer') + ');INSERT INTO question_biz(biz_id,question_id) VALUES(' + section + ',' + bank_question + ');COMMIT;')
        private.update(course=course, student=student, teacher=teacher, questionAuthor=author_id)
        admin = fixture.login(accounts, 'admin');sessions.append(admin)
        author = fixture.login(accounts, 'student');sessions.append(author)
        learner = fixture.login({'student': fresh}, 'student');sessions.append(learner)
        staff = fixture.login(accounts, 'teacher');sessions.append(staff)
        guest = requests.Session();sessions.append(guest)
        metadata = fixture.gateway_call(guest, 'GET', '/public/courses/' + course)
        require(metadata['name'] == name and metadata['price'] == 0, 'Guest public course metadata failed')

        def safe(value):
            if isinstance(value, dict):
                require(not set(value).intersection({'mediaId', 'mediaName', 'password', 'username', 'teacherIds'}), 'Public response leaks private fields')
                for child in value.values():safe(child)
            elif isinstance(value, list):
                for child in value:safe(child)

        safe(metadata)
        forbidden = guest.post('http://127.0.0.1:' + str(setup.PORTS['gateway']) + '/api/v2/services/learning/replies', json={'questionId': course, 'content': 'anonymous probe'}, headers={'Idempotency-Key': str(uuid.uuid4())}, timeout=15)
        require(forbidden.status_code == 401, 'Guest interaction must require authentication')
        public['guestPublicAndPrivateBoundary'] = True
        for session, who in [(author, author_id), (learner, student)]:
            operation(session, 'trade', '/orders/free-courses/' + course, {})
            wait_for(lambda: sql('tj_learning', 'SELECT COUNT(*) FROM learning_lesson l JOIN learning_entitlement e ON e.user_id=l.user_id AND e.course_id=l.course_id WHERE l.user_id=' + who + ' AND l.course_id=' + course + ' AND e.active=1') == '1', 'Actual free enrollment did not grant learning rights')
        require(sql('tj_learning', 'SELECT COUNT(*) FROM learning_record WHERE user_id=' + student) == '0', 'Completion must start empty')
        public['realFreeEnrollments'] = 2

        def overview():
            value = fixture.gateway_call(learner, 'GET', '/learning-hub/overview')
            require({'pendingExams', 'waitingForGradingExams', 'unreadReplies', 'examReminderError', 'replyReminderError'}.issubset(value), 'Overview reminder contract missing')
            require(value['examReminderError'] is None and value['replyReminderError'] is None, 'Real reminder relay degraded; HTTP 200 alone is not proof')
            require(all(type(value[k]) is int and value[k] >= 0 for k in ['pendingExams', 'waitingForGradingExams', 'unreadReplies']), 'Reminder counts must be actual nonnegative numbers')
            return value

        initial_overview = overview()
        require(initial_overview['pendingExams'] == 0 and initial_overview['waitingForGradingExams'] == 0, 'Fresh learner has unexpected pending exams')
        question_ids, reply_ids, accepted = [], [], []

        def qa():
            rows = fixture.gateway_call(learner, 'GET', '/learning-hub/points?pageNo=1&pageSize=100')['list']
            values = [r for r in rows if int(r['type']) == 3]
            return sum(int(r['points']) for r in values), len(values)

        require(qa() == (0, 0), 'QA quota must start at zero')
        before_author_inbox = {str(r['id']) for r in fixture.gateway_call(author, 'GET', '/services/message/inboxes?pageNo=1&pageSize=100')['list']}
        for i in range(5):
            require(datetime.now(SHANGHAI).date() == day, 'Business day crossed; quota proof cannot mix days')
            title, content = 'Synthetic adoption ' + marker + ' ' + str(i + 1), 'Synthetic student answer ' + marker + ' ' + str(i + 1)
            operation(author, 'learning', '/services/learning/questions', dict(courseId=course, chapterId=chapter, sectionId=section, title=title, description='Isolated adoption and notification acceptance', anonymity=False))
            questions = fixture.gateway_call(author, 'GET', '/services/learning/questions/page?courseId=' + course + '&pageNo=1&pageSize=100')['list']
            found = [q for q in questions if q['title'] == title]
            require(len(found) == 1, 'Real question not uniquely persisted');question_id = uid(found[0]['id']);question_ids.append(question_id)
            operation(learner, 'learning', '/services/learning/replies', dict(questionId=question_id, content=content, anonymity=False))
            replies = fixture.gateway_call(learner, 'GET', '/services/learning/replies/page?questionId=' + question_id + '&pageNo=1&pageSize=100')['list']
            found = [r for r in replies if r['content'] == content and str(r['userId']) == student]
            require(len(found) == 1, 'Real student answer not uniquely persisted');reply_id = uid(found[0]['id']);reply_ids.append(reply_id)
            key = str(uuid.uuid4());endpoint = '/learning-hub/questions/' + question_id + '/accept'
            view = operation(staff, 'learning', endpoint, dict(replyId=reply_id), key)
            expected = min((i + 1) * 5, 20)
            require(qa() == (expected, min(i + 1, 4)), 'Actual five-point award or daily twenty-point quota failed')
            accepted.append(expected)
            if i == 0:
                same = operation(staff, 'learning', endpoint, dict(replyId=reply_id), key)
                require(same['operationId'] == view['operationId'], 'Same HTTP idempotency key did not reuse its operation')
                operation(staff, 'learning', endpoint, dict(replyId=reply_id), str(uuid.uuid4()))
                require(qa() == (5, 1), 'Reaccepting one answer duplicated points')
        public.update(adoptions=5, qaPoints=20, qaCreditedFacts=4, firstAwardFive=True, fifthAwardCapped=True, sameAnswerReplayUnique=True)
        require(sql('tj_learning', 'SELECT points FROM points_daily_quota WHERE user_id=' + student + ' AND type=3 AND quota_day=' + lit(day.isoformat())) == '20', 'Persistent QA quota differs')
        for question_id, reply_id in zip(question_ids, reply_ids):
            require(sql('tj_learning', 'SELECT COUNT(*) FROM interaction_question WHERE id=' + question_id + ' AND accepted_reply_id=' + reply_id + ' AND accepted_by=' + teacher) == '1', 'Actual teacher acceptance missing')
            for key in ['accepted:' + question_id, 'reply:' + reply_id]:
                wait_for(lambda key=key: sql('tj_message', "SELECT COUNT(*) FROM reliability_inbox WHERE consumer_name='learning.notice' AND event_id=" + lit(key)) == '1', 'Actual broker notice receipt missing')
        notices = wait_for(lambda: (lambda rows: rows if len([r for r in rows if r.get('title') == '你的回答已被教师采纳']) == 5 else None)(fixture.gateway_call(learner, 'GET', '/services/message/inboxes?pageNo=1&pageSize=100')['list']), 'Student acceptance notices missing')
        author_notices = wait_for(lambda: (lambda rows: rows if len([r for r in rows if str(r['id']) not in before_author_inbox and r.get('title') == '你的课程问题收到新回复']) >= 5 else None)(fixture.gateway_call(author, 'GET', '/services/message/inboxes?pageNo=1&pageSize=100')['list']), 'Question author reply notices missing')
        require(sql('tj_message', 'SELECT COUNT(*) FROM user_inbox WHERE user_id=' + student + ' AND title=' + lit('你的回答已被教师采纳')) == '5', 'Reaccepting an answer duplicated its notice')
        public.update(acceptedNotices=5, replyNotices=5, actualBrokerNotices=True)
        require(overview()['unreadReplies'] == initial_overview['unreadReplies'] + 5, 'Actual accepted-answer notices did not increase own overview count')
        accepted_notice = next(n for n in notices if n.get('title') == '你的回答已被教师采纳')
        fixture.gateway_call(learner, 'PUT', '/services/message/inboxes/' + uid(accepted_notice['id']) + '/read')
        require(overview()['unreadReplies'] == initial_overview['unreadReplies'] + 4, 'Real mark-read did not decrement own reminder count')
        public.update(realOverviewRelay=True, ownUnreadIncrement=5, markReadDecrement=1)
        require(not fixture.gateway_call(learner, 'GET', '/learning-hub/achievements')['list'], 'Fresh learner unexpectedly earned a badge before completion')
        paper = uid(fixture.command(admin, 'exam', 'POST', '/admin/exam-papers', dict(courseId=course, sectionId=section, questionIds=[bank_question], passPercent=60, graders=[teacher]))['id'])
        require(overview()['pendingExams'] == 1 and overview()['waitingForGradingExams'] == 0, 'Published unpassed paper missing from actionable reminders')
        attempt = uid(fixture.command(learner, 'exam', 'POST', '/exam-papers/' + paper + '/attempts', {})['id'])
        require(overview()['pendingExams'] == 1, 'In-progress exam stopped being actionable')
        result = fixture.command(learner, 'exam', 'POST', '/exam-attempts/' + attempt + '/submit', dict(answers={bank_question: '1'}))
        require(result['status'] == 'FINISHED' and result['passed'], 'Actual completion exam did not pass')
        require(overview()['pendingExams'] == 0, 'Passed exam remained actionable')

        def course_completed():
            record = fixture.gateway_call(learner, 'GET', '/services/learning/learning-records/course/' + course)
            return record if int(record['status']) == 2 and int(record['learnedSections']) == 1 and len([r for r in record['records'] if r.get('finished')]) == 1 else None

        wait_for(course_completed, 'Actual exam-to-learning consumer did not complete the single-section course')
        wait_for(lambda: any(b['code'] == 'FIRST_COURSE' for b in fixture.gateway_call(learner, 'GET', '/learning-hub/achievements')['list']), 'Real completion did not award FIRST_COURSE')
        # A passed paper intentionally refuses another attempt. Publish a new immutable
        # version of the same section to exercise the permitted cross-version retake.
        next_paper = uid(fixture.command(admin, 'exam', 'POST', '/admin/exam-papers', dict(courseId=course, sectionId=section, questionIds=[bank_question], passPercent=60, graders=[teacher]))['id'])
        require(next_paper != paper and overview()['pendingExams'] == 1, 'Latest paper version was not actionable')
        retake = uid(fixture.command(learner, 'exam', 'POST', '/exam-papers/' + next_paper + '/attempts', {})['id'])
        result = fixture.command(learner, 'exam', 'POST', '/exam-attempts/' + retake + '/submit', dict(answers={bank_question: '1'}))
        require(result['status'] == 'FINISHED' and result['passed'], 'Actual retake did not pass')
        require(overview()['pendingExams'] == 0, 'Passing latest version did not clear actionable reminder')
        public.update(pendingExamProgression=[0, 1, 1, 0, 1, 0], separateWaitingCountZero=True)
        for passed in [attempt, retake]:
            event = sql('tj_exam', 'SELECT event_id FROM reliability_outbox WHERE business_key=' + lit('exam:' + passed + ':passed'))
            require(event, 'Exam passed outbox fact missing')
            wait_for(lambda event=event: sql('tj_learning', "SELECT COUNT(*) FROM reliability_inbox WHERE consumer_name='learning.exam' AND event_id=" + lit(event)) == '1', 'Actual exam passed receipt missing')
        require(sql('tj_learning', 'SELECT COUNT(*) FROM learning_achievement WHERE user_id=' + student + " AND code='FIRST_COURSE'") == '1', 'FIRST_COURSE badge duplicated')
        require(sql('tj_learning', 'SELECT COUNT(*) FROM learning_milestone WHERE user_id=' + student + ' AND course_id=' + course) == '1', 'Retake duplicated learning milestone')
        points = fixture.gateway_call(learner, 'GET', '/learning-hub/points?pageNo=1&pageSize=100')['list']
        require(sum(int(p['points']) for p in points if int(p['type']) == 1) == 10 and qa() == (20, 4), 'Retake changed learning or QA points')
        public.update(firstCourseViaRealExamConsumer=True, learningPoints=10, courseBadgeCount=1, retakeDoesNotDuplicate=True, passed=True)
        private.update(status='PASSED', questionIds=question_ids, replyIds=reply_ids, qaProgression=accepted, examAttempt=attempt, retakeAttempt=retake,
                       noticeInboxCount=len(notices), authorInboxCount=len(author_notices), result=public)
    except Exception as error:
        private.update(status='FAILED', failureType=type(error).__name__, failureMessage=str(error))
        (work / 'failure.log').write_text(traceback.format_exc(), encoding='utf8')
        raise
    finally:
        for session in sessions:session.close()
        private['completedAt'] = datetime.now(timezone.utc).isoformat()
        (work / 'result.json').write_text(json.dumps(private, ensure_ascii=False, indent=2), encoding='utf8')
        (TASK / 'live-interaction-result.json').write_text(json.dumps(private, ensure_ascii=False, indent=2), encoding='utf8')
        (TASK / 'live-interaction-result-public.json').write_text(json.dumps(public, indent=2), encoding='utf8')
    print(json.dumps(public))


if __name__ == '__main__':
    main()
