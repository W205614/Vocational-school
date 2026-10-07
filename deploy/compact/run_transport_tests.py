import os,shutil,subprocess
from setup import LOCAL,ROOT,secrets_config
values,_=secrets_config();env=os.environ.copy();env['TJ_INTERNAL_TOKEN']=values['ACCEPTANCE_INTERNAL_TOKEN']
with (LOCAL/'transport-tests.log').open('wb') as output:r=subprocess.run([shutil.which('mvn') or 'mvn','-B','-Pcompact','-pl','tj-compact/runtime','-am','-Dtest=LocalModuleTransportTest','-Dsurefire.failIfNoSpecifiedTests=false','test'],cwd=ROOT,env=env,stdout=output,stderr=subprocess.STDOUT)
print('Local module transport tests '+('PASSED' if r.returncode==0 else 'FAILED'));raise SystemExit(r.returncode)
