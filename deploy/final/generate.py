"""Prepare the final local-simulation stack from verified image IDs; never publish secrets."""
from pathlib import Path
import json,yaml,subprocess,secrets,sys,shutil
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[1];LOCAL=BASE/'.local';ACC=BASE.parent/'acceptance';SOURCE=ACC/'.local'
sys.path.insert(0,str(ACC));from start_service import MODULES
RELEASE='ee045c7'
def run(args,log):
 with log.open('wb') as out:
  result=subprocess.run(args,stdout=out,stderr=subprocess.STDOUT)
 if result.returncode:raise RuntimeError('Preparation command failed; private diagnostics saved')
def main():
 LOCAL.mkdir(parents=True,exist_ok=True);(LOCAL/'configs').mkdir(exist_ok=True);(LOCAL/'objects').mkdir(exist_ok=True);(LOCAL/'backup').mkdir(exist_ok=True)
 path=BASE/'.env'
 if not path.exists():path.write_text('FINAL_RELEASE='+RELEASE+'\nFINAL_DB_PASSWORD='+secrets.token_hex(24)+'\nFINAL_MQ_PASSWORD='+secrets.token_hex(24)+'\nFINAL_INTERNAL_TOKEN='+secrets.token_hex(32)+'\nFINAL_GRAFANA_PASSWORD='+secrets.token_hex(24)+'\n',encoding='utf8')
 env=dict(line.split('=',1) for line in path.read_text(encoding='utf8').splitlines() if '=' in line)
 old=dict(line.split('=',1) for line in (ACC/'.env').read_text(encoding='utf8').splitlines() if '=' in line)
 key=LOCAL/'signing.jks'
 if not key.exists():run(['E:/Program Files/jdk/bin/keytool.exe','-genkeypair','-alias','acceptance','-keyalg','RSA','-keysize','2048','-validity','365','-dname','CN=Tianji Local Final','-storetype','JKS','-keystore',str(key),'-storepass',env['FINAL_INTERNAL_TOKEN'],'-keypass',env['FINAL_INTERNAL_TOKEN']],LOCAL/'keytool.log')
 (LOCAL/'app.env').write_text('TJ_INTERNAL_TOKEN='+env['FINAL_INTERNAL_TOKEN']+'\n',encoding='utf8')
 replacement={old['ACCEPTANCE_DB_PASSWORD']:env['FINAL_DB_PASSWORD'],old['ACCEPTANCE_MQ_PASSWORD']:env['FINAL_MQ_PASSWORD'],old['ACCEPTANCE_INTERNAL_TOKEN']:env['FINAL_INTERNAL_TOKEN']}
 def replace(x):
  if isinstance(x,dict):return {k:replace(v) for k,v in x.items()}
  if isinstance(x,list):return [replace(v) for v in x]
  return replacement.get(x,x) if isinstance(x,str) else x
 (LOCAL/'configs'/'dns.security').write_text('networkaddress.cache.ttl=10\nnetworkaddress.cache.negative.ttl=2\n',encoding='utf8')
 images=json.loads((SOURCE/'containers/images.json').read_text(encoding='utf8'))
 overrides=LOCAL/'backend-images.json'
 if overrides.exists():images.update(json.loads(overrides.read_text(encoding='utf8')))
 services={}
 for name,(module,main,port,db) in MODULES.items():
  config=replace(yaml.safe_load((SOURCE/'containers/full'/(name+'.yml')).read_text(encoding='utf8')))
  config['spring']['rabbitmq']['username']='tianji'
  if name=='media':
   excludes=config.setdefault('tj',{}).setdefault('auth',{}).setdefault('resource',{}).setdefault('excludeLoginPaths',[])
   if '/course-covers/*' not in excludes:excludes.append('/course-covers/*')
  (LOCAL/'configs'/(name+'.yml')).write_text(yaml.safe_dump(config,allow_unicode=True,sort_keys=False),encoding='utf8')
  image=images[name];actual=subprocess.check_output(['docker','image','inspect',image['image'],'--format','{{.Id}}']).decode().strip()
  if actual!=image['imageId']:raise RuntimeError('Verified image changed '+name)
  tag=image.get('finalTag','tianji-final/'+name+':'+RELEASE);subprocess.run(['docker','tag',actual,tag],check=True)
  services['app-'+name]={'image':tag,'restart':'unless-stopped','env_file':['./.local/app.env'],'environment':{'APP_PORT':str(port),'JAVA_TOOL_OPTIONS':'-Djava.security.properties=/run/acceptance/dns.security'},'ports':['127.0.0.1:'+str(port)+':'+str(port)],'volumes':['./.local/configs/dns.security:/run/acceptance/dns.security:ro','./.local/configs/'+name+'.yml:/run/acceptance/application.yml:ro','./.local/signing.jks:/run/acceptance/signing.jks:ro','./.local/objects:/run/objects'],'mem_limit':'768m','cpus':2,'healthcheck':{'test':['CMD','java','-Xms16m','-Xmx32m','-cp','/app/health','HealthProbe'],'interval':'10s','timeout':'4s','start_period':'40s','retries':30},'depends_on':{n:{'condition':'service_healthy'} for n in ['mysql','redis','rabbitmq']}}
  if name=='search':services['app-'+name]['depends_on']['elasticsearch']={'condition':'service_healthy'}
  if name=='gateway':services['app-'+name]['depends_on']['app-auth']={'condition':'service_healthy'}
 infra=yaml.safe_load((ACC/'compose.yaml').read_text(encoding='utf8'))
 for name,service in infra['services'].items():
  service=replace(service)
  if name=='mysql':service['environment']['MYSQL_ROOT_PASSWORD']='${FINAL_DB_PASSWORD:?Set private final .env}';service['mem_limit']='1g';service['command']=['--default-time-zone=+08:00','--innodb-buffer-pool-size=256M','--max-connections=200']
  if name=='mysql':service['healthcheck']['test']=['CMD-SHELL',"MYSQL_PWD=$$MYSQL_ROOT_PASSWORD mysql -uroot -N -e 'SELECT 1'"]
  if name=='redis':service['mem_limit']='128m';service['command']=['redis-server','--appendonly','yes','--maxmemory','96mb','--maxmemory-policy','noeviction']
  if name=='rabbitmq':service['environment'].update(RABBITMQ_DEFAULT_USER='tianji',RABBITMQ_DEFAULT_PASS='${FINAL_MQ_PASSWORD:?Set private final .env}');service['mem_limit']='512m'
  service['restart']='unless-stopped';service['volumes']=[s.replace('acceptance_','final_') for s in service.get('volumes',[])];services[name]=service
 extra=yaml.safe_load((ACC/'compose.extras.yaml').read_text(encoding='utf8'))
 for name,service in extra['services'].items():
  service.pop('profiles',None);service['restart']='unless-stopped';service['volumes']=[s.replace('acceptance_','final_') for s in service.get('volumes',[])]
  if name=='prometheus':
   service['volumes']=['./.local/observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro','../acceptance/observability/alerts.yml:/etc/prometheus/alerts.yml:ro','./.local/observability/internal-token:/etc/prometheus/internal-token:ro','final_prometheus:/prometheus'];service['mem_limit']='256m'
  if name=='grafana':
   service['environment']['GF_SECURITY_ADMIN_USER']='admin';service['environment']['GF_SECURITY_ADMIN_PASSWORD']='${FINAL_GRAFANA_PASSWORD:?Set private final .env}'
   service['environment']['GF_PLUGINS_PREINSTALL_DISABLED']='true'
   service['volumes']=['../acceptance/observability/grafana:/etc/grafana/provisioning:ro','../acceptance/observability/dashboards:/var/lib/grafana/dashboards:ro','final_grafana:/var/lib/grafana'];service['mem_limit']='256m'
  if name=='elasticsearch':service['mem_limit']='1536m'
  service['healthcheck']={'test':['CMD-SHELL','wget -q -O /dev/null http://127.0.0.1:'+('9090/-/ready' if name=='prometheus' else '3000/api/health')],'interval':'10s','timeout':'5s','start_period':'20s','retries':30} if name in ['prometheus','grafana'] else service.get('healthcheck',{})
  services[name]=service
 for app,port,alias in [('student',23500,80),('admin',23501,81)]:
  tag='tianji-final/'+app+':ux-20261007'
  services['web-'+app]={'image':tag,'restart':'unless-stopped','environment':{'GW_UPSTREAM':'app-gateway:23310'},'ports':['127.0.0.1:'+str(port)+':8080','127.0.0.1:'+str(alias)+':8080'],'mem_limit':'128m','depends_on':{'app-gateway':{'condition':'service_healthy'}}}
 volumes={name.replace('acceptance_','final_'):{} for name in list(infra['volumes'])+list(extra['volumes'])}
 (BASE/'compose.yaml').write_text(yaml.safe_dump({'name':'tianji-final','services':services,'volumes':volumes},sort_keys=False),encoding='utf8')
 obs=LOCAL/'observability';obs.mkdir(exist_ok=True);(obs/'internal-token').write_text(env['FINAL_INTERNAL_TOKEN'],encoding='utf8')
 config={'global':{'scrape_interval':'10s'},'rule_files':['/etc/prometheus/alerts.yml'],'scrape_configs':[{'job_name':name,'metrics_path':'/actuator/prometheus','http_headers':{'X-Internal-Token':{'files':['/etc/prometheus/internal-token']}},'static_configs':[{'targets':['app-'+name+':'+str(port)]}]} for name,(_,_,port,_) in MODULES.items() if name!='gateway']}
 (obs/'prometheus.yml').write_text(yaml.safe_dump(config,sort_keys=False),encoding='utf8')
 (LOCAL/'images.json').write_text(json.dumps(images,indent=2),encoding='utf8')
 from build_web import main as build_web
 build_web()
 print('Final Compose group prepared: 14 verified backend images, 2 rebuilt frontend images and 6 infrastructure services',flush=True)
if __name__=='__main__':main()
