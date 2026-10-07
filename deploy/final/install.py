"""Quiesce, back up and restore legacy business data. Old containers remain until final gates pass."""
from pathlib import Path
import subprocess,json,hashlib,datetime,base64,shlex,requests,sys,time
from runtime import BASE,ACC,configure
prepare=configure();LOCAL=prepare.LOCAL;BACKUP=LOCAL/'backup';BACKUP.mkdir(parents=True,exist_ok=True)
def command(args,output=None,input=None):
 if output:
  with Path(output).open('wb') as file:
   result=subprocess.run(args,input=input,stdout=file,stderr=subprocess.PIPE)
 else:result=subprocess.run(args,input=input,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if result.returncode:
  (LOCAL/'install-error.log').write_bytes(result.stderr)
  raise RuntimeError('Deployment command failed; private diagnostics saved')
 return result.stdout if not output else b''
def old_sql(sql):
 return command(['docker','exec','-i','tianji-desktop-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 -N -B'],input=sql.encode()).decode().strip()
def backup():
 manifest=BACKUP/'manifest.json'
 if manifest.exists():raise RuntimeError('Backup already prepared; refusing to replace the recovery point')
 ids=command(['docker','ps','-aq','--filter','label=com.docker.compose.project=tianji-desktop']).decode().split()
 snapshot=BACKUP/'legacy-containers.json';old=json.loads(snapshot.read_text(encoding='utf8')) if snapshot.exists() else json.loads(command(['docker','inspect']+ids));snapshot.write_text(json.dumps(old,indent=2),encoding='utf8')
 # Stop all producers/consumers/entry points first; leave only data infrastructure for the dump.
 for item in old:
  name=item['Config']['Labels'].get('com.docker.compose.service')
  if item['State']['Running'] and name not in ['mysql','redis','mq']:command(['docker','stop','--time','30',item['Id']])
 print('Legacy application writes stopped; recovery containers retained',flush=True)
 queue_rows=command(['docker','exec','tianji-desktop-mq-1','rabbitmqctl','-q','list_queues','-p','/tjxt','name','messages_ready','messages_unacknowledged']).decode()
 queues=[]
 for line in queue_rows.splitlines():
  parts=line.split('\t')
  if len(parts)==3 and parts[1].isdigit():queues.append(dict(queue=parts[0],ready=int(parts[1]),unacked=int(parts[2])))
 if any((q['ready'] or q['unacked']) and not q['queue'].startswith('error.') for q in queues):raise RuntimeError('Legacy business messages must drain before cutover')
 (BACKUP/'legacy-queues.json').write_text(json.dumps(queues,indent=2),encoding='utf8')
 redis=next(i for i in old if i['Config']['Labels'].get('com.docker.compose.service')=='redis')
 tokens=[]
 for arg in redis['Config'].get('Cmd',[]):tokens.extend(shlex.split(arg))
 password=tokens[tokens.index('--requirepass')+1] if '--requirepass' in tokens else None
 if password is None and len(tokens)>1 and tokens[1].startswith('/'):
  config=command(['docker','exec','tianji-desktop-redis-1','cat',tokens[1]]).decode()
  for line in config.splitlines():
   parts=shlex.split(line,comments=True)
   if parts and parts[0]=='requirepass':password=parts[1]
 def redis_cmd(*args):
  result=command(['docker','exec']+(['-e','REDISCLI_AUTH='+password] if password else [])+['tianji-desktop-redis-1','redis-cli','--raw']+list(args))
  if b'NOAUTH' in result or b'WRONGPASS' in result:raise RuntimeError('Legacy Redis authentication unavailable')
  return result
 cache={}
 for pattern in ['sign:uid:*','likes:set:biz:*']:
  for key in redis_cmd('--scan','--pattern',pattern).decode().splitlines():
   if key.startswith('sign:uid:'):cache[key]={'type':'bitmap','value':base64.b64encode(redis_cmd('GET',key)[:-1]).decode()}
   else:cache[key]={'type':'set','members':redis_cmd('SMEMBERS',key).decode().splitlines()}
 (BACKUP/'legacy-business-cache.json').write_text(json.dumps(cache,indent=2),encoding='utf8')
 print('Legacy Redis business facts captured: '+str(len(cache))+' keys',flush=True)
 counts={}
 for db in prepare.DATABASES:
  for table in old_sql('SHOW TABLES FROM '+db).splitlines():
   counts[db+'.'+table]=int(old_sql('SELECT COUNT(*) FROM '+db+'.`'+table+'`'))
 if not counts:raise RuntimeError('No original table counts captured; refusing unverified backup')
 dump=BACKUP/'baseline.sql'
 command(['docker','exec','tianji-desktop-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --databases '+' '.join(prepare.DATABASES)],output=dump)
 # Preserve the full legacy snapshot, including Nacos/XXL configuration, for rollback.
 command(['docker','exec','tianji-desktop-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --all-databases'],output=BACKUP/'legacy-all-databases.sql')
 redis_cmd('SAVE')
 for item in old:
  if item['State']['Running'] and item['Config']['Labels'].get('com.docker.compose.service') in ['mysql','redis','mq']:command(['docker','stop','--time','30',item['Id']])
 for name,target in [('redis','/data'),('mq','/var/lib/rabbitmq')]:
  item=next(i for i in old if i['Config']['Labels'].get('com.docker.compose.service')==name)
  command(['docker','run','--rm','--volumes-from',item['Id']+':ro','-v',str(BACKUP)+':/backup','--entrypoint','tar','redis:7.4-alpine','-czf','/backup/legacy-'+name+'-data.tgz','-C',target,'.'])
 shutil_copy=__import__('shutil').copyfile;shutil_copy(dump,LOCAL/'baseline.sql')
 value=dict(createdAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),businessDumpSha256=hashlib.sha256(dump.read_bytes()).hexdigest(),counts=counts,legacyFailureMessages=sum(q['ready'] for q in queues if q['queue'].startswith('error.')),businessQueuePending=0,redisBusinessKeys=len(cache),oldContainerIds=[i['Id'] for i in old],volumesPreserved=True)
 manifest.write_text(json.dumps(value,indent=2),encoding='utf8')
 print('Legacy backups verified and stack stopped; '+str(len(counts))+' table counts captured',flush=True)
def restore():
 command(prepare.COMPOSE+['up','-d','--wait','--wait-timeout','240','mysql','redis','rabbitmq','elasticsearch'],output=LOCAL/'infra-start.log')
 if prepare.mysql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='tj_trade'")!='0':raise RuntimeError('Refusing to replace existing final business data')
 command(prepare.COMPOSE+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4'],input=(BACKUP/'baseline.sql').read_bytes(),output=LOCAL/'restore.log')
 expected=json.loads((BACKUP/'manifest.json').read_text(encoding='utf8'))['counts'];actual={}
 if not expected:raise RuntimeError('Backup table counts must be verified before migration')
 for key,count in expected.items():
  db,table=key.split('.');actual[key]=int(prepare.mysql('SELECT COUNT(*) FROM `'+table+'`',db))
 if expected!=actual:raise RuntimeError('Restored business table counts differ')
 (LOCAL/'restore-verified.json').write_text(json.dumps(dict(status='PASSED',exactTables=len(actual),counts=actual),indent=2),encoding='utf8')
 print('Final data restored; '+str(len(actual))+' exact table counts match',flush=True)
def import_cache():
 cache=json.loads((BACKUP/'legacy-business-cache.json').read_text(encoding='utf8'));signs=likes=0;unresolved=[]
 for key,item in cache.items():
  if item['type']=='bitmap':
   parts=key.split(':');user=parts[2];month=parts[3]
   if not user.isdigit() or len(month)!=6 or not month.isdigit():raise RuntimeError('Unexpected legacy sign key')
   year,m=int(month[:4]),int(month[4:]);raw=base64.b64decode(item['value']);streak=0
   import calendar
   for day in range(1,calendar.monthrange(year,m)[1]+1):
    signed=day-1<len(raw)*8 and raw[(day-1)//8] & (1<<(7-(day-1)%8))
    streak=streak+1 if signed else 0
    if signed:
     date=datetime.date(year,m,day).isoformat();reward={7:10,14:20,28:40}.get(streak,0)
     prepare.mysql(f"INSERT IGNORE INTO sign_record(user_id,sign_day,sign_days,reward_points) VALUES({user},'{date}',{streak},{reward})",'tj_learning');signs+=1
  else:
   biz=key.split(':')[-1]
   if not biz.isdigit() or not all(x.isdigit() for x in item['members']):raise RuntimeError('Unexpected legacy likes')
   if prepare.mysql('SELECT COUNT(*) FROM interaction_reply WHERE id='+biz,'tj_learning')=='0':unresolved.append(key);continue
   for user in item['members']:
    prepare.mysql(f"INSERT IGNORE INTO liked_record(user_id,biz_type,biz_id) VALUES({user},'QA',{biz})",'tj_remark');likes+=1
   prepare.mysql("INSERT INTO liked_counter(biz_type,biz_id,liked_times,version) SELECT 'QA',"+biz+",COUNT(*),1 FROM liked_record WHERE biz_type='QA' AND biz_id="+biz+" ON DUPLICATE KEY UPDATE liked_times=VALUES(liked_times),version=version+1",'tj_remark')
   count=prepare.mysql("SELECT liked_times FROM liked_counter WHERE biz_type='QA' AND biz_id="+biz,'tj_remark')
   prepare.mysql('UPDATE interaction_reply SET liked_times='+count+',liked_version=1 WHERE id='+biz,'tj_learning')
 (LOCAL/'legacy-cache-import.json').write_text(json.dumps(dict(status='PASSED',signFacts=signs,likeRelations=likes,unresolvedArchivedKeys=unresolved,noHistoricalPointsAwarded=True),indent=2),encoding='utf8')
 print('Legacy Redis facts imported without issuing historical points; sign='+str(signs)+', likes='+str(likes)+', archived unresolved='+str(len(unresolved)),flush=True)
if __name__=='__main__':
 action=sys.argv[1]
 if action=='backup':backup()
 elif action=='restore':restore()
 elif action=='import-cache':import_cache()
 else:raise ValueError('Unknown install action')
