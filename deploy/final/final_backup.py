"""Capture the validated final data and private configuration for local recovery."""
from pathlib import Path
import subprocess,json,hashlib,zipfile,datetime
from runtime import configure
p=configure();BASE=Path(__file__).resolve().parent;LOCAL=BASE/'.local'
def main():
 target=LOCAL/'backup/final-business.sql'
 with target.open('wb') as output:
  result=subprocess.run(p.COMPOSE+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --databases '+' '.join(p.DATABASES)],stdout=output,stderr=subprocess.PIPE)
 if result.returncode:raise RuntimeError('Final backup failed')
 if target.stat().st_size<10000:raise RuntimeError('Unexpected final backup size')
 archive=LOCAL/'backup/final-private-config.zip'
 with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED) as z:
  for f,name in [(BASE/'.env','.env'),(LOCAL/'signing.jks','signing.jks'),(LOCAL/'app.env','app.env')]:z.write(f,name)
  for f in (LOCAL/'configs').glob('*.yml'):z.write(f,'configs/'+f.name)
 manifest={'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'databaseCount':12,'sqlBytes':target.stat().st_size,'sqlSha256':hashlib.sha256(target.read_bytes()).hexdigest(),'privateConfigSha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'mediaFiles':{str(f.relative_to(LOCAL/'objects')):hashlib.sha256(f.read_bytes()).hexdigest() for f in (LOCAL/'objects').rglob('*') if f.is_file()}}
 (LOCAL/'backup/final-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
 print('Validated final SQL and private configuration backed up locally',flush=True)
if __name__=='__main__':main()
