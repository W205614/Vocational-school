"""Run the real compact user startup regression against isolated acceptance MySQL only."""
from pathlib import Path
import os
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'deploy/acceptance'))


def main():
    from prepare import local_secrets
    values=local_secrets()
    env={**os.environ,'ACCEPTANCE_DB_PASSWORD':values['ACCEPTANCE_DB_PASSWORD'],
         'TJ_INTERNAL_TOKEN':values['ACCEPTANCE_INTERNAL_TOKEN']}
    env.setdefault('ACCEPTANCE_DB_PORT','23316')
    command=[shutil.which('mvn') or 'mvn','-B','-Pcompact','-pl','tj-compact/identity','-am',
             '-Dtest=CompactCredentialStartupDatabaseTest','-Dsurefire.failIfNoSpecifiedTests=false','test']
    return subprocess.run(command,cwd=ROOT,env=env).returncode


if __name__=='__main__':raise SystemExit(main())
