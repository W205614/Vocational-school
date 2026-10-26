"""Export only redacted startup/browser diagnostics; never dump private configs."""
import json,subprocess
from setup import LOCAL,PROJECT,secrets_config
from ci_redaction import redact as redact_text,excerpt

env,dbkeys=secrets_config()
private=set(env.values())|set(dbkeys.values())
accounts=LOCAL/'accounts.json'
if accounts.exists():
 for account in json.loads(accounts.read_text(encoding='utf8')).values():
  if isinstance(account,dict) and account.get('password'):private.add(account['password'])

def redact(text):
 return redact_text(text,private)

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
for name in ['up-None.log','browser.log','maven.log','transport-tests.log']:
 path=LOCAL/name
 if path.exists():report['privateLogExcerpts'][name]=excerpt(path.read_text(encoding='utf8',errors='replace'),private)
(LOCAL/'ci-diagnostics.json').write_text(json.dumps(report,indent=2),encoding='utf8')
