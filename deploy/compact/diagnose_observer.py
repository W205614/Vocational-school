"""Diagnostic-only timing of the frozen workload's database observation stages.

Never a formal acceptance entry point: both the original elapsed outcomes and
the instrumentation are retained, without excluding observation or failures.
"""
import collections
import json
from pathlib import Path
import sys
import threading
import time
import mixed_load
from setup import LOCAL


class TimedObserver(mixed_load.Observer):
    timings = collections.defaultdict(list)
    timing_lock = threading.Lock()

    @classmethod
    def record(cls, key, started):
        with cls.timing_lock:
            cls.timings[key].append((time.perf_counter() - started) * 1000)

    def scalar(self, sql, values=()):
        started = time.perf_counter()
        connection = self.pool.get(timeout=10)
        self.record('connection_wait', started)
        try:
            started = time.perf_counter()
            connection.ping(reconnect=True)
            self.record('ping', started)
            started = time.perf_counter()
            with connection.cursor() as cursor:
                cursor.execute(sql, values or None)
                row = cursor.fetchone()
                return row[0] if row else None
        finally:
            self.record('execute_and_fetch', started)
            self.pool.put(connection)


if __name__ == '__main__':
    if '--protocol' not in sys.argv or sys.argv[sys.argv.index('--protocol') + 1] != 'smoke':
        raise SystemExit('Observer instrumentation is permitted only with --protocol smoke')
    mixed_load.Observer = TimedObserver
    directory = LOCAL / 'observer-diagnostics' / str(time.time_ns())
    directory.mkdir(parents=True)
    try:
        result = mixed_load.main()
    finally:
        summary = {'status': 'DIAGNOSTIC', 'formalAcceptance': False, 'stagesMs': {}}
        for key, values in TimedObserver.timings.items():
            ordered = sorted(values)
            summary['stagesMs'][key] = {
                'count': len(values), 'p50': ordered[int((len(values)-1)*.5)],
                'p95': ordered[int((len(values)-1)*.95)], 'max': max(values),
            }
        (directory / 'report.json').write_text(json.dumps(summary, indent=2), encoding='utf8')
        print('Observer diagnostic: ' + str(directory), flush=True)
    raise SystemExit(result)
