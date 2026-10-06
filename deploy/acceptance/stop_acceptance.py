"""Stop only identity-checked acceptance processes/containers; preserve volumes and all other projects."""
import argparse,subprocess,json,sys,concurrent.futures
from prepare import BASE,LOCAL
from start_service import MODULES

def command(args):return subprocess.run(args,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout.decode().strip()
def stop_native(name):
 subprocess.run([sys.executable,str(BASE/'stop_service.py'),name],check=True)
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--keep-infra',action='store_true');args=parser.parse_args()
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:list(pool.map(stop_native,MODULES))
 simulator=LOCAL/'simulator-pid.json'
 if simulator.exists():
  pid=int(json.loads(simulator.read_text(encoding='utf8'))['pid'])
  expected=str(BASE/'simulator.py').replace("'","''")
  script="$p=Get-CimInstance Win32_Process -Filter 'ProcessId = "+str(pid)+"';if($p -and $p.Name -eq 'python.exe' -and $p.CommandLine.Contains('"+expected+"')){Stop-Process -Id $p.ProcessId;Write-Output 'Stopped isolated legacy simulator'}"
  subprocess.run(['powershell','-NoProfile','-Command',script],check=True)
 stopped=[]
 if not args.keep_infra:
  ids=command(['docker','ps','-q','--filter','label=com.docker.compose.project=vocational-acceptance']).split()
  allowed={'mysql','redis','rabbitmq','elasticsearch','prometheus','grafana'}|{'app-'+n for n in MODULES}|{'web-student','web-admin'}
  for identity in ids:
   info=json.loads(command(['docker','inspect',identity]))[0];labels=info['Config']['Labels']
   if labels.get('com.docker.compose.project')!='vocational-acceptance' or labels.get('com.docker.compose.service') not in allowed:raise RuntimeError('Unexpected acceptance container; refusing automatic stop')
  for identity in ids:
   command(['docker','stop','--time','30',identity]);stopped.append(identity)
  print('Stopped '+str(len(stopped))+' acceptance containers; persistent volumes retained',flush=True)
 print('Acceptance temporary resources stopped; original deployment and unrelated projects untouched',flush=True)
if __name__=='__main__':main()
