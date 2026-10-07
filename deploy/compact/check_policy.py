import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
policy=set((ROOT/'tj-gateway/src/main/resources/access-policy.txt').read_text(encoding='utf8').splitlines());missing=[]
for source in (ROOT/'frontend/openapi').glob('*.json'):
 for path,verbs in json.loads(source.read_text(encoding='utf8'))['paths'].items():
  for verb in verbs:
   if verb.upper() in {'GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS'} and verb.upper()+':'+path not in policy:missing.append(verb.upper()+':'+path)
if missing:raise RuntimeError('Undeclared API contracts: '+', '.join(missing))
missing_admin=[line.replace(':/api/v2/services/',':/api/v2/admin/',1) for line in policy if ':/api/v2/services/' in line and line.replace(':/api/v2/services/',':/api/v2/admin/',1) not in policy]
if missing_admin:raise RuntimeError('Undeclared administrator aliases: '+', '.join(missing_admin))
if any('**' in p for p in policy):raise RuntimeError('Broad grants are forbidden in the explicit API inventory')
print('All versioned API contracts have explicit method/path declarations')
