"""Real gateway workflows. An accepted command is never counted as business completion."""
import datetime
import json
import time
import uuid
import requests

class WorkloadFailure(Exception):
    def __init__(self, kind): self.kind = kind; super().__init__(kind)

class Actor:
    def __init__(self, index, profile, account, fixture, base, stats, observe):
        self.index = index; self.profile = profile; self.account = account
        self.fixture = fixture; self.base = base.rstrip('/') + '/api/v2'; self.stats = stats; self.observe = observe
        self.clients = {}; self.cycle = 0; self.refresh_at = {}

    def login(self, role='student'):
        account = self.account if role == 'student' else self.account[role + 'Account']
        client = requests.Session()
        endpoint = '/auth/accounts/login' if role == 'student' else '/auth/accounts/admin/login'
        response = client.post(self.base + endpoint, json={'type': 1, 'username': account['username'], 'password': account['password']}, timeout=15)
        if not response.ok or response.json().get('code') != 200: raise WorkloadFailure('login')
        client.headers['Authorization'] = 'Bearer ' + response.json()['data']
        self.clients[role] = client; self.refresh_at[role] = time.monotonic() + 180

    def request(self, method, path, body=None, role='student', key=None, query=None):
        client = self.clients[role]
        if time.monotonic() >= self.refresh_at[role]:
            response = client.get(self.base + '/auth/accounts/refresh', params={'audience': 'student' if role == 'student' else 'admin'}, timeout=10)
            if not response.ok: raise WorkloadFailure('refresh')
            client.headers['Authorization'] = 'Bearer ' + response.json()['data']; self.refresh_at[role] = time.monotonic() + 180
        started = time.monotonic()
        try:
            response = client.request(method, self.base + path, json=body, headers={'Idempotency-Key': key} if key else {}, timeout=10)
            data = response.json()
            outcome = 'ok' if response.ok and data.get('code') == 200 else 'admission' if response.status_code == 429 else 'business' if 400 <= response.status_code < 500 else 'system'
            if query: self.stats.add('query/' + query, time.monotonic() - started, outcome)
            if outcome != 'ok': raise WorkloadFailure(outcome + '-HTTP-' + str(response.status_code) + '-' + path.split('?')[0])
            return data.get('data')
        except requests.RequestException:
            if query: self.stats.add('query/' + query, time.monotonic() - started, 'system')
            raise WorkloadFailure('transport') from None

    def until(self, callback, predicate, deadline):
        while time.monotonic() < deadline:
            value = callback()
            if predicate(value): return value
            time.sleep(.2)
        raise WorkloadFailure('completion-timeout')

    def command(self, kind, service, path, body=None, role='student', method='POST'):
        started = time.monotonic()
        try:
            op = self.request(method, path, body, role, str(uuid.uuid4()))
            if isinstance(op, dict) and 'operationId' in op:
                op = self.until(lambda: self.request('GET', '/operations/' + service + '/' + op['operationId'], role=role), lambda value: value['status'] != 'PENDING', started + 30)
                if op['status'] != 'SUCCEEDED': raise WorkloadFailure('operation-failed')
                value = op.get('result')
                if isinstance(value, str): value = json.loads(value)
            else: value = op
            return started, value
        except Exception:
            self.stats.add('async/' + kind, time.monotonic() - started, 'system'); raise

    def completed(self, kind, started): self.stats.add('async/' + kind, time.monotonic() - started, 'ok')

    def step(self, warm=False):
        f = self.fixture; cycle = self.cycle
        if warm or self.profile == 'query':
            choices = [('profile', '/services/user/users/me'), ('lessons', '/services/learning/lessons/page'), ('notes', '/notes'), ('course', '/services/course/course/' + f['course']), ('search', '/services/search/courses/portal?pageNo=1&pageSize=10')]
            kind, path = choices[cycle % len(choices)]; self.request('GET', path, query=kind)
        elif self.profile == 'notes':
            content = 'opt-note-' + str(self.index) + '-' + str(cycle)
            started, value = self.command('notes', 'learning', '/notes', {'courseId': f['notesCourse'], 'content': content})
            self.until(lambda: self.observe("SELECT COUNT(*) FROM tj_learning.course_note WHERE user_id=%s AND content=%s", (self.account['id'], content)), bool, started + 30)
            self.request('GET', '/notes', query='notes'); self.completed('notes', started)
        elif self.profile == 'progress':
            moment = cycle + 1; lesson = self.account['lessons'][f['course']]
            started = time.monotonic()
            self.request('POST', '/services/learning/learning-records', {'sectionType': 1, 'lessonId': lesson, 'sectionId': f['video'], 'duration': f['videoDuration'], 'moment': moment, 'commitTime': datetime.datetime.now().isoformat()})
            self.until(lambda: self.observe('SELECT MAX(moment) FROM tj_learning.learning_record WHERE lesson_id=%s AND section_id=%s', (lesson, f['video'])), lambda value: value is not None and value >= moment, started + 30)
            self.request('GET', '/services/learning/learning-records/course/' + f['course'], query='learning'); self.completed('progress', started)
        elif self.profile == 'exam':
            if cycle >= f['cyclesPerActor']: raise WorkloadFailure('fixture-exhausted')
            paper = str(int(f['paperStart']) + cycle)
            started, attempt = self.command('exam-start', 'exam', '/exam-papers/' + paper + '/attempts')
            attempt_id = str(attempt['id']); self.completed('exam-start', started)
            started, _ = self.command('exam-submit', 'exam', '/exam-attempts/' + attempt_id + '/submit', {'answers': {f['objective']: '1,2', f['subjective']: 'reliable transactions'}, 'version': 0})
            self.completed('exam-submit', started)
            started, _ = self.command('exam-grade', 'exam', '/teacher/exam-attempts/' + attempt_id + '/grades', {'questionId': f['subjective'], 'score': 10, 'version': 0, 'feedback': 'synthetic load'}, role='teacher')
            value = self.request('GET', '/exam-attempts/' + attempt_id, query='exam-attempt')
            if value['status'] != 'FINISHED' or not value['passed']: raise WorkloadFailure('exam-invariant')
            self.until(lambda: self.observe('SELECT COUNT(*) FROM tj_exam.reliability_outbox o JOIN tj_learning.reliability_inbox i ON i.event_id COLLATE utf8mb4_bin=o.event_id COLLATE utf8mb4_bin WHERE o.business_key=%s', ('exam:' + attempt_id + ':passed',)), bool, started + 30)
            self.completed('exam-grade', started)
        elif self.profile == 'coupon':
            if cycle >= f['cyclesPerActor']: raise WorkloadFailure('fixture-exhausted')
            coupon = str(int(f['couponStart']) + cycle)
            started, _ = self.command('coupon', 'promotion', '/coupons/' + coupon + '/claims')
            if self.observe('SELECT COUNT(*) FROM tj_promotion.user_coupon WHERE user_id=%s AND coupon_id=%s', (self.account['id'], coupon)) != 1: raise WorkloadFailure('coupon-invariant')
            self.completed('coupon', started)
        elif self.profile == 'trade':
            order = str(int(f['tradeOrderStart']) + self.index * f['cyclesPerActor'] + cycle)
            started, _ = self.command('order-create', 'trade', '/orders', {'orderId': order, 'courseIds': [f['course']], 'couponIds': []})
            detail = self.request('GET', '/orders/' + order, query='order'); self.completed('order-create', started)
            started = time.monotonic()
            link = self.request('POST', '/services/trade/pay/order', {'orderId': order, 'payChannelCode': 'mockPay'})
            self.pay(link)
            self.until(lambda: self.request('GET', '/orders/' + order), lambda value: value['status'] == 2, started + 30)
            detail = self.request('GET', '/orders/' + order, query='order')
            refund_detail = str(detail['details'][0]['id'])
            self.until(lambda: self.observe('SELECT active FROM tj_learning.learning_entitlement WHERE order_detail_id=%s', (refund_detail,)), lambda value: value == 1, started + 30)
            self.completed('payment', started)
            started = time.monotonic()
            accepted, _ = self.command('refund-apply', 'trade', '/services/trade/refund-apply', {'orderDetailId': refund_detail, 'refundReason': 'synthetic load', 'questionDesc': 'synthetic load'})
            self.completed('refund-apply', accepted)
            refund_id = self.observe('SELECT id FROM tj_trade.refund_apply WHERE order_detail_id=%s ORDER BY id DESC LIMIT 1', (refund_detail,))
            approved, _ = self.command('refund-approve', 'trade', '/admin/trade/refund-apply/approval', {'id': str(refund_id), 'approveType': 1, 'approveOpinion': 'synthetic load'}, role='admin', method='PUT')
            self.completed('refund-approve', approved)
            # Baseline compensation can take a second 30-second lease. Retain that latency
            # as a failed 3-second target, rather than discarding the completed sample.
            self.until(lambda: self.request('GET', '/orders/' + order), lambda value: value['status'] == 7, started + 60)
            self.until(lambda: self.observe('SELECT active FROM tj_learning.learning_entitlement WHERE order_detail_id=%s', (refund_detail,)), lambda value: value == 0, started + 60)
            self.completed('refund', started)

    def pay(self, link):
        from urllib.parse import urlparse
        parsed = urlparse(link)
        path = parsed.path.removeprefix('/api/v2')
        self.request('POST', '/simulator/payments/' + path.rsplit('/', 1)[-1] + '/confirm')
