"""Immutable verified recovery bundles, including actual media bytes."""
from pathlib import Path
import argparse, datetime, hashlib, json, os, subprocess, tempfile, zipfile
from contextlib import contextmanager
from runtime import configure
BASE=Path(__file__).resolve().parent
LOCAL=BASE/'.local'

def digest(path):
 with path.open('rb') as source:return hashlib.file_digest(source,'sha256').hexdigest()

def verify(directory):
 manifest=json.loads((directory/'manifest.json').read_text(encoding='utf8'))
 for name,expected in manifest['files'].items():
  path=directory/name
  if path.parent!=directory or not path.is_file() or digest(path)!=expected:raise RuntimeError('Recovery checksum mismatch')
 with zipfile.ZipFile(directory/'media.zip') as archive:
  if archive.testzip() is not None or set(archive.namelist())!=set(manifest['mediaFiles']):raise RuntimeError('Corrupt media inventory')
  for name,expected in manifest['mediaFiles'].items():
   if name.startswith('/') or '..' in Path(name).parts or '\\' in name:raise RuntimeError('Invalid archive path')
   with archive.open(name) as source:
    if hashlib.file_digest(source,'sha256').hexdigest()!=expected:raise RuntimeError('Media checksum mismatch')
 with zipfile.ZipFile(directory/'private-config.zip') as archive:
  if archive.testzip() is not None:raise RuntimeError('Corrupt private configuration')
 return manifest

@contextmanager
def quiesce(p):
 services=subprocess.check_output(p.COMPOSE+['ps','--status','running','--services']).decode().splitlines()
 apps=[name for name in services if name.startswith(('app-','web-'))]
 try:
  if apps:subprocess.run(p.COMPOSE+['stop','-t','60',*apps],check=True,stdout=subprocess.DEVNULL)
  yield
 finally:
  if apps:subprocess.run(p.COMPOSE+['start',*apps],check=True,stdout=subprocess.DEVNULL)

def capture(runtime=None,source_base=None,quiesced=False):
 p=runtime or configure()
 source=Path(source_base).resolve() if source_base else BASE
 local=source/'.local'
 base=Path(os.environ.get('TJ_BACKUP_HOME',str(local/'backup'))).resolve();base.mkdir(parents=True,exist_ok=True)
 stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
 directory=Path(tempfile.mkdtemp(prefix='.incomplete-',dir=base));sql=directory/'business.sql'
 with sql.open('wb') as output:
  result=subprocess.run(p.COMPOSE+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --routines --triggers --databases '+' '.join(p.DATABASES)],stdout=output,stderr=subprocess.PIPE)
 if result.returncode or sql.stat().st_size<10000:raise RuntimeError('Backup failed; incomplete bundle retained')
 with zipfile.ZipFile(directory/'private-config.zip','w',compression=zipfile.ZIP_DEFLATED) as archive:
  for path,name in [(source/'.env','.env'),(local/'signing.jks','signing.jks'),(local/'app.env','app.env'),(local/'database-accounts.json','database-accounts.json'),(local/'accounts.json','accounts.json'),(local/'browser-fixture.json','browser-fixture.json'),(local/'images.json','images.json'),(local/'load-accounts.json','load-accounts.json'),(local/'build-source.json','build-source.json'),(local/'infrastructure-images.json','infrastructure-images.json')]:
   if path.exists():archive.write(path,name)
  for path in sorted((local/'configs').iterdir()):
   if path.is_file() and path.suffix in {'.yml','.security'}:archive.write(path,'configs/'+path.name)
  if (source/'compose.yaml').exists():archive.write(source/'compose.yaml','compose.yaml')
 media={}
 with zipfile.ZipFile(directory/'media.zip','w',compression=zipfile.ZIP_STORED) as archive:
  for path in sorted((local/'objects').rglob('*')):
   if path.is_symlink():raise RuntimeError('Recovery media must not contain symlinks')
   if path.is_file():
    name=path.relative_to(local/'objects').as_posix();before=digest(path);archive.write(path,name)
    if digest(path)!=before:raise RuntimeError('Media changed during capture; retry backup')
    media[name]=before
 from redis_snapshot import transfer
 transfer(p,directory/'redis.json')
 names=p.mysql("SELECT table_schema,table_name FROM information_schema.tables WHERE table_schema IN ("+','.join("'"+db+"'" for db in p.DATABASES)+") AND table_type='BASE TABLE' ORDER BY table_schema,table_name").splitlines()
 queries=[]
 for row in names:
  db,table=row.split('\t');queries.append("SELECT '"+db+'.'+table+"',COUNT(*) FROM `"+db+"`.`"+table+"`")
 counts={row.split('\t')[0]:int(row.split('\t')[1]) for row in p.mysql(' UNION ALL '.join(queries)).splitlines()}
 (directory/'table-counts.json').write_text(json.dumps(counts,indent=2),encoding='utf8')
 broker=None
 if quiesced:
  node=subprocess.check_output(p.COMPOSE+['exec','-T','rabbitmq','hostname']).decode().strip()
  subprocess.run(p.COMPOSE+['exec','-T','rabbitmq','rabbitmqctl','stop_app'],check=True,stdout=subprocess.DEVNULL)
  try:
   with (directory/'rabbitmq.tar.gz').open('wb') as output:
    result=subprocess.run(p.COMPOSE+['exec','-T','-u','0','rabbitmq','tar','-czf','-','-C','/var/lib/rabbitmq','.'],stdout=output,stderr=subprocess.PIPE)
   if result.returncode:raise RuntimeError('Broker bytes capture failed')
   broker={'hostname':node,'file':'rabbitmq.tar.gz'}
  finally:subprocess.run(p.COMPOSE+['exec','-T','rabbitmq','rabbitmqctl','start_app'],check=True,stdout=subprocess.DEVNULL)
 files=['business.sql','media.zip','private-config.zip','redis.json','table-counts.json']+(['rabbitmq.tar.gz'] if broker else [])
 manifest={'format':3,'quiesced':quiesced,'broker':broker,'queueRecovery':'broker-bytes' if broker else 'NOT_CAPTURED','at':stamp,'databaseCount':len(p.DATABASES),'files':{name:digest(directory/name) for name in files},'mediaFiles':media}
 (directory/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8');verify(directory)
 completed=base/stamp;directory.rename(completed)
 print('Verified recovery bundle: '+str(completed),flush=True);return completed

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--verify',type=Path);parser.add_argument('--quiesce',action='store_true');args=parser.parse_args()
 if args.verify:verify(args.verify.resolve());print('Recovery checksums and media bytes verified')
 else:
  if args.quiesce:
   p=configure()
   with quiesce(p):capture(p,quiesced=True)
  else:capture()
