"""Deterministic, sufficient mixed-business fixtures; isolated benchmarks only."""
import argparse
import json
import math
import secrets
import bcrypt
from setup import LOCAL, PROJECT, mysql

BASE_ID = 850000000000000000

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--max-seconds', type=int, default=600)
    args = parser.parse_args()
    if not PROJECT.startswith('tianji-opt-'):
        raise RuntimeError('Mixed fixtures require a separately named tianji-opt-* benchmark')
    bound = math.ceil(args.max_seconds / .25) + 1
    path = LOCAL / 'load-fixture.json'
    if path.exists():
        if json.loads(path.read_text(encoding='utf8'))['cyclesPerActor'] < bound:
            raise RuntimeError('Fixture bank too small')
        print('Existing mixed-business fixture retained'); return
    accounts = json.loads((LOCAL / 'accounts.json').read_text(encoding='utf8'))
    fixture = json.loads((LOCAL / 'browser-fixture.json').read_text(encoding='utf8'))
    password = secrets.token_urlsafe(24)
    hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
    schema = {table: [line.split('\t')[0] for line in mysql('SHOW COLUMNS FROM tj_user.' + table).splitlines()] for table in ('user', 'user_detail')}
    statements = []; users = []
    for index in range(200):
        uid = BASE_ID + index; username = 'opt-load-' + str(index)
        values = {'id': str(uid), 'username': "'" + username + "'", 'name': "'" + username + "'", 'password': "'" + hashed + "'", 'cell_phone': "'189" + str(index).zfill(8) + "'", 'auth_version': '0', 'status': '1'}
        for table, columns in schema.items():
            expressions = ','.join(values.get(column, '`' + column + '`') for column in columns)
            statements.append('INSERT INTO tj_user.' + table + '(' + ','.join('`' + column + '`' for column in columns) + ') SELECT ' + expressions + ' FROM tj_user.' + table + ' WHERE id=' + accounts['student']['id'] + ';')
        lessons = {}
        for offset, course in enumerate((fixture['course'], fixture['notesCourse'])):
            lesson = BASE_ID + 1000 + index * 2 + offset; detail = BASE_ID + 2000 + index * 2 + offset
            statements.append(f'INSERT INTO tj_learning.learning_lesson(id,user_id,course_id,expire_time,status,learned_sections) VALUES({lesson},{uid},{course},NOW()+INTERVAL 1 YEAR,0,0);')
            statements.append(f'INSERT INTO tj_learning.learning_entitlement(order_detail_id,order_id,user_id,course_id,active,expires_at) VALUES({detail},{detail},{uid},{course},1,NOW()+INTERVAL 1 YEAR);')
            lessons[course] = str(lesson)
        users.append({'id': str(uid), 'username': username, 'password': password, 'lessons': lessons})
    staff = {'teacher': [], 'admin': []}
    for role_index, role in enumerate(staff):
        for index in range(20):
            uid = BASE_ID + 500 + role_index * 100 + index
            username = 'opt-' + role + '-' + str(index)
            values = {'id': str(uid), 'username': "'" + username + "'", 'name': "'" + username + "'", 'password': "'" + hashed + "'", 'cell_phone': "'188" + str(role_index * 100 + index).zfill(8) + "'", 'auth_version': '0', 'status': '1'}
            for table, columns in schema.items():
                expressions = ','.join(values.get(column, '`' + column + '`') for column in columns)
                statements.append('INSERT INTO tj_user.' + table + '(' + ','.join('`' + column + '`' for column in columns) + ') SELECT ' + expressions + ' FROM tj_user.' + table + ' WHERE id=' + accounts[role]['id'] + ';')
            staff[role].append({'id': str(uid), 'username': username, 'password': password})
    for index, user in enumerate(users):
        user['teacherAccount'] = staff['teacher'][index % 20]
        user['adminAccount'] = staff['admin'][index % 20]
    mysql('START TRANSACTION;' + ''.join(statements) + 'COMMIT;')
    mysql('UPDATE tj_course.course_catalogue SET media_duration=36000 WHERE id=' + fixture['video'])
    paper_start = BASE_ID + 100000; coupon_start = BASE_ID + 200000
    teacher = accounts['teacher']['id']; admin = accounts['admin']['id']
    for start in range(0, bound, 100):
        statements = []
        for index in range(start, min(start + 100, bound)):
            paper = paper_start + index; coupon = coupon_start + index
            statements.append(f"INSERT INTO tj_exam.exam_paper(id,course_id,section_id,version,total_score,pass_percent,section_count) VALUES({paper},{fixture['course']},{fixture['exam']},{index+1},20,60,2);")
            statements.append(f"INSERT INTO tj_exam.exam_paper_question(paper_id,question_id,position,name,type,score,options,answer,analysis) SELECT {paper},q.id,IF(q.type=5,1,0),q.name,q.type,q.score,d.options,d.answer,d.analysis FROM tj_exam.question q JOIN tj_exam.question_detail d ON d.id=q.id WHERE q.id IN({fixture['objective']},{fixture['subjective']});")
            statements.extend(f"INSERT INTO tj_exam.exam_grader(paper_id,user_id) VALUES({paper},{account['id']});" for account in staff['teacher'])
            statements.append(f"INSERT INTO tj_promotion.coupon(id,name,discount_type,discount_value,obtain_way,issue_begin_time,issue_end_time,term_days,status,total_num,user_limit,creater,updater) VALUES({coupon},'opt-hot-{index}',4,1,1,NOW()-INTERVAL 1 DAY,NOW()+INTERVAL 1 YEAR,365,3,200,1,{admin},{admin});")
        mysql('START TRANSACTION;' + ''.join(statements) + 'COMMIT;')
    mysql(f"INSERT INTO tj_exam.exam_paper_family(course_id,section_id,latest_version) VALUES({fixture['course']},{fixture['exam']},{bound}) ON DUPLICATE KEY UPDATE latest_version={bound}")
    (LOCAL / 'load-accounts.json').write_text(json.dumps(users, indent=2), encoding='utf8')
    value = {**fixture, 'seed': 20261007, 'cyclesPerActor': bound, 'paperStart': str(paper_start), 'couponStart': str(coupon_start), 'tradeOrderStart': str(BASE_ID + 1000000), 'thinkSeconds': .25, 'videoDuration': 36000}
    path.write_text(json.dumps(value, indent=2), encoding='utf8')
    print(f'Mixed fixture ready: 200 users, {bound} independent paper/coupon rounds; credentials remain private')

if __name__ == '__main__': main()
