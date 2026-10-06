import subprocess,json,sys
from prepare import LOCAL,BASE
with (LOCAL/'simulator.log').open('w',encoding='utf8') as output:
 process=subprocess.Popen([sys.executable,str(BASE/'simulator.py')],stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
(LOCAL/'simulator-pid.json').write_text(json.dumps({'pid':process.pid,'port':23600}),encoding='utf8')
print('Explicit isolated simulator started')
