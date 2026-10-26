"""Compatibility CLI; current capture and verification live in deploy/operations."""
from pathlib import Path
import argparse
import sys
BASE=Path(__file__).resolve().parent
sys.path.insert(0,str(BASE.parent/'operations'))
from runtime_backup import digest,table_checksums,verify,quiesce,capture as _capture

def capture(runtime=None,source_base=None,quiesced=False):
    if runtime is None:
        from runtime import configure
        runtime=configure()
    return _capture(runtime,source_base or BASE,quiesced=quiesced)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--verify',type=Path);parser.add_argument('--quiesce',action='store_true');args=parser.parse_args()
    if args.verify:verify(args.verify.resolve());print('Recovery checksums and media bytes verified')
    elif args.quiesce:
        from runtime import configure
        runtime=configure()
        with quiesce(runtime):capture(runtime,quiesced=True)
    else:capture()
