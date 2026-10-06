"""Restart only identity-verified acceptance JVMs; bounded readiness checks and startup parallelism."""
import argparse,subprocess,time,requests,concurrent.futures
from start_service import MODULES
from pathlib import Path
PYTHON='E:/download/Anaconda/python.exe';BASE=Path(__file__).parent
def stop(name):subprocess.run([PYTHON,str(BASE/'stop_service.py'),name],check=True)
def start(name):
 subprocess.run([PYTHON,str(BASE/'start_service.py'),name],check=True)
 deadline=time.monotonic()+150;port=MODULES[name][2]
 while time.monotonic()<deadline:
  try:
   if requests.get(f'http://127.0.0.1:{port}/actuator/health/readiness',timeout=3).status_code==200:print(name+' ready',flush=True);return
  except requests.RequestException:pass
  time.sleep(2)
 raise RuntimeError(name+' did not become ready; inspect its private .local log')
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--only',default=','.join(MODULES));args=parser.parse_args();names=args.only.split(',')
 if len(set(names))!=len(names) or any(n not in MODULES for n in names):raise ValueError('Invalid acceptance services')
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:list(pool.map(stop,names))
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:list(pool.map(start,names))
if __name__=='__main__':main()
