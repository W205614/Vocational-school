"""Reproducible compact deployment. Defaults create synthetic data in a new isolated project.
No private historical artifact is needed. init refuses an occupied application database.
"""
from pathlib import Path
import argparse,hashlib,json,os,re,secrets,shutil,subprocess,sys,time
import yaml
SOURCE=Path(__file__).resolve().parent;BASE=Path(os.environ.get('TJ_COMPACT_HOME',str(SOURCE))).resolve();BASE.mkdir(parents=True,exist_ok=True);ROOT=SOURCE.parents[1];ACC=SOURCE.parent/'acceptance';LOCAL=BASE/'.local'
sys.path.insert(0,str(ACC));from start_service import MODULES
GROUPS={'identity':['auth','user'],'commerce':['trade','pay','promotion'],'education':['course','learning','exam','remark'],'support':['media','search','message','data']}
PORTS={'identity':24001,'commerce':24002,'education':24003,'support':24004,'gateway':24310}
OFFSET=int(os.environ.get('TJ_COMPACT_PORT_OFFSET','0'));PORTS={k:v+OFFSET for k,v in PORTS.items()}
PROJECT=os.environ.get('TJ_COMPACT_PROJECT','tianji-compact')
if not re.fullmatch('[a-z][a-z0-9-]{2,40}',PROJECT):raise ValueError('Invalid isolated project name')
COMPOSE=['docker','compose','-p',PROJECT,'-f',str(BASE/'compose.yaml'),'--env-file',str(BASE/'.env')]
def run(command,name,input=None):
 LOCAL.mkdir(exist_ok=True)
 with (LOCAL/(name+'.log')).open('wb') as log:
  result=subprocess.run(command,input=input,stdout=log,stderr=subprocess.STDOUT)
 if result.returncode:raise RuntimeError('Command failed; private diagnostic: '+name+'.log')
def mysql(sql):
 result=subprocess.run(COMPOSE+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B --default-character-set=utf8mb4'],input=sql.encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if result.returncode:(LOCAL/'mysql-error.log').write_bytes(result.stderr);raise RuntimeError('SQL failed; private diagnostic retained')
 return result.stdout.decode('utf8').strip()
def secrets_config():
 LOCAL.mkdir(exist_ok=True);path=BASE/'.env'
 if not path.exists():path.write_text('\n'.join(k+'='+secrets.token_hex(32) for k in ['ACCEPTANCE_DB_PASSWORD','ACCEPTANCE_MQ_PASSWORD','ACCEPTANCE_INTERNAL_TOKEN','ACCEPTANCE_GRAFANA_PASSWORD'])+'\n',encoding='utf8')
 env=dict(line.split('=',1) for line in path.read_text(encoding='utf8').splitlines() if '=' in line)
 accounts=LOCAL/'database-accounts.json'
 if not accounts.exists():accounts.write_text(json.dumps({a:secrets.token_hex(24) for a in MODULES if MODULES[a][3] and a!='data'},indent=2),encoding='utf8')
 return env,{a:pw for a,pw in json.loads(accounts.read_text(encoding='utf8')).items() if a!='data'}
def prepare():
 env,dbkeys=secrets_config();configs=LOCAL/'configs';configs.mkdir(exist_ok=True);(LOCAL/'objects').mkdir(exist_ok=True)
 (LOCAL/'app.env').write_text('TJ_INTERNAL_TOKEN='+env['ACCEPTANCE_INTERNAL_TOKEN']+'\n',encoding='utf8')
 key=LOCAL/'signing.jks'
 if not key.exists():run([shutil.which('keytool') or 'keytool','-genkeypair','-alias','compact','-keyalg','RSA','-keysize','2048','-validity','365','-dname','CN=Tianji Local Compact','-storetype','JKS','-keystore',str(key),'-storepass',env['ACCEPTANCE_INTERNAL_TOKEN'],'-keypass',env['ACCEPTANCE_INTERNAL_TOKEN']],'keytool')
 targets={a:'http://app-'+g+':'+str(PORTS[g])+'/_modules/'+a for g,aliases in GROUPS.items() for a in aliases}
 excludes=[line.strip() for source in ROOT.glob('tj-*/**/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports') for line in source.read_text(encoding='utf8').splitlines() if line.strip()]+['com.tianji.authsdk.resource.config.ResourceInterceptorConfiguration','com.tianji.authsdk.resource.config.FeignRelayUserAutoConfiguration','org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration','org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration','org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration','com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration']
 for alias,(module,_,_,database) in MODULES.items():
  if alias in {'data','gateway'}:database=None
  c=yaml.safe_load((ROOT/module/'src/main/resources/application.yml').read_text(encoding='utf8'));s=c['spring'];c['server']={'port':PORTS.get(alias,0),'shutdown':'graceful'}
  s['profiles']={'active':'local-simulator'};s['config']={'import':[]};s['main']={'allow-bean-definition-overriding':False}
  s['cloud']['sentinel']={'enabled':False};s['cloud']['nacos']={'config':{'enabled':False,'import-check':{'enabled':False}},'discovery':{'enabled':False}}
  s['cloud']['discovery']={'client':{'simple':{'instances':{a+'-service':[{'uri':url}] for a,url in targets.items()}}}}
  clients=s['cloud'].setdefault('openfeign',{}).setdefault('client',{}).setdefault('config',{})
  for a,url in targets.items():clients[a]=clients[a+'-service']={'url':url,'connectTimeout':1500,'readTimeout':5000,'loggerLevel':'none'}
  for source in ROOT.glob('tj-*/**/src/main/java/**/*Client.java'):
   for declaration in re.findall(r'@FeignClient\(([^)]*)\)',source.read_text(encoding='utf8')):
    named=re.search(r'(?:value|name)\s*=\s*"([^"]+)"',declaration);positional=re.match(r'\s*"([^"]+)"',declaration);service=(named or positional)
    if not service:continue
    target=targets.get(service.group(1).removesuffix('-service'))
    context=re.search(r'contextId\s*=\s*"([^"]+)"',declaration)
    if target and context:clients[context.group(1)]={'url':target,'connectTimeout':1500,'readTimeout':5000,'loggerLevel':'none'}
  s['data']={'redis':{'host':'redis','port':6379,'timeout':'2s'}}
  s['rabbitmq']={'host':'rabbitmq','port':5672,'username':'tianji','password':env['ACCEPTANCE_MQ_PASSWORD'],'publisher-confirm-type':'correlated','publisher-returns':True,'listener':{'simple':{'retry':{'enabled':True,'max-attempts':3,'initial-interval':'500ms'},'default-requeue-rejected':False,'concurrency':1,'max-concurrency':2,'prefetch':10}}}
  if database:s['datasource']={'url':'jdbc:mysql://mysql:3306/'+database+'?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true','username':'app_'+alias,'password':dbkeys[alias],'driver-class-name':'com.mysql.cj.jdbc.Driver','hikari':{'maximum-pool-size':4,'minimum-idle':0,'idle-timeout':60000,'connection-timeout':3000,'pool-name':alias+'-db'}}
  else:s.pop('datasource',None);s['autoconfigure']={'exclude':['org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration']}
  tj=c.setdefault('tj',{});tj['xxljob']={'enabled':False};tj.setdefault('swagger',{})['enable']=False
  tj['reliability']={'enabled':database is not None,'operation-interval-ms':750,'dispatch-interval-ms':750,'operation-core':1,'operation-max':4,'outbox-core':1,'outbox-max':2}
  resource=tj.setdefault('auth',{}).setdefault('resource',{});resource['enable']=True;resource.setdefault('excludeLoginPaths',[]).extend(['/actuator/health/**','/readyz'])
  if alias=='auth':c['encrypt']={'key-store':{'alias':'compact','location':'file:/run/compact/signing.jks','password':env['ACCEPTANCE_INTERNAL_TOKEN'],'secret':env['ACCEPTANCE_INTERNAL_TOKEN']}};resource['includeLoginPaths']=['/menus/me','/accounts/logout','/accounts/sessions/**']
  if alias=='media':tj['platform']={'file':'LOCAL','media':'LOCAL'};tj['local-storage']={'enabled':True,'directory':'/run/objects'};tj['tencent']['vod']['enable']=False;tj['tencent']['cos']['enable']=False;resource['excludeLoginPaths'].extend(['/local-content/**','/course-covers/*']);s['servlet']={'multipart':{'max-file-size':'200MB','max-request-size':'201MB'}}
  if alias=='search':s['elasticsearch']={'uris':'http://elasticsearch:9200'};tj['interests']={'top-number':10}
  if alias=='message':tj.setdefault('sms',{})['simulated']=True
  if alias=='pay':tj['pay']['simulated']=True;tj['pay']['notifyHost']='http://app-gateway:'+str(PORTS['gateway'])+'/api/v2';resource['excludeLoginPaths'].extend(['/pay-orders/**','/refund-orders/**','/pay-channels/list','/notify/**'])
  if alias=='learning':tj['learning']={'progress-interval-ms':1000}
  if alias=='gateway':
   s['main']['web-application-type']='reactive';s['cloud']['gateway']['server']['webflux']['routes']=[];tj['routes']=targets;tj['simulators']={'enabled':True,'url':targets['pay'],'storage':True,'video':True,'sms':True};tj['reliability']={'enabled':False};resource['enable']=False
  c['springdoc']={'api-docs':{'enabled':False},'swagger-ui':{'enabled':False}}
  c['mybatis-plus']={'mapper-locations':'classpath*:mapper/**/*.xml','configuration':{'map-underscore-to-camel-case':True}}
  c['logging']={'level':{'root':'INFO','com.alibaba.nacos':'WARN','feign':'WARN'}}
  c['management']['endpoint']['health']['group']['readiness']['include']='readinessState,redis'+(',db' if database else '')
  (configs/(alias+'.yml')).write_text(yaml.safe_dump(c,allow_unicode=True,sort_keys=False),encoding='utf8')
 for group in GROUPS:
  c={'server':{'port':PORTS[group],'shutdown':'graceful','tomcat':{'threads':{'max':80,'min-spare':4},'accept-count':50,'max-connections':2000}},'spring':{'application':{'name':group+'-app'},'autoconfigure':{'exclude':excludes},'main':{'allow-bean-definition-overriding':False},'cloud':{'sentinel':{'enabled':False},'nacos':{'config':{'enabled':False,'import-check':{'enabled':False}},'discovery':{'enabled':False}}}},'tj':{'compact':{'config-directory':'/run/compact/configs'},'reliability':{'enabled':False}},'management':{'endpoints':{'web':{'exposure':{'include':'health,info,prometheus'}}},'endpoint':{'health':{'probes':{'enabled':True},'group':{'readiness':{'include':'readinessState,modules','additional-path':'server:/readyz'}}}}}}
  (configs/(group+'.yml')).write_text(yaml.safe_dump(c,sort_keys=False),encoding='utf8')
 infrastructure={'mysql':{'image':'mysql:8.4','environment':{'MYSQL_ROOT_PASSWORD':'${ACCEPTANCE_DB_PASSWORD}','TZ':'Asia/Shanghai'},'command':['--default-time-zone=+08:00','--innodb-buffer-pool-size=256M','--max-connections=160'],'ports':['127.0.0.1:'+str(24316+OFFSET)+':3306'],'volumes':['compact_mysql:/var/lib/mysql'],'mem_limit':'1g','healthcheck':{'test':['CMD-SHELL','MYSQL_PWD=$$MYSQL_ROOT_PASSWORD mysql -uroot -N -e "SELECT 1"'],'interval':'5s','timeout':'3s','retries':60}},'redis':{'image':'redis:7.4-alpine','command':['redis-server','--appendonly','yes','--maxmemory','192mb','--maxmemory-policy','noeviction'],'ports':['127.0.0.1:'+str(24379+OFFSET)+':6379'],'volumes':['compact_redis:/data'],'mem_limit':'256m','healthcheck':{'test':['CMD','redis-cli','ping'],'interval':'5s','timeout':'3s','retries':30}},'rabbitmq':{'image':'rabbitmq:4.1-management','environment':{'RABBITMQ_DEFAULT_USER':'tianji','RABBITMQ_DEFAULT_PASS':'${ACCEPTANCE_MQ_PASSWORD}'},'ports':['127.0.0.1:'+str(24373+OFFSET)+':5672'],'volumes':['compact_rabbit:/var/lib/rabbitmq'],'mem_limit':'512m','healthcheck':{'test':['CMD','rabbitmq-diagnostics','-q','ping'],'interval':'5s','timeout':'5s','retries':30}},'elasticsearch':{'image':'docker.elastic.co/elasticsearch/elasticsearch:7.17.29','environment':{'discovery.type':'single-node','xpack.security.enabled':'false','ES_JAVA_OPTS':'-Xms512m -Xmx512m'},'ports':['127.0.0.1:'+str(24920+OFFSET)+':9200'],'volumes':['compact_search:/usr/share/elasticsearch/data'],'mem_limit':'1536m','healthcheck':{'test':['CMD-SHELL','curl -fsS http://localhost:9200/_cluster/health'],'interval':'5s','timeout':'3s','retries':60}}}
 services=dict(infrastructure)
 for group in list(GROUPS)+['gateway']:
  services['app-'+group]={'image':'tianji-compact/'+group+':local','restart':'unless-stopped','env_file':['./.local/app.env'],'ports':['127.0.0.1:'+str(PORTS[group])+':'+str(PORTS[group])],'volumes':['./.local/configs:/run/compact/configs:ro','./.local/signing.jks:/run/compact/signing.jks:ro','./.local/objects:/run/objects'],'mem_limit':'1536m' if group!='gateway' else '768m','environment':{'APP_PORT':str(PORTS[group])},'command':['--spring.config.location=file:/run/compact/configs/'+group+'.yml'],'depends_on':{i:{'condition':'service_healthy'} for i in infrastructure},'healthcheck':{'test':['CMD','java','-Xms16m','-Xmx32m','-cp','/app/health','HealthProbe'],'interval':'10s','timeout':'4s','start_period':'90s','retries':30}}
 for app,port in [('student',24500+OFFSET),('admin',24501+OFFSET)]:services['web-'+app]={'image':'tianji-compact/'+app+':local','environment':{'GW_UPSTREAM':'app-gateway:'+str(PORTS['gateway'])},'ports':['127.0.0.1:'+str(port)+':8080'],'mem_limit':'128m','depends_on':{'app-gateway':{'condition':'service_healthy'}}}
 (BASE/'compose.yaml').write_text(yaml.safe_dump({'name':PROJECT,'services':services,'volumes':{n:{} for n in ['compact_mysql','compact_redis','compact_rabbit','compact_search']}},sort_keys=False),encoding='utf8')
 print('Independent compact configuration generated; secrets kept in ignored files',flush=True)
def initialize(clone=None):
 run(COMPOSE+['up','-d','--wait','mysql','redis','rabbitmq','elasticsearch'],'infra')
 occupied=mysql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name LIKE 'tj\\_%'")!='0'
 state=LOCAL/'init-state.json'
 if occupied and not state.exists():raise RuntimeError('Refusing to replace an occupied business schema without this initializer journal')
 if not state.exists():state.write_text(json.dumps({'phase':'IMPORTING','clone':str(clone) if clone else None}),encoding='utf8')
 phase=json.loads(state.read_text(encoding='utf8'))['phase']
 if phase=='COMPLETE':print('Initialization already completed');return
 if phase=='IMPORTING' and clone:
  from zipfile import ZipFile
  sys.path.insert(0,str(SOURCE.parent/'final'));from final_backup import verify
  verify(clone)
  run(COMPOSE+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],'restore',input=(clone/'business.sql').read_bytes())
  with ZipFile(clone/'media.zip') as archive:archive.extractall(LOCAL/'objects')
 elif phase=='IMPORTING':
  mysql((SOURCE/'schema.sql').read_text(encoding='utf8'));mysql((SOURCE/'catalog.sql').read_text(encoding='utf8'))
 state.write_text(json.dumps({'phase':'SCHEMA','clone':str(clone) if clone else None}),encoding='utf8')
 configure_acceptance()
 import migrate;migrate.main()
 env,passwords=secrets_config()
 sql=''
 for a,pw in passwords.items():
  db=MODULES[a][3];sql+="CREATE USER IF NOT EXISTS 'app_"+a+"'@'%' IDENTIFIED BY '"+pw+"';GRANT SELECT,INSERT,UPDATE,DELETE ON `"+db+"`.* TO 'app_"+a+"'@'%';"
 mysql(sql)
 if not clone:
  import runpy;runpy.run_path(str(ACC/'seed_accounts.py'),run_name='__main__');mysql((SOURCE/'demo.sql').read_text(encoding='utf8'));runpy.run_path(str(ACC/'browser_fixture.py'),run_name='__main__')
 state.write_text(json.dumps({'phase':'COMPLETE','clone':str(clone) if clone else None}),encoding='utf8')
 print('Business schemas initialized; runtime accounts have only own-schema DML privileges',flush=True)
def configure_acceptance():
 import prepare as p;p.LOCAL=LOCAL;p.COMPOSE=COMPOSE;p.RUNTIME=BASE
 p.local_secrets=lambda:dict(secrets_config()[0],ACCEPTANCE_MQ_USERNAME='tianji')
 return p
def build(package=True,group=None):
 requested_group=group
 # Linux bind mounts retain host ownership. Keep the container non-root while
 # allowing the developer to read backups of files created by the application.
 app_uid=os.getuid() if hasattr(os,'getuid') and os.getuid()!=0 else 10001
 app_gid=os.getgid() if hasattr(os,'getgid') and os.getgid()!=0 else 10001
 mvn=shutil.which('mvn') or 'mvn'
 if package:run([mvn,'-B','-Pcompact','-DskipTests','package'],'maven')
 manifest=json.loads((LOCAL/'images.json').read_text(encoding='utf8')) if (LOCAL/'images.json').exists() else {}
 selected=[group] if group else list(GROUPS)+['gateway']
 for group in selected:
  jar=ROOT/('tj-gateway/target/tj-gateway.jar' if group=='gateway' else 'tj-compact/'+group+'/target/tj-'+group+'-app.jar')
  folder=LOCAL/'images'/group;folder.mkdir(parents=True,exist_ok=True);shutil.copyfile(jar,folder/'app.jar')
  run([shutil.which('javac') or 'javac','-d',str(folder),str(ACC/'docker/HealthProbe.java')],'health-compile')
  heap='384m' if group=='gateway' else '768m'
  (folder/'Dockerfile').write_text('FROM eclipse-temurin:21-jre\nENV TZ=Asia/Shanghai\nWORKDIR /app\nRUN groupadd --non-unique --gid '+str(app_gid)+' app && useradd --non-unique --uid '+str(app_uid)+' --gid app --home /app app && mkdir -p /app/logs/csp && chown -R app:app /app/logs\nCOPY app.jar /app/app.jar\nCOPY HealthProbe.class /app/health/HealthProbe.class\nUSER app\nENTRYPOINT ["java","-Xms96m","-Xmx'+heap+'","-XX:MaxDirectMemorySize=96m","-XX:ActiveProcessorCount=2","-jar","/app/app.jar"]\n',encoding='utf8')
  image='tianji-compact/'+group+':local';run(['docker','build','--pull=false','-t',image,str(folder)],'image-'+group)
  manifest[group]={'image':image,'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'imageId':subprocess.check_output(['docker','image','inspect',image,'--format','{{.Id}}']).decode().strip()}
  print('Built compact '+group,flush=True)
 if not requested_group:
  run([shutil.which('npm') or 'npm','--prefix',str(ROOT/'frontend'),'run','build'],'frontend')
  for app in ['student','admin']:
   folder=LOCAL/'images'/app;folder.mkdir(parents=True,exist_ok=True);shutil.copytree(ROOT/'frontend/apps'/app/'dist',folder/'dist',dirs_exist_ok=True)
   shutil.copyfile(ACC/'docker/nginx.conf.template',folder/'nginx.conf.template');shutil.copyfile(ACC/'docker/Dockerfile.web',folder/'Dockerfile');run(['docker','build','--pull=false','-t','tianji-compact/'+app+':local',str(folder)],'image-'+app)
   manifest[app]={'image':'tianji-compact/'+app+':local','imageId':subprocess.check_output(['docker','image','inspect','tianji-compact/'+app+':local','--format','{{.Id}}']).decode().strip()}
 (LOCAL/'images.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
def up(group=None):run(COMPOSE+['up','-d','--wait']+(['app-'+group] if group else []),'up-'+str(group))
def main():
 parser=argparse.ArgumentParser();parser.add_argument('action',choices=['prepare','init','build','up','migrate']);parser.add_argument('--skip-package',action='store_true');parser.add_argument('--clone',type=Path);parser.add_argument('--group',choices=list(GROUPS)+['gateway']);args=parser.parse_args()
 if args.action=='prepare':prepare()
 elif args.action=='init':initialize(args.clone)
 elif args.action=='build':build(not args.skip_package,args.group)
 elif args.action=='migrate':configure_acceptance();import migrate;migrate.main()
 else:up(args.group)
if __name__=='__main__':main()
