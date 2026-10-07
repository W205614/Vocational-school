"""Restore into a fresh independent home/project only. Never overwrite a running deployment."""
import argparse,os,json,subprocess,zipfile,sys,re
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('backup',type=Path);parser.add_argument('--home',type=Path,required=True);parser.add_argument('--project',required=True);parser.add_argument('--offset',type=int,default=1000);args=parser.parse_args()
home=args.home.resolve()
if home.exists() and any(home.iterdir()):raise RuntimeError('Recovery home must be new and empty')
if not re.fullmatch('tianji-recovery-[a-z0-9-]+',args.project):raise RuntimeError('Recovery project must be separately named')
for resource in ['container','volume']:
 if subprocess.check_output(['docker',resource,'ls','-q','--filter','label=com.docker.compose.project='+args.project]).strip():raise RuntimeError('Recovery project already owns resources; choose a new project')
os.environ.update(TJ_COMPACT_HOME=str(home),TJ_COMPACT_PROJECT=args.project,TJ_COMPACT_PORT_OFFSET=str(args.offset))
from setup import prepare,initialize,COMPOSE,LOCAL,configure_acceptance,ROOT,SOURCE,run,mysql,PORTS
sys.path.insert(0,str(SOURCE.parent/'final'))
from final_backup import verify,digest
from redis_snapshot import transfer
backup=args.backup.resolve();manifest=verify(backup)
if not manifest.get('quiesced') or not manifest.get('broker'):raise RuntimeError('Full recovery requires a quiescent bundle with broker bytes')
home.mkdir(parents=True,exist_ok=True);LOCAL.mkdir(exist_ok=True)
with zipfile.ZipFile(backup/'private-config.zip') as archive:
 for name in archive.namelist():
  if name.startswith('/') or '..' in Path(name).parts or '\\' in name:raise RuntimeError('Unsafe private config archive')
 archive.extractall(LOCAL/'recovered-config')
 private=LOCAL/'recovered-config'
 for name in ['signing.jks','database-accounts.json','accounts.json','browser-fixture.json','load-accounts.json','images.json']:
  if (private/name).exists():__import__('shutil').copyfile(private/name,LOCAL/name)
 __import__('shutil').copyfile(private/'.env',home/'.env')
prepare()
import yaml
compose=yaml.safe_load((home/'compose.yaml').read_text(encoding='utf8'));compose['services']['rabbitmq']['hostname']=manifest['broker']['hostname'];compose['services']['rabbitmq']['volumes']=[{'type':'volume','source':'compact_rabbit','target':'/var/lib/rabbitmq','volume':{'nocopy':True}}];(home/'compose.yaml').write_text(yaml.safe_dump(compose,sort_keys=False),encoding='utf8')
run(COMPOSE+['create','rabbitmq'],'create-broker')
container=subprocess.check_output(COMPOSE+['ps','-aq','rabbitmq']).decode().strip();mounts=json.loads(subprocess.check_output(['docker','inspect',container]))[0]['Mounts'];volume=next(m['Name'] for m in mounts if m['Destination']=='/var/lib/rabbitmq')
if not volume.startswith(args.project+'_'):raise RuntimeError('Recovery volume escaped its isolated project')
run(['docker','run','--rm','-i','--entrypoint','sh','-v',volume+':/restore','mysql:8.4','-c','test -z "$(ls -A /restore)" && tar -xzf - -C /restore'],'restore-broker',input=(backup/'rabbitmq.tar.gz').read_bytes())
initialize(backup)
p=configure_acceptance();transfer(p,backup/'redis.json',restore=True)
expected=json.loads((backup/'table-counts.json').read_text(encoding='utf8'))
queries=[]
for qualified in expected:
 db,table=qualified.split('.')
 if not re.fullmatch('[a-zA-Z0-9_]+',db+table):raise RuntimeError('Invalid table inventory')
 queries.append("SELECT '"+qualified+"',COUNT(*) FROM `"+db+"`.`"+table+"`")
actual={row.split('\t')[0]:int(row.split('\t')[1]) for row in mysql(' UNION ALL '.join(queries)).splitlines()}
if actual!=expected:raise RuntimeError('Restored record counts differ from the recovery bundle')
for name,sha in manifest['mediaFiles'].items():
 if digest(LOCAL/'objects'/name)!=sha:raise RuntimeError('Restored media hash mismatch')
# Verify immutable images before boot, including browser assets.
images=json.loads((LOCAL/'images.json').read_text(encoding='utf8'))
for details in images.values():
 current=subprocess.check_output(['docker','image','inspect',details['image'],'--format','{{.Id}}']).decode().strip()
 if current!=details['imageId']:raise RuntimeError('Image tag drift; restore the recorded image before boot')
result={'status':'DATA_VERIFIED','tableCount':len(expected),'mediaFileCount':len(manifest['mediaFiles']),'redisRecords':len(json.loads((backup/'redis.json').read_text())['records']),'brokerBytesRestored':True,'backup':str(backup)}
(LOCAL/'recovery-result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
run(COMPOSE+['up','-d','--wait'],'up-recovery')
# Search is a rebuildable projection; reset its completion markers only in this restored clone.
mysql('UPDATE tj_search.course_metadata_projection SET processed_version=0;UPDATE tj_search.course_sales_projection SET processed_version=0')
run([sys.executable,str(SOURCE/'prepare_search.py')],'restore-search-schema')
run([sys.executable,str(SOURCE/'rebuild_search.py')],'restore-search-data')
run([sys.executable,str(SOURCE/'recovery_smoke.py')],'recovery-business')
# Repeat the full browser business path only after immutable record/hash comparison.
run([sys.executable,str(SOURCE/'run_browser.py')],'recovery-browser')
result['browser']=json.loads((LOCAL/'browser-result.json').read_text(encoding='utf8'))
result['status']='PASSED';(LOCAL/'recovery-result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
print('Independent recovery PASSED: tables, media bytes, Redis, broker, search and business paths')
