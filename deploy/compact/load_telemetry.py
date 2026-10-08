"""Independent telemetry collector: RSS means process RSS, never a container limit."""
import datetime
import json
from statistics import median
import subprocess
import threading
import time
import requests

class Telemetry:
    def __init__(self, compose, runtime, token):
        self.compose = compose; self.runtime = runtime; self.token = token
        self.samples = []; self.errors = []; self.stop = threading.Event()
        self.thread = threading.Thread(target=self.collect, daemon=True)
        self.initial_background = None
        self.host_cpus = int(subprocess.check_output(['docker', 'info', '--format', '{{.NCPU}}'], text=True).strip())

    def sample(self):
        ids = subprocess.check_output(self.compose + ['ps', '-q'], text=True).splitlines()
        own = set(ids)
        all_ids = subprocess.check_output(['docker', 'ps', '-q', '--no-trunc'], text=True).splitlines()
        background = sorted(set(all_ids) - own)
        if self.initial_background is None: self.initial_background = background
        value = {'monotonic': time.monotonic(), 'at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'javaRssBytes': 0,
                 'containers': [], 'metrics': {}, 'backgroundChanged': background != self.initial_background}
        raw = subprocess.check_output(['docker', 'stats', '--no-stream', '--format', '{{json .}}', *all_ids], text=True)
        value['containers'] = [json.loads(line) for line in raw.splitlines()]
        background_short = {identifier[:12] for identifier in background}
        value['backgroundCpuPercent'] = sum(float(row['CPUPerc'].rstrip('%')) for row in value['containers'] if row.get('ID') in background_short)
        for service in self.runtime['javaServices']:
            container = subprocess.check_output(self.compose + ['ps', '-q', service], text=True).strip()
            if not container: raise RuntimeError('Missing Java process: ' + service)
            table = subprocess.check_output(['docker', 'top', container, '-eo', 'pid,comm,rss,args'], text=True, stderr=subprocess.PIPE)
            # Health probes are short-lived Java processes; measure only the application JVM.
            rss = [int(line.split()[2]) * 1024 for line in table.splitlines()[1:] if line.split()[1] == 'java' and '-jar /app/app.jar' in line]
            if len(rss) != 1: raise RuntimeError('Expected exactly one Java process: ' + service)
            value['javaRssBytes'] += rss[0]
        for alias, endpoint in self.runtime['metrics'].items():
            response = requests.get(endpoint, headers={'X-Internal-Token': self.token}, timeout=2)
            response.raise_for_status()
            value['metrics'][alias] = [line for line in response.text.splitlines() if not line.startswith('#') and any(name in line for name in ('hikaricp_connections_pending', 'executor_queued_tasks', 'tj_events_pending', 'tj_events_oldest_seconds', 'tj_operations_pending', 'jvm_gc_pause_seconds'))]
        return value

    def collect(self):
        while not self.stop.is_set():
            try: self.samples.append(self.sample())
            except Exception as error: self.errors.append({'type': type(error).__name__, 'reason': str(error)})
            self.stop.wait(10)

    def start(self): self.thread.start()
    def finish(self, end, users):
        self.stop.set(); self.thread.join(timeout=60)
        tail = [value['javaRssBytes'] for value in self.samples if end - 360 <= value['monotonic'] <= end]
        rss = None
        if users == 200 and len(tail) >= 6 and not self.errors:
            third = max(1, len(tail) // 3)
            # Continuing growth is insufficient evidence, not a memory saving.
            if median(tail[-third:]) <= median(tail[:third]) * 1.05:
                rss = median(tail)
        # docker CPU percentages use one core as 100%; a single busy core on a
        # 12-core host is not evidence that half of the machine was unavailable.
        heavy_background = sum(sample.get('backgroundCpuPercent', 0) > self.host_cpus * 50 for sample in self.samples) >= 3
        return {'samples': self.samples, 'telemetryErrors': self.errors, 'javaRssMedianBytes': rss,
                'hostCpus': self.host_cpus, 'backgroundCpuInvalidThresholdPercent': self.host_cpus * 50,
                'contaminated': heavy_background or any(sample['backgroundChanged'] for sample in self.samples)}
