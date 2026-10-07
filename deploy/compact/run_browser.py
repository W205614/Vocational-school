import os,sys,subprocess,shutil,json,re
from setup import ROOT,LOCAL,OFFSET,SOURCE,configure_acceptance,ACC
# Every run uses fresh explicit fixtures, so failed runs do not contaminate later attempts.
configure_acceptance();__import__('runpy').run_path(str(ACC/'browser_fixture.py'),run_name='__main__')
subprocess.run([sys.executable,str(SOURCE/'rebuild_search.py')],check=True)
os.environ.update(ACCEPTANCE_CONTAINER_UI='1',TJ_UI_RUNTIME_HOME=str(LOCAL),TJ_STUDENT_URL='http://127.0.0.1:'+str(24500+OFFSET),TJ_ADMIN_URL='http://127.0.0.1:'+str(24501+OFFSET),TJ_PYTHON=sys.executable,TJ_MEDIA_FIXTURE_SCRIPT=str(SOURCE/'attach_browser_media.py'))
with (LOCAL/'browser.log').open('wb') as out:r=subprocess.run([shutil.which('npm') or 'npm','--prefix',str(ROOT/'frontend'),'run','test:e2e'],env=os.environ,stdout=out,stderr=subprocess.STDOUT)
text=(LOCAL/'browser.log').read_text(encoding='utf8',errors='replace');counts={key:int(match.group(1)) if (match:=re.search(r'(\d+) '+key,text)) else 0 for key in ['passed','failed','skipped']}
(LOCAL/'browser-result.json').write_text(json.dumps({'status':'PASSED' if r.returncode==0 and counts['passed'] else 'FAILED',**counts},indent=2),encoding='utf8')
print('Compact browser regression:',counts);raise SystemExit(r.returncode or (0 if counts['passed'] else 1))
