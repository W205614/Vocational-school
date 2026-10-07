"""Run an isolated recovery rehearsal and restore only previously running source services."""
import argparse,datetime,json,subprocess,sys
from pathlib import Path
from setup import COMPOSE,LOCAL,SOURCE,run

parser=argparse.ArgumentParser()
parser.add_argument('backup',type=Path)
parser.add_argument('--pause-source',action='store_true',help='Free Docker memory by temporarily stopping this source project only')
args=parser.parse_args()
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S')
home=LOCAL/('recovery-home-'+stamp);project='tianji-recovery-'+stamp
info={'home':str(home),'project':project,'backup':str(args.backup.resolve())}
LOCAL.mkdir(exist_ok=True)
(LOCAL/'recovery-run.json').write_text(json.dumps(info,indent=2),encoding='utf8')
running=subprocess.check_output(COMPOSE+['ps','--status','running','--services']).decode().splitlines()
result_code=1
try:
 if args.pause_source and running:run(COMPOSE+['stop','-t','60',*running],'stop-before-recovery')
 with (LOCAL/'recovery-driver.log').open('wb') as log:
  result=subprocess.run([sys.executable,str(SOURCE/'restore.py'),str(args.backup.resolve()),'--home',str(home),'--project',project,'--offset','1000'],stdout=log,stderr=subprocess.STDOUT)
 result_code=result.returncode
 if result_code==0:
  proof=json.loads((home/'.local/recovery-result.json').read_text(encoding='utf8'))
  proof.update(home=str(home),project=project)
  (LOCAL/'recovery-acceptance.json').write_text(json.dumps(proof,indent=2),encoding='utf8')
  print('Independent recovery acceptance PASSED',flush=True)
 else:print('Recovery failed; diagnostics and all recovery resources retained',flush=True)
finally:
 if (home/'compose.yaml').exists() and (home/'.env').exists():
  recovery=['docker','compose','-p',project,'-f',str(home/'compose.yaml'),'--env-file',str(home/'.env')]
  subprocess.run(recovery+['stop','-t','60'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 if args.pause_source and running:
  run(COMPOSE+['start','--wait',*running],'resume-after-recovery')
  print('Previously running source services resumed; volumes retained',flush=True)
raise SystemExit(result_code)
