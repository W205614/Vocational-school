from setup import configure_acceptance,BASE
import sys,argparse
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'final'))
from final_backup import capture,quiesce
parser=argparse.ArgumentParser();parser.add_argument('--online',action='store_true');args=parser.parse_args()
p=configure_acceptance()
if args.online:capture(p,BASE)
else:
 with quiesce(p):capture(p,BASE,quiesced=True)
