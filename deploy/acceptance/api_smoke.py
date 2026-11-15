import requests, json, uuid, time, concurrent.futures, os, argparse
from prepare import LOCAL, mysql

BASE = os.environ.get('ZXT_ACCEPTANCE_ORIGIN', 'http://127.0.0.1:23310').rstrip('/') + '/api/v2'
accounts = json.loads((LOCAL / 'accounts.json').read_text())


def call(session, method, path, body=None, key=None, expected=200):
    response = session.request(
        method,
        BASE + path,
        json=body,
        headers={'Idempotency-Key': key} if key else {},
        timeout=20,
    )
    data = response.json()
    if response.status_code != expected or expected < 400 and data.get('code') != 200:
        raise RuntimeError(
            f'{method} {path}: HTTP {response.status_code}, code {data.get("code")}, request {data.get("requestId")}'
        )
    return data.get('data')


def login(label):
    session = requests.Session()
    account = accounts[label]
    token = call(
        session,
        'POST',
        '/auth/accounts/' + ('login' if label == 'student' else 'admin/login'),
        dict(
            type=1,
            username=account['username'],
            password=account['password'],
            rememberMe=False,
        ),
    )
    session.headers['Authorization'] = 'Bearer ' + token
    return session


def wait(session, service, op):
    deadline = time.monotonic() + 60
    while op['status'] == 'PENDING' and time.monotonic() < deadline:
        time.sleep(0.3)
        op = call(session, 'GET', '/operations/' + service + '/' + op['operationId'])
    return op


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument(
        '--persistence-only', action='store_true',
        help='Run persistence checks without repeating the authorization checks',
    )
    args = parser.parse_args()
    student = login('student')
    if not args.persistence_only:
        admin = login('admin')
        teacher = login('teacher')
        print('Three isolated roles logged in', flush=True)
        anon = requests.Session()
        anon.headers.update(
            {
                'user-info': accounts['admin']['id'],
                'user-role': '1',
                'X-Internal-Token': 'forged',
            }
        )
        call(anon, 'GET', '/favorites', expected=401)
        call(student, 'GET', '/admin/payment-conflicts', expected=403)
        # The gateway replaces a client-supplied origin and identity with its own values.
        student.headers.update(
            {
                'x-request-from': 'feign',
                'user-info': accounts['admin']['id'],
                'user-role': '1',
            }
        )
        call(student, 'GET', '/favorites')
        print('Identity spoofing and student/admin boundary checked', flush=True)
    user = accounts['student']['id']
    marker = uuid.uuid4().hex
    course = str(820000000000000000 + int(marker[:14], 16))
    # Seed only a new catalogue row; the real enrollment/outbox flow grants learning rights.
    mysql(
        'CREATE TEMPORARY TABLE api_smoke_course LIKE course;'
        'INSERT INTO api_smoke_course SELECT * FROM course WHERE id=1;'
        f"UPDATE api_smoke_course SET id={course},name='API smoke free {marker}',"
        'free=1,price=0,status=2,deleted=0,valid_duration=12,'
        'purchase_start_time=NOW()-INTERVAL 1 DAY,purchase_end_time=NOW()+INTERVAL 1 YEAR;'
        'INSERT INTO course SELECT * FROM api_smoke_course;',
        'tj_course',
    )
    enrollment = wait(
        student, 'trade',
        call(student, 'POST', '/orders/free-courses/' + course, {}, marker, 202),
    )
    assert enrollment['status'] == 'SUCCEEDED', enrollment
    deadline = time.monotonic() + 60
    while True:
        lesson = call(student, 'GET', '/services/learning/learning-records/course/' + course)
        if lesson is not None and lesson['status'] != 3:
            break
        if time.monotonic() >= deadline:
            raise AssertionError('Free enrollment did not project valid learning rights')
        time.sleep(0.3)
    key = uuid.uuid4().hex
    payload = dict(courseId=course, content='Acceptance private note ' + key)
    original = call(student, 'POST', '/notes', payload, key, 202)
    repeated = call(student, 'POST', '/notes', payload, key, 202)
    assert repeated['operationId'] == original['operationId']
    call(student, 'POST', '/notes', dict(payload, content='different'), key, 409)
    result = wait(student, 'learning', original)
    assert result['status'] == 'SUCCEEDED'
    note = result['result']
    assert call(student, 'GET', '/notes/' + note['id'])['content'] == payload['content']
    edited = wait(
        student,
        'learning',
        call(
            student,
            'PUT',
            '/notes/' + note['id'],
            dict(content='edited', version=note['version']),
            uuid.uuid4().hex,
            202,
        ),
    )
    assert edited['status'] == 'SUCCEEDED'
    stale = wait(
        student,
        'learning',
        call(
            student,
            'PUT',
            '/notes/' + note['id'],
            dict(content='stale', version=note['version']),
            uuid.uuid4().hex,
            202,
        ),
    )
    assert stale['status'] == 'FAILED'
    call(student, 'PUT', '/favorites/' + course)
    call(student, 'PUT', '/favorites/' + course)
    assert call(student, 'GET', '/favorites/' + course)['favorited'] is True
    assert (
        mysql(
            f'SELECT COUNT(*) FROM course_favorite WHERE user_id={user} AND course_id={course}',
            'tj_learning',
        )
        == '1'
    )
    call(student, 'DELETE', '/favorites/' + course)
    call(student, 'DELETE', '/favorites/' + course)
    current = call(student, 'GET', '/notes/' + note['id'])
    delete_key = uuid.uuid4().hex
    delete_path = '/notes/' + note['id'] + '?version=' + str(current['version'])
    deletion = call(student, 'DELETE', delete_path, key=delete_key, expected=202)
    assert (
        call(student, 'DELETE', delete_path, key=delete_key, expected=202)[
            'operationId'
        ]
        == deletion['operationId']
    )
    assert wait(student, 'learning', deletion)['status'] == 'SUCCEEDED'
    call(student, 'GET', '/notes/' + note['id'], expected=400)

    def sign(index):
        session = requests.Session()
        session.headers.update(student.headers)
        key = uuid.uuid4().hex
        try:
            return wait(
                session,
                'learning',
                call(session, 'POST', '/sign-ins', key=key, expected=202),
            )
        finally:
            session.close()

    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        signed = list(pool.map(sign, range(10)))
    assert all(op['status'] == 'SUCCEEDED' for op in signed)
    assert (
        mysql(
            f"SELECT COUNT(*) FROM sign_record WHERE user_id={user} AND sign_day=CURDATE()",
            'tj_learning',
        )
        == '1'
    )
    assert (
        mysql(
            f"SELECT COUNT(*) FROM reliability_outbox WHERE business_key=CONCAT('sign:',{user},':',CURDATE())",
            'tj_learning',
        )
        == '1'
    )
    plans = call(
        student, 'GET', '/services/learning/lessons/plans?pageNo=1&pageSize=20'
    )
    expected_points = int(
        mysql(
            f"SELECT COALESCE(SUM(points),0) FROM points_record WHERE user_id={user} AND create_time>=DATE_SUB(CURDATE(),INTERVAL WEEKDAY(CURDATE()) DAY) AND create_time<DATE_SUB(CURDATE(),INTERVAL WEEKDAY(CURDATE()) DAY)+INTERVAL 7 DAY",
            'tj_learning',
        )
    )
    assert plans['weekPoints'] == expected_points
    # Only seeded acceptance users: distinct fixtures at each boundary, always removed.
    point_ids = [str(880000000000000000 + int(time.time()) * 10 + i) for i in range(4)]
    begin = 'DATE_SUB(CURDATE(),INTERVAL WEEKDAY(CURDATE()) DAY)'
    try:
        fixtures = [
            (user, 7, begin + '-INTERVAL 1 SECOND'),
            (user, 3, begin),
            (user, 11, begin + '+INTERVAL 7 DAY'),
            (accounts['teacher']['id'], 100, begin),
        ]
        for identity, (owner, points, date_sql) in zip(point_ids, fixtures):
            mysql(
                f"INSERT INTO points_record(id,user_id,type,points,source_event_id,create_time) VALUES({identity},{owner},1,{points},'{uuid.uuid4()}',{date_sql})",
                'tj_learning',
            )
        assert (
            call(
                student, 'GET', '/services/learning/lessons/plans?pageNo=1&pageSize=20'
            )['weekPoints']
            == expected_points + 3
        )
    finally:
        mysql(
            'DELETE FROM points_record WHERE id IN(' + ','.join(point_ids) + ')',
            'tj_learning',
        )
    refreshed = call(student, 'POST', '/auth/accounts/refresh?audience=student')
    assert isinstance(refreshed, str)
    (LOCAL / 'api-smoke.json').write_text(
        json.dumps(
            dict(
                status='PASSED',
                selection='PERSISTENCE' if args.persistence_only else 'ALL',
                checks=([] if args.persistence_only else [
                    'three roles login', 'identity spoofing', 'admin boundary',
                ]) + [
                    'free enrollment projects real learning rights',
                    'durable note create and replay',
                    'same key conflict',
                    'note optimistic conflict',
                    'note delete replay',
                    'concurrent sign uniqueness',
                    'owned weekly points equal persisted ledger',
                    'weekly boundaries include Monday midnight and exclude next week',
                    'favorite repeat writes',
                    'refresh cookie',
                ],
            ),
            indent=2,
        )
    )
    print('Real gateway/API persistence smoke PASSED', flush=True)
