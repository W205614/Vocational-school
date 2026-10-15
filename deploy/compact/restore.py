"""Restore into a fresh independent home/project only. Never overwrite a running deployment."""
import argparse,os,json,subprocess,zipfile,sys,re,time
from pathlib import Path
from recovery_upgrade import (load_upgrade,preserve_generated_configs,activate_generated_configs,upgraded_configuration,
                              copy_verified_manifests,compare_broker_archives,compare_redis_snapshots,tar_inventory)
parser=argparse.ArgumentParser();parser.add_argument('backup',type=Path);parser.add_argument('--home',type=Path,required=True);parser.add_argument('--project',required=True);parser.add_argument('--offset',type=int,default=1000);parser.add_argument('--upgrade-from',type=Path,help='Verified deployment home supplying seven new application images after raw recovery checks');args=parser.parse_args()
home=args.home.resolve()
backup=args.backup.resolve()
if home.is_relative_to(backup):raise RuntimeError('Recovery home must be outside the immutable backup bundle')
upgrade_home=args.upgrade_from.resolve() if args.upgrade_from else None
if upgrade_home and (home.is_relative_to(upgrade_home) or upgrade_home.is_relative_to(home)):raise RuntimeError('Recovery home must be independent of the upgrade source deployment')
if home.exists() and any(home.iterdir()):raise RuntimeError('Recovery home must be new and empty')
if not re.fullmatch('tianji-recovery-[a-z0-9-]+',args.project):raise RuntimeError('Recovery project must be separately named')
for resource in ['container','volume']:
 if subprocess.check_output(['docker',resource,'ls','-q','--filter','label=com.docker.compose.project='+args.project]).strip():raise RuntimeError('Recovery project already owns resources; choose a new project')
upgrade=load_upgrade(upgrade_home,Path(__file__).resolve().parents[2]) if upgrade_home else None
os.environ.update(TJ_COMPACT_HOME=str(home),TJ_COMPACT_PROJECT=args.project,TJ_COMPACT_PORT_OFFSET=str(args.offset))
from setup import prepare,initialize,COMPOSE,LOCAL,configure_acceptance,ROOT,SOURCE,run,mysql,PORTS
sys.path.insert(0,str(SOURCE.parent/'final'))
from final_backup import verify,digest,table_checksums
from redis_snapshot import transfer
manifest=verify(backup)
if not manifest.get('quiesced') or not manifest.get('broker'):raise RuntimeError('Full recovery requires a quiescent bundle with broker bytes')
home.mkdir(parents=True,exist_ok=True);LOCAL.mkdir(exist_ok=True)
with zipfile.ZipFile(backup/'private-config.zip') as archive:
 for name in archive.namelist():
  if name.startswith('/') or '..' in Path(name).parts or '\\' in name:raise RuntimeError('Unsafe private config archive')
 archive.extractall(LOCAL/'recovered-config')
 private=LOCAL/'recovered-config'
 for name in ['signing.jks','database-accounts.json','accounts.json','browser-fixture.json','load-accounts.json','images.json','build-source.json','infrastructure-images.json']:
  if (private/name).exists():__import__('shutil').copyfile(private/name,LOCAL/name)
 __import__('shutil').copyfile(private/'.env',home/'.env')
prepare()
import yaml
generated=yaml.safe_load((home/'compose.yaml').read_text(encoding='utf8'))
generated_configs=preserve_generated_configs(home) if upgrade else None
if upgrade:(LOCAL/'generated-upgrade-compose.yaml').write_text(yaml.safe_dump(generated,sort_keys=False),encoding='utf8')
if not (private/'compose.yaml').exists() or not (private/'build-source.json').exists():
 raise RuntimeError('Legacy bundle lacks immutable Compose/source metadata; recover it using its original source checkout')
recorded=yaml.safe_load((private/'compose.yaml').read_text(encoding='utf8'))
if set(recorded['services'])!=set(generated['services']):raise RuntimeError('Recovery topology differs from the recorded deployment')
frozen={}
if (private/'infrastructure-images.json').exists():frozen.update(json.loads((private/'infrastructure-images.json').read_text(encoding='utf8')))
source=json.loads((private/'build-source.json').read_text(encoding='utf8'));frozen.update(source['imageDigests'])
if (private/'runtime-images.json').exists():
 actual=json.loads((private/'runtime-images.json').read_text(encoding='utf8'))
 if any(actual.get(name)!=image for name,image in source['imageDigests'].items()):raise RuntimeError('Recovery source and captured runtime images differ')
 frozen.update(actual)
for image in frozen.values():
 if not re.fullmatch(r'sha256:[0-9a-f]{64}',image):raise RuntimeError('Recovery requires immutable image IDs')
 # Upgrade recovery never boots the old application/web images. Their recorded
 # IDs remain provenance; only infrastructure and the verified new images run.
required_frozen={name:image for name,image in frozen.items() if not upgrade or not name.startswith(('app-','web-'))}
for image in required_frozen.values():
 if subprocess.check_output(['docker','image','inspect',image,'--format','{{.Id}}']).decode().strip()!=image:
  raise RuntimeError('Load the verified rollback image archive before restoring this bundle')
from recovery_configuration import recorded_configuration
compose=recorded_configuration(recorded,private/'configs',home,args.project,args.offset,frozen,manifest['broker']['hostname'])
(home/'compose.yaml').write_text(yaml.safe_dump(compose,sort_keys=False),encoding='utf8')
run(COMPOSE+['create','rabbitmq'],'create-broker')
container=subprocess.check_output(COMPOSE+['ps','-aq','rabbitmq']).decode().strip();mounts=json.loads(subprocess.check_output(['docker','inspect',container]))[0]['Mounts'];volume=next(m['Name'] for m in mounts if m['Destination']=='/var/lib/rabbitmq')
if not volume.startswith(args.project+'_'):raise RuntimeError('Recovery volume escaped its isolated project')
tar_inventory(backup/'rabbitmq.tar.gz')
run(['docker','run','--rm','-i','--entrypoint','sh','-v',volume+':/restore',frozen['mysql'],'-c','test -z "$(ls -A /restore)" && tar -xzf - -C /restore'],'restore-broker',input=(backup/'rabbitmq.tar.gz').read_bytes())
broker_copy=LOCAL/'restored-broker-check.tar.gz'
with broker_copy.open('wb') as output:
 checked=subprocess.run(['docker','run','--rm','--entrypoint','sh','-v',volume+':/restore:ro',frozen['mysql'],'-c','tar -czf - -C /restore .'],stdout=output,stderr=subprocess.PIPE)
if checked.returncode:(LOCAL/'broker-check-error.log').write_bytes(checked.stderr);raise RuntimeError('Restored broker inventory could not be verified')
broker_proof=compare_broker_archives(backup/'rabbitmq.tar.gz',broker_copy)
initialize(backup,migrate_schema=False)
p=configure_acceptance();transfer(p,backup/'redis.json',restore=True)
redis_copy=LOCAL/'restored-redis-check.json';transfer(p,redis_copy)
redis_proof=compare_redis_snapshots(json.loads((backup/'redis.json').read_text(encoding='utf8')),json.loads(redis_copy.read_text(encoding='utf8')),int(time.time()*1000))
expected=json.loads((backup/'table-counts.json').read_text(encoding='utf8'))
queries=[]
for qualified in expected:
 db,table=qualified.split('.')
 if not re.fullmatch('[a-zA-Z0-9_]+',db+table):raise RuntimeError('Invalid table inventory')
 queries.append("SELECT '"+qualified+"',COUNT(*) FROM `"+db+"`.`"+table+"`")
actual={row.split('\t')[0]:int(row.split('\t')[1]) for row in mysql(' UNION ALL '.join(queries)).splitlines()}
if actual!=expected:raise RuntimeError('Restored record counts differ from the recovery bundle')
checksum_path=backup/'table-checksums.json'
if not checksum_path.exists():raise RuntimeError('Content-verifiable recovery requires a new quiescent backup bundle')
if table_checksums(mysql,expected)!=json.loads(checksum_path.read_text(encoding='utf8')):
 raise RuntimeError('Restored table contents differ from the recovery bundle')
for name,sha in manifest['mediaFiles'].items():
 if digest(LOCAL/'objects'/name)!=sha:raise RuntimeError('Restored media hash mismatch')
# Verify immutable images before boot, including browser assets.
images=json.loads((LOCAL/'images.json').read_text(encoding='utf8'))
for details in images.values():
 if upgrade:continue
 current=subprocess.check_output(['docker','image','inspect',details['imageId'],'--format','{{.Id}}']).decode().strip()
 if current!=details['imageId']:raise RuntimeError('Recorded image is unavailable; load the verified rollback archive before boot')
result={'status':'DATA_VERIFIED','tableCount':len(expected),'tableContentChecksumsMatched':True,'mediaFileCount':len(manifest['mediaFiles']),'backup':str(backup),**redis_proof,**broker_proof}
result['rawRestoreProof']={'status':'PASSED','sourceCommit':source['sourceCommit'],'imageDigests':frozen,'executedRecoveryImages':required_frozen,'oldApplicationsStarted':not bool(upgrade),'tableCount':len(expected),'tableCountsMatched':True,'tableContentChecksumsMatched':True,'mediaFileCount':len(manifest['mediaFiles']),**redis_proof,**broker_proof}
result['migrationUpgradeProof']={'status':'PENDING' if upgrade else 'NOT_REQUESTED'}
(LOCAL/'recovery-result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
try:
 if upgrade:
  result['migrationUpgradeProof'].update(sourceCommit=upgrade['sourceCommit'],previousSourceCommit=source['sourceCommit'],imageDigests=upgrade['imageDigests'],sourceManifestChecksums=upgrade['manifests'])
  (LOCAL/'recovery-result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
  # Applications have never started: migrate the verified old data in this isolated clone only.
  run([sys.executable,str(SOURCE/'setup.py'),'migrate'],'upgrade-migrations')
  result['migrationUpgradeProof']['appliedMigrations']=json.loads((LOCAL/'migrations.json').read_text(encoding='utf8'))
  compose=upgraded_configuration(compose,generated,upgrade['imageDigests'])
  result['migrationUpgradeProof']['generatedConfigurationFingerprint']=activate_generated_configs(home,generated_configs)
  (home/'compose.yaml').write_text(yaml.safe_dump(compose,sort_keys=False),encoding='utf8')
  copy_verified_manifests(upgrade_home,home,upgrade)
  result['migrationUpgradeProof']['status']='MIGRATED'
  (LOCAL/'recovery-result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
 run(COMPOSE+['up','-d','--wait'],'up-recovery')
 expected_apps=upgrade['imageDigests'] if upgrade else source['imageDigests']
 for service,image in expected_apps.items():
  running=subprocess.check_output(COMPOSE+['ps','-q',service],text=True).strip()
  if not running or '\n' in running or subprocess.check_output(['docker','inspect',running,'--format','{{.Image}}'],text=True).strip()!=image:raise RuntimeError('Recovered application runtime differs from its immutable source binding')
 if upgrade:result['migrationUpgradeProof']['runtimeImageBindingsMatched']=True
 # Search is a rebuildable projection; reset markers only after raw data comparison and incremental migration.
 mysql('UPDATE tj_search.course_metadata_projection SET processed_version=0;UPDATE tj_search.course_sales_projection SET processed_version=0')
 run([sys.executable,str(SOURCE/'prepare_search.py')],'restore-search-schema')
 run([sys.executable,str(SOURCE/'rebuild_search.py')],'restore-search-data')
 run([sys.executable,str(SOURCE/'recovery_smoke.py')],'recovery-business')
 run([sys.executable,str(SOURCE/'run_browser.py')],'recovery-browser')
 result['browser']=json.loads((LOCAL/'browser-result.json').read_text(encoding='utf8'))
 if result['browser'].get('status')!='PASSED':raise RuntimeError('Recovery browser report did not pass')
 if upgrade:result['migrationUpgradeProof'].update(status='PASSED',searchRebuilt=True,businessSmokePassed=True,browserPassed=True)
 result['status']='PASSED'
except Exception as error:
 result.update(status='FAILED',failureType=type(error).__name__)
 if upgrade:result['migrationUpgradeProof']['status']='FAILED'
 raise
finally:
 (LOCAL/'recovery-result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
print('Independent recovery PASSED: tables, media bytes, Redis, broker, search and business paths')
