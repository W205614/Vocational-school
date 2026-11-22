"""Real gateway, transactions and RabbitMQ; explicit isolated payment simulator."""

import json, time, uuid, concurrent.futures
from api_smoke import call, login, wait, accounts
from prepare import mysql, LOCAL


def eventually(check, description, seconds=45):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        if check():
            return
        time.sleep(0.3)
    raise AssertionError(description)


def scalar(sql, db='tj_trade'):
    return mysql(sql, db)


def order(student, courses=None):
    courses = courses or ['1']
    confirm = call(
        student, 'GET', '/orders/confirmation?courseIds=' + ','.join(courses)
    )
    body = dict(orderId=confirm['orderId'], courseIds=courses, couponIds=[])
    key = uuid.uuid4().hex
    first = call(student, 'POST', '/orders', body, key, 202)
    repeated = call(student, 'POST', '/orders', body, key, 202)
    assert first['operationId'] == repeated['operationId']
    result = wait(student, 'trade', first)
    assert result['status'] == 'SUCCEEDED', result
    return str(confirm['orderId'])


def pay(student, identity):
    result = call(
        student,
        'POST',
        '/services/trade/pay/order',
        dict(orderId=identity, payChannelCode='mockPay'),
    )
    assert result == '/simulate-payment/' + identity


def main():
    student = login('student')
    admin = login('admin')
    environment = call(student, 'GET', '/environment')
    assert environment['payment'] == 'SIMULATED'
    identity = order(student)
    assert call(student, 'GET', '/orders/' + identity)['details'][0]['entitlement']['state'] == 'NOT_GRANTED'
    call(student, 'GET', '/admin/dashboard/pending-entitlements', expected=403)
    call(admin, 'GET', '/admin/media/processing-failures')
    pay(student, identity)
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        futures = [
            pool.submit(
                call, student, 'POST', '/simulator/payments/' + identity + '/confirm'
            )
            for _ in range(20)
        ]
        for future in futures:
            future.result()
    eventually(
        lambda: scalar('SELECT status FROM `order` WHERE id=' + identity) == '2',
        'payment status',
    )
    detail = scalar('SELECT id FROM order_detail WHERE order_id=' + identity)
    eventually(
        lambda: scalar(
            'SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id='
            + detail
            + ' AND active=1',
            'tj_learning',
        )
        == '1',
        'grant',
    )
    assert (
        scalar(
            "SELECT COUNT(*) FROM reliability_outbox WHERE business_key='order:"
            + identity
            + ":paid'"
        )
        == '1'
    )
    assert scalar('SELECT COUNT(*) FROM payment_fact WHERE order_id=' + identity) == '1'
    paid = call(student, 'GET', '/orders/' + identity)
    assert paid['paidAt'] and not paid['paymentNeedsReview']
    assert paid['details'][0]['entitlement']['state'] == 'ACTIVE'
    assert paid['details'][0]['entitlement']['canLearn'] is True
    assert all(str(row['orderId']) != identity for row in call(admin, 'GET', '/admin/dashboard/pending-entitlements')['list'])
    # Repeated asynchronous refund command shares operation and result.
    key = uuid.uuid4().hex
    body = dict(
        orderDetailId=detail,
        refundReason='Acceptance',
        questionDesc='Isolated verification',
    )
    request = call(student, 'POST', '/services/trade/refund-apply', body, key, 202)
    assert (
        request['operationId']
        == call(student, 'POST', '/services/trade/refund-apply', body, key, 202)[
            'operationId'
        ]
    )
    assert wait(student, 'trade', request)['status'] == 'SUCCEEDED'
    refund = scalar(
        'SELECT id FROM refund_apply WHERE order_detail_id='
        + detail
        + ' ORDER BY id DESC LIMIT 1'
    )
    pending = call(student, 'GET', '/orders/' + identity)['details'][0]
    assert pending['refunds'][0]['status'] == 1
    assert str(pending['refunds'][0]['id']) == refund
    assert len(pending['refunds'][0]['nodes']) == 1
    queued = call(admin, 'GET', '/admin/trade/refund-apply/page?refundStatus=1&id=' + refund)
    assert [str(item['id']) for item in queued['list']] == [refund]
    approved = call(
        admin,
        'PUT',
        '/services/trade/refund-apply/approval',
        dict(id=refund, approveType=1, approveOpinion='Acceptance'),
        uuid.uuid4().hex,
        202,
    )
    assert wait(admin, 'trade', approved)['status'] == 'SUCCEEDED'
    eventually(
        lambda: scalar('SELECT status FROM refund_apply WHERE id=' + refund) == '5',
        'refund terminal',
    )
    eventually(
        lambda: scalar('SELECT status FROM `order` WHERE id=' + identity) == '7',
        'refunded order terminal',
    )
    eventually(
        lambda: scalar(
            'SELECT active FROM learning_entitlement WHERE order_detail_id=' + detail,
            'tj_learning',
        )
        == '0',
        'target entitlement revoked',
    )
    assert (
        scalar(
            'SELECT COUNT(*) FROM learning_lesson WHERE user_id='
            + accounts['student']['id']
            + ' AND course_id=1',
            'tj_learning',
        )
        == '1'
    )
    refunded = call(student, 'GET', '/orders/' + identity)['details'][0]
    assert refunded['refunds'][0]['status'] == 5 and len(refunded['refunds'][0]['nodes']) == 3
    assert refunded['entitlement']['state'] == 'REVOKED'
    assert call(admin, 'GET', '/admin/trade/refund-apply/page?refundStatus=1&id=' + refund)['list'] == []
    assert scalar("SELECT COUNT(*) FROM reliability_outbox WHERE business_key LIKE 'refund:" + refund + ":notice:%'") == '3'
    # Late payment is a persisted manual case and must not grant an entitlement.
    closed = order(student)
    pay(student, closed)
    call(student, 'PUT', '/orders/' + closed + '/cancel')
    call(student, 'POST', '/simulator/payments/' + closed + '/confirm')
    eventually(
        lambda: scalar('SELECT COUNT(*) FROM payment_conflict WHERE order_id=' + closed)
        == '1',
        'late payment case',
    )
    assert scalar('SELECT status FROM `order` WHERE id=' + closed) == '3'
    late = call(student, 'GET', '/orders/' + closed)
    assert late['paidAt'] and late['paymentNeedsReview']
    assert late['details'][0]['entitlement']['state'] == 'NOT_GRANTED'
    closed_detail = scalar('SELECT id FROM order_detail WHERE order_id=' + closed)
    assert (
        scalar(
            'SELECT COUNT(*) FROM learning_entitlement WHERE order_detail_id='
            + closed_detail,
            'tj_learning',
        )
        == '0'
    )
    assert (
        scalar(
            'SELECT COUNT(*) FROM refund_apply WHERE order_detail_id=' + closed_detail
        )
        == '0'
    )
    # Two distinct refund callbacks compete for the same parent order. Duplicate approvals retain their operation.
    fixture = json.loads((LOCAL / 'browser-fixture.json').read_text())
    dual = order(student, ['1', fixture['course']])
    pay(student, dual)
    call(student, 'POST', '/simulator/payments/' + dual + '/confirm')
    eventually(
        lambda: scalar('SELECT status FROM `order` WHERE id=' + dual) == '2',
        'two-detail payment',
    )
    details = scalar('SELECT id FROM order_detail WHERE order_id=' + dual).splitlines()
    assert len(details) == 2
    requests = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        for result in [
            pool.submit(
                call,
                student,
                'POST',
                '/services/trade/refund-apply',
                dict(
                    orderDetailId=d,
                    refundReason='Concurrent acceptance',
                    questionDesc='Two details',
                ),
                uuid.uuid4().hex,
                202,
            )
            for d in details
        ]:
            requests.append(result.result())
    for result in requests:
        assert wait(student, 'trade', result)['status'] == 'SUCCEEDED'
    refunds = [
        scalar(
            'SELECT id FROM refund_apply WHERE order_detail_id='
            + d
            + ' ORDER BY id DESC LIMIT 1'
        )
        for d in details
    ]
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        approvals = []
        for r in refunds:
            key = uuid.uuid4().hex
            body = dict(id=r, approveType=1, approveOpinion='Concurrent acceptance')
            approvals.extend(
                [
                    pool.submit(
                        call,
                        admin,
                        'PUT',
                        '/services/trade/refund-apply/approval',
                        body,
                        key,
                        202,
                    )
                    for _ in range(10)
                ]
            )
        results = [result.result() for result in approvals]
    assert len(set(result['operationId'] for result in results)) == 2
    for result in results:
        assert wait(admin, 'trade', result)['status'] == 'SUCCEEDED'
    eventually(
        lambda: scalar('SELECT status FROM `order` WHERE id=' + dual) == '7',
        'concurrent callbacks aggregate refunded terminal',
        90,
    )
    for d in details:
        assert scalar('SELECT status FROM order_detail WHERE id=' + d) == '7'
        eventually(
            lambda: scalar(
                'SELECT active FROM learning_entitlement WHERE order_detail_id=' + d,
                'tj_learning',
            )
            == '0',
            'target entitlement revoked',
        )
    # A partially refunded order exposes per-course status while the user's other order is still active.
    mixed = order(student, ['1', fixture['course']])
    alternate = order(student)
    for number in [mixed, alternate]:
        pay(student, number)
        call(student, 'POST', '/simulator/payments/' + number + '/confirm')
        eventually(lambda: all(
            item['entitlement']['state'] == 'ACTIVE'
            for item in call(student, 'GET', '/orders/' + number)['details']
        ), 'orders independently grant course entitlements')
    mixed_view = call(student, 'GET', '/orders/' + mixed)
    target = next(item for item in mixed_view['details'] if str(item['courseId']) == '1')
    requested = call(student, 'POST', '/services/trade/refund-apply', dict(
        orderDetailId=str(target['id']), refundReason='Partial acceptance', questionDesc='Retain the other courses',
    ), uuid.uuid4().hex, 202)
    assert wait(student, 'trade', requested)['status'] == 'SUCCEEDED'
    lines = call(student, 'GET', '/orders/' + mixed)['details']
    partial_refund = next(line['refunds'][0]['id'] for line in lines if line['id'] == target['id'])
    approved = call(admin, 'PUT', '/services/trade/refund-apply/approval', dict(
        id=str(partial_refund), approveType=1, approveOpinion='Partial acceptance',
    ), uuid.uuid4().hex, 202)
    assert wait(admin, 'trade', approved)['status'] == 'SUCCEEDED'
    eventually(lambda: any(
        str(item['courseId']) == '1' and item['entitlement']['state'] == 'REVOKED'
        for item in call(student, 'GET', '/orders/' + mixed)['details']
    ), 'refund propagates on exactly one order line')
    mixed_view = call(student, 'GET', '/orders/' + mixed)
    mixed_items = {str(item['courseId']): item for item in mixed_view['details']}
    assert mixed_view['status'] == 2
    assert mixed_items['1']['refunds'][0]['status'] == 5
    assert mixed_items['1']['entitlement']['otherOrderActive'] is True
    assert mixed_items['1']['entitlement']['canLearn'] is True
    assert mixed_items[str(fixture['course'])]['entitlement']['state'] == 'ACTIVE'
    evidence = dict(
        status='PASSED',
        paidOrder=identity,
        refundedDetail=detail,
        latePaidOrder=closed,
        concurrentRefundOrder=dual,
        partialRefundOrder=mixed,
        checks=[
            'idempotent order',
            '20 repeated payment confirmations',
            'one payment fact and paid event',
            'grant by order detail',
            'idempotent refund command',
            'payment fact, per-course entitlement and refund progress through the student API',
            'admin queue access and approved refund removed from pending view',
            'one notice per refund state transition',
            'partial refund preserves sibling course and an independent order still grants the same course',
            'refund terminal and entitlement revoke',
            'learning history preserved',
            'concurrent two-detail refunds and 20 repeated approvals aggregate one terminal order',
            'cancelled late payment manual case without automatic grant or refund',
        ],
    )
    (LOCAL / 'financial-smoke.json').write_text(json.dumps(evidence, indent=2))
    print('Financial gateway + RabbitMQ smoke PASSED', flush=True)


if __name__ == '__main__':
    main()
