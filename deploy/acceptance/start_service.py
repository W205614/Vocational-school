"""Start an upgraded service only against isolated resources. Never print generated credentials."""
from pathlib import Path
import subprocess,os,argparse,yaml,json,hashlib,zipfile,io
from prepare import BASE,LOCAL,local_secrets
REPO=BASE.parents[1]
MODULES={'data':('tj-data','com.tianji.data.DataCenterApplication',23895,'tj_data'),'pay':('tj-pay/tj-pay-service','com.tianji.pay.PayApplication',23883,'tj_pay'),'promotion':('tj-promotion','com.tianji.promotion.PromotionApplication',23892,'tj_promotion'),
 'learning':('tj-learning','com.tianji.learning.LearningApplication',23890,'tj_learning'),
 'trade':('tj-trade','com.tianji.trade.TradeApplication',23888,'tj_trade'),
 'exam':('tj-exam','com.tianji.exam.ExamApplication',23885,'tj_exam'),
 'course':('tj-course','com.tianji.course.CourseApplication',23881,'tj_course'),
 'user':('tj-user','com.tianji.user.UserApplication',23882,'tj_user'),
 'auth':('tj-auth/tj-auth-service','com.tianji.auth.AuthApplication',23884,'tj_auth'),
 'media':('tj-media','com.tianji.media.MediaApplication',23886,'tj_media'),
 'remark':('tj-remark','com.tianji.remark.RemarkApplication',23891,'tj_remark'),
 'search':('tj-search','com.tianji.search.SearchApplication',23889,'tj_search'),
 'message':('tj-message/tj-message-service','com.tianji.message.MessageApplication',23887,'tj_message'),
 'gateway':('tj-gateway','com.tianji.gateway.GatewayApplication',23310,None)}
def main():
 parser=argparse.ArgumentParser();parser.add_argument('service',choices=MODULES);parser.add_argument('--fault',choices=['after-publish','after-consume']);parser.add_argument('--fault-key');parser.add_argument('--progress-interval-ms',type=int,default=1000);args=parser.parse_args()
 if not 1000<=args.progress_interval_ms<=600000:parser.error('progress interval must be 1000..600000')
 if args.fault and not args.fault_key:parser.error('--fault-key is required')
 metadata=LOCAL/(args.service+'-pid.json')
 if metadata.exists():
  record=json.loads(metadata.read_text())
  expected='@'+str((LOCAL/(args.service+'-java.args')).resolve())
  script="$p=Get-CimInstance Win32_Process -Filter 'ProcessId = "+str(int(record['pid']))+"';if($p -and $p.Name -eq 'java.exe' -and $p.CommandLine.Contains('"+expected.replace("'","''")+"')){exit 7}"
  check=subprocess.run(['powershell','-NoProfile','-Command',script],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
  if check.returncode==7:raise RuntimeError('Recorded acceptance JVM still exists; use the identity-checked stop tool before starting')
 module,main,port,database=MODULES[args.service]
 import socket
 with socket.socket() as probe:
  probe.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1)
  try:probe.bind(('127.0.0.1',port))
  except OSError:raise RuntimeError('Acceptance port is already in use; refusing to replace runtime files')
 env=os.environ.copy();env['JAVA_HOME']='E:/Program Files/jdk'
 secrets=local_secrets()
 config=yaml.safe_load((REPO/module/'src/main/resources/application.yml').read_text(encoding='utf8'))
 config['server']['port']=port;config['server']['address']='127.0.0.1'
 spring=config['spring'];spring['config']={'import':[]};spring['profiles']={'active':'acceptance'}
 spring['datasource']={'url':f'jdbc:mysql://127.0.0.1:23316/{database}?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true','username':'root','password':secrets['ACCEPTANCE_DB_PASSWORD'],'driver-class-name':'com.mysql.cj.jdbc.Driver','hikari':{'maximum-pool-size':8,'minimum-idle':2,'connection-timeout':3000}}
 spring['data']={'redis':{'host':'127.0.0.1','port':23379,'timeout':'2s'}}
 spring['rabbitmq']={'host':'127.0.0.1','port':23373,'username':'acceptance','password':secrets['ACCEPTANCE_MQ_PASSWORD'],'publisher-confirm-type':'correlated','publisher-returns':True,'listener':{'simple':{'retry':{'enabled':True,'max-attempts':3,'initial-interval':'500ms'},'default-requeue-rejected':False}}}
 spring['cloud']['nacos']={'config':{'enabled':False,'import-check':{'enabled':False}},'discovery':{'enabled':False}}
 spring['cloud']['discovery']={'client':{'simple':{'instances':{alias+'-service':[{'uri':f'http://127.0.0.1:{target}'}] for alias,(_,_,target,_) in MODULES.items() if alias!='gateway'}}}}
 config.setdefault('tj',{})['xxljob']={'enabled':False}
 if args.service=='learning':config['tj'].setdefault('learning',{})['progress-interval-ms']=args.progress_interval_ms
 if args.fault:config['tj']['acceptance']={'fault':{'point':args.fault,'business-key':args.fault_key}}
 if args.service=='media':
  config['tj']['platform']={'file':'LOCAL','media':'LOCAL'}
  config['tj']['local-storage']={'enabled':True,'directory':str(LOCAL/'objects')}
  config['spring']['servlet']={'multipart':{'max-file-size':'200MB','max-request-size':'201MB'}}
 if args.service=='data':
  spring.pop('datasource',None);spring['autoconfigure']={'exclude':['org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration']}
  config['tj']['reliability']={'enabled':False}
  config['management']['endpoint']['health']['group']['readiness']['include']='readinessState,redis'
 if args.service=='search':
  spring['elasticsearch']={'uris':'http://127.0.0.1:23920'};config['tj']['interests']={'top-number':10}
 if args.service=='message':config['tj']['sms']['simulated']=True
 if args.service=='pay':
  config['tj']['pay']['simulated']=True
  resource_excludes=config['tj'].setdefault('auth',{}).setdefault('resource',{}).setdefault('excludeLoginPaths',[])
  resource_excludes.extend(['/pay-orders/**','/refund-orders/**','/pay-channels/list','/notify/**'])
 resource=config['tj'].setdefault('auth',{}).setdefault('resource',{})
 resource['enable']=True
 resource.setdefault('excludeLoginPaths',[]).append('/actuator/health/**')
 if args.service=='media':resource['excludeLoginPaths'].append('/local-content/**')
 env['TJ_INTERNAL_TOKEN']=secrets['ACCEPTANCE_INTERNAL_TOKEN']
 # All cross-service requests remain inside this acceptance environment.
 clients=spring['cloud'].setdefault('openfeign',{}).setdefault('client',{}).setdefault('config',{})
 for alias,(_,_,target,_) in MODULES.items():
  clients[alias+'-service']=clients[alias]={'url':f'http://127.0.0.1:{target}','connectTimeout':1500,'readTimeout':5000}
 if args.service=='auth':
  key=LOCAL/'acceptance-signing.jks';password=secrets['ACCEPTANCE_INTERNAL_TOKEN']
  if not key.exists():
   subprocess.run(['E:/Program Files/jdk/bin/keytool.exe','-genkeypair','-alias','acceptance','-keyalg','RSA','-keysize','2048','-validity','30','-dname','CN=Acceptance','-storetype','JKS','-keystore',str(key),'-storepass',password,'-keypass',password],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
  config['encrypt']={'key-store':{'alias':'acceptance','location':key.as_uri(),'password':password,'secret':password}}
 if args.service=='gateway':
  spring['main']={'web-application-type':'reactive'}
  spring['cloud']['gateway']['server']['webflux']['routes']=[]
  config['tj']['routes']={alias:f'http://127.0.0.1:{target}' for alias,(_,_,target,_) in MODULES.items() if alias!='gateway'}
  config['tj']['reliability']={'enabled':False}
 config['mybatis-plus']={'mapper-locations':'classpath*:mapper/**/*.xml','configuration':{'map-underscore-to-camel-case':True}}
 config['logging']={'level':{'root':'INFO','com.alibaba.nacos':'WARN'}}
 if args.service=='gateway':config['tj']['simulators']={'enabled':True,'url':'http://127.0.0.1:23883','storage':True,'video':True,'sms':True}
 path=LOCAL/(args.service+'-application.yml');path.write_text(yaml.safe_dump(config,allow_unicode=True,sort_keys=False),encoding='utf8')
 # Snapshot a complete executable jar. Running JVMs never reference Maven's mutable target/classes.
 artifact=REPO/module/'target'/(Path(module).name+'.jar')
 data=artifact.read_bytes()
 with zipfile.ZipFile(io.BytesIO(data)) as archive:
  if 'BOOT-INF/classes/'+main.replace('.','/')+'.class' not in archive.namelist():raise RuntimeError('Missing executable application class')
 digest=hashlib.sha256(data).hexdigest()
 destination=LOCAL/'runtime'/args.service/digest/'app.jar';destination.parent.mkdir(parents=True,exist_ok=True)
 if destination.exists():
  if hashlib.sha256(destination.read_bytes()).hexdigest()!=digest:raise RuntimeError('Immutable runtime artifact was modified')
 else:
  temporary=destination.with_suffix('.tmp');temporary.write_bytes(data);temporary.replace(destination)
 arguments=LOCAL/(args.service+'-java.args')
 arguments.write_text('-Xms128m\n-Xmx512m\n-jar\n"'+destination.as_posix()+'"\n--spring.config.location=file:'+path.as_posix()+'\n',encoding='utf8')
 with (LOCAL/(args.service+'-service.log')).open('w',encoding='utf8') as output:
  process=subprocess.Popen(['E:/Program Files/jdk/bin/java.exe','@'+str(arguments)],cwd=REPO,env=env,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
 (LOCAL/(args.service+'-pid.json')).write_text(json.dumps({'pid':process.pid,'port':port,'main':main,'artifactSha256':digest}))
 print(f'Isolated {args.service} started on {port}; logs retained in .local')
if __name__=='__main__':main()
