"""Repair only entitlement facts proved by retained order details, after an immutable backup.

Unknown historical grants are listed for review; they are never invented from a lesson's
aggregated expiry, and no purchase, learning, exam or financial history is replaced.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
from setup import LOCAL, ROOT, BASE, PROJECT, COMPOSE, configure_acceptance, mysql
from evidence import utcnow

FACTS = """SELECT d.id,d.order_id,d.user_id,d.course_id,d.status,d.refund_status,d.valid_duration,
 d.course_expire_time,COALESCE(o.pay_time,o.finish_time) purchased_at,
 IF(COALESCE(d.valid_duration,0)<=0,NULL,COALESCE(d.course_expire_time,TIMESTAMPADD(MONTH,d.valid_duration,COALESCE(o.pay_time,o.finish_time)))) expiry,
 IF(COALESCE(d.refund_status,0)=5 OR o.status=7,0,1) active
 FROM tj_trade.order_detail d JOIN tj_trade.`order` o ON o.id=d.order_id
 WHERE d.status IN(2,4,5,6,7) ORDER BY d.user_id,d.course_id,d.id"""

def lines(sql):
    value = mysql(sql)
    return [line.split('\t') for line in value.splitlines()] if value else []

def state():
    facts = lines(FACTS)
    known = []
    manual = []
    for row in facts:
        detail, order, user, course, status, refund, duration, explicit, purchased, expiry, active = row
        if (duration != 'NULL' and int(duration) < 0 or purchased == 'NULL' and explicit == 'NULL'
                or duration not in ('NULL', '0') and expiry == 'NULL'
                or duration in ('NULL', '0') and explicit != 'NULL'):
            manual.append({'detailId': detail, 'reason': 'incomplete or conflicting purchased validity'}); continue
        known.append({'detailId': detail, 'orderId': order, 'userId': user, 'courseId': course,
                      'active': int(active), 'expiresAt': None if expiry == 'NULL' else expiry})
    rights = lines('SELECT order_detail_id,order_id,user_id,course_id,active,expires_at FROM tj_learning.learning_entitlement ORDER BY order_detail_id')
    current = {row[0]: row for row in rights}
    differences = []
    for fact in known:
        expected = [fact['detailId'], fact['orderId'], fact['userId'], fact['courseId'], str(fact['active']), fact['expiresAt'] or 'NULL']
        old = current.get(fact['detailId'])
        if old is not None and old[1:4] != expected[1:4]:
            manual.append({'detailId': fact['detailId'], 'reason': 'existing entitlement provenance conflict'}); continue
        if old is not None and old[4] == '0' and expected[4] == '1':
            manual.append({'detailId': fact['detailId'], 'reason': 'refund tombstone conflicts with financial snapshot'}); continue
        # DATETIME(3) output can include .000 while historical details have seconds.
        if old is None or old[:5] != expected[:5] or old[5].removesuffix('.000') != expected[5].removesuffix('.000'):
            differences.append({'before': old, 'after': fact})
    unknown = lines('SELECT l.id,l.user_id,l.course_id FROM tj_learning.learning_lesson l LEFT JOIN tj_learning.learning_entitlement e ON e.user_id=l.user_id AND e.course_id=l.course_id WHERE e.order_detail_id IS NULL ORDER BY l.user_id,l.course_id')
    fingerprint = hashlib.sha256(json.dumps({'facts': facts, 'rights': rights}, separators=(',', ':')).encode()).hexdigest()
    return {'sourceFingerprint': fingerprint, 'differences': differences, 'manualReview': manual,
            'lessonsWithoutProvenance': unknown, 'verifiedPairs': sorted({(fact['userId'], fact['courseId']) for fact in known if fact['detailId'] not in {m['detailId'] for m in manual}})}

def statement(fact):
    for key in ('detailId', 'orderId', 'userId', 'courseId'):
        if not fact[key].isdigit(): raise ValueError('Invalid fact identity')
    expiry = 'NULL' if fact['expiresAt'] is None else "'" + fact['expiresAt'] + "'"
    if fact['expiresAt'] is not None:
        import datetime
        datetime.datetime.fromisoformat(fact['expiresAt'])
    return 'INSERT INTO tj_learning.learning_entitlement(order_detail_id,order_id,user_id,course_id,active,expires_at) VALUES(' + ','.join([fact[key] for key in ('detailId','orderId','userId','courseId')] + [str(fact['active']), expiry]) + ') ON DUPLICATE KEY UPDATE active=VALUES(active),expires_at=VALUES(expires_at);'

def main():
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['audit','apply']);parser.add_argument('--audit',type=Path)
    args=parser.parse_args();LOCAL.mkdir(exist_ok=True)
    if not (BASE==ROOT/'deploy/compact' or BASE.is_relative_to(ROOT/'deploy/compact/.local/optimization')) or PROJECT!='tianji-compact' and not PROJECT.startswith('tianji-opt-'):
        raise RuntimeError('Historical repair is restricted to this workspace and its audit clones')
    if args.audit is not None and not args.audit.resolve().is_relative_to(LOCAL):raise RuntimeError('Use the difference list generated in this deployment')
    if args.action == 'audit':
        report={'at':utcnow(), **state()};path=LOCAL / ('entitlement-audit-'+str(__import__('time').time_ns())+'.json')
        path.write_text(json.dumps(report,indent=2),encoding='utf8');print('Private historical difference list: '+str(path));return
    if args.audit is None:parser.error('apply requires the reviewed audit file')
    audit=json.loads(args.audit.read_text(encoding='utf8'))
    sys.path.insert(0,str(ROOT/'deploy/final'));from final_backup import quiesce,capture,verify
    runtime=configure_acceptance()
    with quiesce(runtime):
        before=state()
        if before['sourceFingerprint'] != audit['sourceFingerprint']:raise RuntimeError('Facts changed since audit; create a fresh difference list')
        backup=capture(runtime,BASE,quiesced=True);verify(backup)
        statements=['START TRANSACTION;']
        # No producers/consumers are running; acquire all guards in normal order.
        for user,course in before['verifiedPairs']:
            statements.append(f'INSERT INTO tj_learning.learning_entitlement_guard(user_id,course_id) VALUES({user},{course}) ON DUPLICATE KEY UPDATE course_id=VALUES(course_id);SELECT course_id FROM tj_learning.learning_entitlement_guard WHERE user_id={user} AND course_id={course} FOR UPDATE;')
        statements += [statement(row['after']) for row in before['differences']]
        for user,course in before['verifiedPairs']:
            statements.append(f"UPDATE tj_learning.learning_entitlement_guard g JOIN tj_learning.learning_lesson l ON l.user_id=g.user_id AND l.course_id=g.course_id SET g.last_active_status=IF(l.status<>3,l.status,g.last_active_status) WHERE g.user_id={user} AND g.course_id={course};")
            statements.append(f"UPDATE tj_learning.learning_lesson l JOIN tj_learning.learning_entitlement_guard g ON g.user_id=l.user_id AND g.course_id=l.course_id LEFT JOIN (SELECT user_id,course_id,COUNT(*) n,SUM(expires_at IS NULL) permanent,MAX(expires_at) expiry FROM tj_learning.learning_entitlement WHERE active=1 AND (expires_at IS NULL OR expires_at>NOW(3)) GROUP BY user_id,course_id) e ON e.user_id=l.user_id AND e.course_id=l.course_id SET l.status=IF(COALESCE(e.n,0)=0,3,IF(l.status=3,g.last_active_status,l.status)),l.expire_time=IF(COALESCE(e.n,0)=0 OR e.permanent>0,NULL,e.expiry) WHERE l.user_id={user} AND l.course_id={course};")
        statements.append('COMMIT;');mysql(''.join(statements))
        after=state()
        report={'status':'PASSED' if not after['differences'] else 'FAILED','at':utcnow(),'audit':str(args.audit.resolve()),'backup':str(backup),'beforeFingerprint':before['sourceFingerprint'],'afterFingerprint':after['sourceFingerprint'],'repaired':len(before['differences']),'manualReview':after['manualReview'],'lessonsWithoutProvenance':after['lessonsWithoutProvenance']}
        (LOCAL/'entitlement-reconciliation.json').write_text(json.dumps(report,indent=2),encoding='utf8')
        print('Verified historical repair: '+report['status']+'; unknown provenance retained for review')
        if report['status']!='PASSED':raise RuntimeError('Historical reconciliation incomplete')

if __name__=='__main__':main()
