"""Export only redacted startup/browser diagnostics; never dump private configs."""
import json,re,subprocess
from setup import LOCAL,PROJECT,secrets_config

env,dbkeys=secrets_config()
private=set(env.values())|set(dbkeys.values())
accounts=LOCAL/'accounts.json'
if accounts.exists():
 for account in json.loads(accounts.read_text(encoding='utf8')).values():
  if isinstance(account,dict) and account.get('password'):private.add(account['password'])

def redact(text):
 for value in sorted(private,key=len,reverse=True):
  if len(value)>=8:text=text.replace(value,'[REDACTED]')
 text=re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[REDACTED JWT]',text)
 return '\n'.join(line for line in text.splitlines() if not re.search(r'password|secret|authorization|cookie|private.key|LoginFormDTO',line,re.I))

ids=subprocess.check_output(['docker','ps','-aq','--filter','label=com.docker.compose.project='+PROJECT]).decode().splitlines()
report={'containers':[],'privateLogExcerpts':{}}
if ids:
 for c in json.loads(subprocess.check_output(['docker','inspect',*ids])):
  state=c['State'];service=c['Config']['Labels']['com.docker.compose.service']
  logs=subprocess.run(['docker','logs','--tail','150',c['Id']],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
  item={'service':service,'status':state['Status'],'exitCode':state['ExitCode'],'oomKilled':state['OOMKilled'],
        'health':state.get('Health',{}).get('Status'),'logTail':redact(logs.stdout.decode('utf8',errors='replace'))}
  report['containers'].append(item)
  print(service,item['status'],'health='+str(item['health']),'oom='+str(item['oomKilled']))
for name in ['up-None.log','browser.log','maven.log']:
 path=LOCAL/name
 if path.exists():report['privateLogExcerpts'][name]=redact(path.read_text(encoding='utf8',errors='replace')[-20000:])
(LOCAL/'ci-diagnostics.json').write_text(json.dumps(report,indent=2),encoding='utf8')
