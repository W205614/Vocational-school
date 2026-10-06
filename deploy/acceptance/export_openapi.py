from pathlib import Path
import argparse,json,requests
from start_service import MODULES
from prepare import LOCAL,BASE
parser=argparse.ArgumentParser();parser.add_argument('services',nargs='*',default=['learning','exam','promotion','trade','course','user','auth']);args=parser.parse_args()
destination=BASE.parents[1]/'frontend/openapi';destination.mkdir(exist_ok=True)
for name in args.services:
 response=requests.get(f'http://127.0.0.1:{MODULES[name][2]}/v3/api-docs',timeout=60);response.raise_for_status();spec=response.json()
 if 'openapi' not in spec:raise RuntimeError(name+' did not return an OpenAPI document')
 spec.pop('servers',None)
 # Public v2 routes serialize an envelope, including aliases to legacy controllers.
 public={}
 for path,item in spec['paths'].items():
  if path.startswith('/actuator') or path=='/jwks' or path.startswith('/internal/'):continue
  if name=='pay' and path.startswith(('/pay-orders','/refund-orders','/pay-channels')):continue
  if name=='auth' and not path.startswith('/api/v2/'):
   external='/api/v2/'+('auth' if path.startswith('/accounts') else 'admin/auth')+path
  else:external=path if path.startswith('/api/v2/') else '/api/v2/services/'+name+path
  for root in ['/api/v2/operations','/api/v2/admin/events','/api/v2/admin/consumer-failures','/api/v2/admin/operation-failures']:
   if external==root or external.startswith(root+'/'):external=root+'/'+name+external[len(root):]
  for method,operation in item.items():
   if method not in {'get','post','put','delete','patch'}:continue
   for status,response in operation.get('responses',{}).items():
    if status.startswith('2') and not response.get('content') and not path.startswith('/local-content/'):
     response['content']={'application/json':{'schema':{'type':'null'}}}
    for content_type,media in list(response.get('content',{}).items()):
     if content_type not in ('application/json','*/*') or path.startswith('/local-content/'):continue
     if media.get('schema',{}).get('format')=='binary':continue
     if content_type=='*/*':response['content']['application/json']=response['content'].pop(content_type)
     original=media.get('schema',{})
     referenced=spec.get('components',{}).get('schemas',{}).get(original.get('$ref','').split('/')[-1],{})
     if {'code','msg','data'}.issubset(referenced.get('properties',{})):continue
     media['schema']={'type':'object','required':['code','msg','data','requestId'],'properties':{'code':{'type':'integer'},'msg':{'type':'string'},'requestId':{'type':'string'},'data':original}}
  public[external]=item
 spec['paths']=public
 # Long values are serialized as strings by the common Jackson configuration.
 def wire_long(value):
  if isinstance(value,dict):
   if value.get('type')=='integer' and value.get('format')=='int64':
    value['type']='string';value.pop('format');value['pattern']='^-?[0-9]+$'
   for item in value.values():wire_long(item)
  elif isinstance(value,list):
   for item in value:wire_long(item)
 wire_long(spec)
 (destination/(name+'.json')).write_text(json.dumps(spec,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print('Exported '+name)
