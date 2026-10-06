"""Serial end-to-end gates; faults require an explicit --faults switch."""
from pathlib import Path
import subprocess,sys,argparse
BASE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--faults',action='store_true');args=parser.parse_args()
scripts=['api_smoke','coupon_concurrency','financial_smoke','auxiliary_smoke','projection_security_smoke']
if args.faults:scripts+=['recovery_smoke','browser_fixture','runtime_faults']
for name in scripts:
 print('Running '+name,flush=True)
 subprocess.run([sys.executable,str(BASE/(name+'.py'))],check=True)
print('Serial gateway/concurrency'+('/fault' if args.faults else '')+' gates PASSED',flush=True)
