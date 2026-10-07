"""Versioned gateway workload with isolated reset, durable completion and bound reports."""
import argparse
import collections
import concurrent.futures
import hashlib
import json
import math
from pathlib import Path
import queue
import random
import threading
import time
import sys
import traceback
# Optional project-local dependency installation avoids changing the host Conda environment.
sys.path.insert(0, str(Path(__file__).resolve().parent / '.local/python-deps'))
import pymysql
from setup import LOCAL, ROOT, PORTS, OFFSET, COMPOSE, secrets_config
from evidence import manifest, bind, utcnow
from perf_protocol import allocation, schedule, check_configuration
from load_workflows import Actor
from load_telemetry import Telemetry

def workload_fingerprint():
    source = Path(__file__).resolve().parent
    names = ('mixed_load.py', 'load_workflows.py', 'load_telemetry.py', 'perf_protocol.py', 'mixed_fixture.py')
    return hashlib.sha256(json.dumps([(name, hashlib.sha256((source / name).read_bytes()).hexdigest()) for name in names]).encode()).hexdigest()

class Stats:
    def __init__(self):
        self.lock = threading.Lock(); self.histograms = collections.defaultdict(collections.Counter)
        self.counts = collections.Counter(); self.workflows = collections.Counter()
    def add(self, kind, elapsed, outcome):
        with self.lock:
            self.histograms[kind + ':' + outcome][round(elapsed * 1000)] += 1
            self.counts[kind + ':' + outcome] += 1
    def result(self):
        def percentile(histogram, fraction):
            target = math.ceil(sum(histogram.values()) * fraction); total = 0
            for value, count in sorted(histogram.items()):
                total += count
                if total >= target: return value
        return {'counts': dict(self.counts), 'workflowCounts': dict(self.workflows),
                'latencyMs': {name: {'count': sum(histogram.values()), 'p50': percentile(histogram, .5),
                                     'p95': percentile(histogram, .95), 'p99': percentile(histogram, .99)}
                              for name, histogram in self.histograms.items()}}

class Observer:
    def __init__(self, password):
        self.pool = queue.Queue()
        for _ in range(4):
            self.pool.put(pymysql.connect(host='127.0.0.1', port=24316 + OFFSET, user='root', password=password,
                                          autocommit=True, charset='utf8mb4', read_timeout=10))
    def scalar(self, sql, values=()):
        connection = self.pool.get(timeout=10)
        try:
            connection.ping(reconnect=True)
            with connection.cursor() as cursor:
                cursor.execute(sql, values or None); row = cursor.fetchone()
                return row[0] if row else None
        finally: self.pool.put(connection)
    def close(self):
        while not self.pool.empty(): self.pool.get().close()
    def drain(self):
        databases = self.scalar("SELECT GROUP_CONCAT(DISTINCT table_schema) FROM information_schema.tables WHERE table_name='reliability_outbox' AND table_schema LIKE 'tj\\_%'")
        schemas = databases.split(',') if databases else []
        end = time.monotonic() + 120
        while time.monotonic() < end:
            pending = sum(self.scalar('SELECT COUNT(*) FROM ' + schema + '.reliability_outbox WHERE status<>\'SENT\'') +
                          self.scalar('SELECT COUNT(*) FROM ' + schema + '.reliability_operation WHERE status=\'PENDING\'') for schema in schemas)
            if pending == 0: return True
            time.sleep(.5)
        return False
    def invariants(self):
        return all(self.scalar(sql) == 0 for sql in [
            'SELECT COUNT(*) FROM tj_promotion.coupon WHERE issue_num>total_num OR used_num>issue_num',
            'SELECT COUNT(*) FROM (SELECT coupon_id,user_id,COUNT(*) n FROM tj_promotion.user_coupon WHERE coupon_id>=850000000000200000 AND coupon_id<850000000000210000 GROUP BY coupon_id,user_id HAVING n>1) duplicate_claims',
            'SELECT COUNT(*) FROM tj_learning.learning_lesson l JOIN tj_course.course c ON c.id=l.course_id WHERE l.user_id>=850000000000000000 AND l.user_id<850000000000000200 AND l.learned_sections>c.section_num',
            'SELECT COUNT(*) FROM tj_exam.exam_attempt WHERE user_id>=850000000000000000 AND user_id<850000000000000200 AND status=\'FINISHED\' AND (score<>20 OR passed<>1)',
            'SELECT COUNT(*) FROM tj_learning.learning_lesson l LEFT JOIN (SELECT user_id,course_id,COUNT(*) n,SUM(expires_at IS NULL) permanent,MAX(expires_at) expiry FROM tj_learning.learning_entitlement WHERE active=1 AND (expires_at IS NULL OR expires_at>NOW()) GROUP BY user_id,course_id) e ON e.user_id=l.user_id AND e.course_id=l.course_id WHERE l.user_id>=850000000000000000 AND l.user_id<850000000000000200 AND ((COALESCE(e.n,0)=0 AND l.status<>3) OR (COALESCE(e.n,0)>0 AND (l.status=3 OR (e.permanent>0 AND l.expire_time IS NOT NULL) OR (e.permanent=0 AND (l.expire_time IS NULL OR ABS(TIMESTAMPDIFF(SECOND,l.expire_time,e.expiry))>1)))))',
        ])

def worker(actor, end, warm=False):
    while time.monotonic() < end:
        started = time.monotonic()
        try:
            actor.step(warm)
            if not warm:
                with actor.stats.lock: actor.stats.workflows[actor.profile] += 1
        except Exception as error:
            actor.stats.add('workflow/' + actor.profile, time.monotonic() - started, getattr(error, 'kind', type(error).__name__))
        finally:
            actor.cycle += 1
        time.sleep(.25)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--protocol', choices=['perf-3h-v1', 'perf-long-v1', 'smoke'], default='perf-3h-v1')
    parser.add_argument('--snapshot', required=True, type=Path)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--users', type=int, default=10); parser.add_argument('--seconds', type=int, default=30)
    parser.add_argument('--warm-seconds', type=int, default=300)
    parser.add_argument('--skip-reset', action='store_true')
    parser.add_argument('--batch-manifest', type=Path)
    args = parser.parse_args()
    if args.protocol != 'smoke' and (args.skip_reset or args.warm_seconds != 300):
        parser.error('Formal protocols require a reset and five-minute warm-up per run')
    if args.protocol != 'smoke' and args.batch_manifest is None:
        parser.error('Formal measurements require the shared audit batch manifest')
    import benchmark_snapshot
    benchmark_snapshot.guard()
    env, _ = secrets_config()
    runtime = json.loads((LOCAL / 'benchmark-runtime.json').read_text(encoding='utf8'))
    fixture = json.loads((LOCAL / 'load-fixture.json').read_text(encoding='utf8'))
    accounts = json.loads((LOCAL / 'load-accounts.json').read_text(encoding='utf8'))
    planned = [(args.users, args.seconds, 1)] if args.protocol == 'smoke' else schedule(args.protocol)
    output = LOCAL / 'performance' / (args.label + '-' + str(time.time_ns()))
    output.mkdir(parents=True, exist_ok=False)
    report = {'protocol': args.protocol, 'label': args.label, 'mode': runtime['mode'], 'status': 'FAILED', 'runs': [],
              'workloadFingerprint': workload_fingerprint(),
              'fixtureFingerprint': hashlib.sha256((LOCAL / 'load-fixture.json').read_bytes()).hexdigest(),
              'workload': {'seed': fixture['seed'], 'thinkSeconds': .25, 'profiles': allocation(200)},
              'scope': 'Short-cycle capacity comparison; not a long-duration leak or backlog proof'}
    started = utcnow(); expected = None
    try:
        for users, seconds, repeat in planned:
            if workload_fingerprint() != report['workloadFingerprint']: raise RuntimeError('Workload changed during protocol')
            if not args.skip_reset: benchmark_snapshot.restore(args.snapshot)
            if expected is None:
                expected = manifest(LOCAL, COMPOSE, ROOT, args.snapshot)
                build = json.loads((LOCAL / 'build-source.json').read_text(encoding='utf8'))
                actual = {name: image for name, image in expected['imageDigests'].items() if name.startswith(('app-', 'web-'))}
                if build['sourceCommit'] != args.source_commit or build['imageDigests'] != actual:
                    raise RuntimeError('Frozen build/source binding mismatch')
                expected['sourceCommit'] = build['sourceCommit']
                if args.batch_manifest:
                    batch = json.loads(args.batch_manifest.read_text(encoding='utf8'))
                    expected['releaseRunId'] = batch['releaseRunId']
                report['manifest'] = expected
            stats = Stats(); observer = Observer(env['ACCEPTANCE_DB_PASSWORD'])
            actors = []; profiles = allocation(users)
            roles = [name for name, count in profiles.items() for _ in range(count)]
            random.Random(fixture['seed']).shuffle(roles)
            try:
                role_numbers = collections.Counter()
                for index, profile in enumerate(roles):
                    account = dict(accounts[index])
                    if profile in ('exam', 'trade'):
                        role = 'teacher' if profile == 'exam' else 'admin'
                        account[role + 'Account'] = accounts[role_numbers[profile]][role + 'Account']
                        role_numbers[profile] += 1
                    actor = Actor(index, profile, account, fixture, 'http://127.0.0.1:' + str(PORTS['gateway']), stats, observer.scalar)
                    actor.login(); time.sleep(1.1)
                    if profile in ('exam', 'trade'):
                        actor.login('teacher' if profile == 'exam' else 'admin'); time.sleep(1.1)
                    actors.append(actor)
                warm = Stats()
                for actor in actors: actor.stats = warm
                with concurrent.futures.ThreadPoolExecutor(max_workers=users) as pool:
                    end = time.monotonic() + args.warm_seconds
                    list(pool.map(lambda actor: worker(actor, end, True), actors))
                    if any(value for name, value in warm.counts.items() if not name.endswith(':ok')):
                        raise RuntimeError('Warm-up correctness failed')
                    for actor in actors: actor.stats = stats; actor.cycle = 0
                    telemetry = Telemetry(COMPOSE, runtime, env['ACCEPTANCE_INTERNAL_TOKEN']); telemetry.start()
                    measure_started = time.monotonic(); sample_started = utcnow(); end = measure_started + seconds
                    futures = [pool.submit(worker, actor, end) for actor in actors]
                    for future in futures: future.result()
                    measured = time.monotonic()
                    measurements = telemetry.finish(end, users)
                drained = observer.drain()
                run = {'users': users, 'repeat': repeat, 'requestedSeconds': seconds, 'elapsedSeconds': measured - measure_started,
                       'sampleStartedAt': sample_started, 'sampleFinishedAt': utcnow(),
                       'status': 'COMPLETED', 'profiles': profiles, 'queuesDrained': drained, 'invariants': drained and observer.invariants(), **stats.result(), **measurements}
                # A worker cannot silently omit a profile while the allocation still looks correct.
                if any(run['workflowCounts'].get(profile, 0) == 0 for profile in profiles): run['invariants'] = False
                report['runs'].append(run)
                (output / 'results.json').write_text(json.dumps(report, indent=2), encoding='utf8')
                print(f"Measured {args.label}: {users} users, repeat {repeat}, outcomes={dict(stats.counts)}", flush=True)
            finally:
                observer.close()
                for actor in actors:
                    for client in actor.clients.values(): client.close()
        report['failures'] = check_configuration(report) if args.protocol != 'smoke' else [name for run in report['runs'] for name, value in run['counts'].items() if value and not name.endswith(':ok')]
        report['status'] = 'PASSED' if not report['failures'] else 'FAILED'
    except BaseException as error:
        report['status'] = 'FAILED'; report['failureType'] = type(error).__name__
        (output / 'failure.log').write_text(traceback.format_exc(), encoding='utf8')
        if isinstance(error, KeyboardInterrupt): raise
        print('Measurement failed; private diagnostics and partial evidence retained: ' + type(error).__name__, flush=True)
    finally:
        if expected is not None: report = bind(report, expected, started=expected['createdAt'])
        (output / 'report.json').write_text(json.dumps(report, indent=2), encoding='utf8')
        (LOCAL / ('performance-' + args.label + '.json')).write_text(json.dumps({'path': str(output / 'report.json')}, indent=2), encoding='utf8')
    print('Performance result: ' + report['status'] + '; ' + str(output / 'report.json'), flush=True)
    return 0 if report['status'] == 'PASSED' else 1

if __name__ == '__main__': raise SystemExit(main())
