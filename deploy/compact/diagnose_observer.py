"""Diagnostic-only timing of the frozen workload's database observation stages.

Never a formal acceptance entry point: both the original elapsed outcomes and
the instrumentation are retained, without excluding observation or failures.
"""
import collections
import argparse
import json
from pathlib import Path
import sys
import threading
import time
import mixed_load
from setup import LOCAL, OFFSET, MODULES


class TimedObserver(mixed_load.Observer):
    connections = 4
    ping_each_read = True
    timings = collections.defaultdict(list)
    timing_lock = threading.Lock()

    def __init__(self, password):
        super().__init__(password,connections=type(self).connections)

    @classmethod
    def record(cls, key, started):
        with cls.timing_lock:
            cls.timings[key].append((time.perf_counter() - started) * 1000)

    def scalar(self, sql, values=()):
        started = time.perf_counter()
        connection = self.pool.get(timeout=10)
        self.record('connection_wait', started)
        try:
            if self.ping_each_read:
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
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument('--observer-connections', type=int, choices=[4, 16], default=4)
    parser.add_argument('--no-ping', action='store_true')
    options, remaining = parser.parse_known_args()
    sys.argv = [sys.argv[0], *remaining]
    TimedObserver.connections = options.observer_connections
    TimedObserver.ping_each_read = not options.no_ping
    database_count = sum(item[3] is not None and alias not in {'data', 'gateway'}
                         for alias, item in MODULES.items())
    if database_count * 8 + TimedObserver.connections + 16 > 160:
        raise SystemExit('Diagnostic exceeds the database connection ceiling')
    if '--protocol' not in sys.argv or sys.argv[sys.argv.index('--protocol') + 1] != 'smoke':
        raise SystemExit('Observer instrumentation is permitted only with --protocol smoke')
    mixed_load.Observer = TimedObserver
    directory = LOCAL / 'observer-diagnostics' / str(time.time_ns())
    directory.mkdir(parents=True)
    try:
        result = mixed_load.main()
    finally:
        summary = {'status': 'DIAGNOSTIC', 'formalAcceptance': False,
                   'observerConnections': TimedObserver.connections,
                   'pingEachRead': TimedObserver.ping_each_read, 'stagesMs': {}}
        for key, values in TimedObserver.timings.items():
            ordered = sorted(values)
            summary['stagesMs'][key] = {
                'count': len(values), 'p50': ordered[int((len(values)-1)*.5)],
                'p95': ordered[int((len(values)-1)*.95)], 'max': max(values),
            }
        (directory / 'report.json').write_text(json.dumps(summary, indent=2), encoding='utf8')
        print('Observer diagnostic: ' + str(directory), flush=True)
    raise SystemExit(result)
