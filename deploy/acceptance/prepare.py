"""Isolated acceptance data clone. Original deployment is only read."""
from pathlib import Path
import subprocess,secrets,argparse,json,time
BASE=Path(__file__).resolve().parent
LOCAL=BASE/'.local'
COMPOSE=['docker','compose','--project-name','vocational-acceptance','--file',str(BASE/'compose.yaml'),'--env-file',str(BASE/'.env')]
DATABASES=['tj_auth','tj_user','tj_course','tj_learning','tj_trade','tj_promotion','tj_exam','tj_remark','tj_pay','tj_search','tj_message','tj_media']
def run(args,**kwargs):
    result=subprocess.run(args,check=False,**kwargs)
    if result.returncode: raise RuntimeError('Command failed: '+args[0]+' (output saved locally)')
    return result
def mysql(sql,db=None):
    cmd=COMPOSE+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B'+(' '+db if db else '')]
    result=subprocess.run(cmd,input=sql.encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode:
        LOCAL.mkdir(exist_ok=True)
        (LOCAL/'mysql-errors.log').write_bytes(result.stderr)
        raise RuntimeError('Isolated SQL failed; diagnostics in private .local/mysql-errors.log')
    return result.stdout.decode('utf8').strip()

def local_secrets():
    LOCAL.mkdir(exist_ok=True)
    path=BASE/'.env'
    if not path.exists():path.write_text('ACCEPTANCE_DB_PASSWORD='+secrets.token_hex(24)+'\nACCEPTANCE_MQ_PASSWORD='+secrets.token_hex(24)+'\n',encoding='utf8')
    values=dict(line.split('=',1) for line in path.read_text(encoding='utf8').splitlines() if '=' in line)
    if 'ACCEPTANCE_INTERNAL_TOKEN' not in values:
        values['ACCEPTANCE_INTERNAL_TOKEN']=secrets.token_hex(32)
        with path.open('a',encoding='utf8') as output:output.write('\nACCEPTANCE_INTERNAL_TOKEN='+values['ACCEPTANCE_INTERNAL_TOKEN']+'\n')
    if 'ACCEPTANCE_GRAFANA_PASSWORD' not in values:
        values['ACCEPTANCE_GRAFANA_PASSWORD']=secrets.token_hex(24)
        with path.open('a',encoding='utf8') as output:output.write('ACCEPTANCE_GRAFANA_PASSWORD='+values['ACCEPTANCE_GRAFANA_PASSWORD']+'\n')
    return values
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--clone',action='store_true');args=parser.parse_args()
    LOCAL.mkdir(exist_ok=True)
    env=BASE/'.env'
    local_secrets()
    run(COMPOSE+['up','-d','--wait'],stdout=open(LOCAL/'infra.log','w'),stderr=subprocess.STDOUT)
    if args.clone:
        if mysql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='tj_trade'")!='0':
            raise RuntimeError('Refusing to overwrite an existing acceptance clone')
        dump=LOCAL/'baseline.sql'
        with dump.open('wb') as output:
            run(['docker','exec','tianji-desktop-mysql-1','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --databases '+' '.join(DATABASES)],
                stdout=output,stderr=open(LOCAL/'dump-errors.log','wb'))
        with dump.open('rb') as source:
            run(COMPOSE+['exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],stdin=source,stdout=open(LOCAL/'restore.log','w'),stderr=subprocess.STDOUT)
        (LOCAL/'baseline-counts.json').write_text(json.dumps({db:mysql("SELECT table_name,table_rows FROM information_schema.tables WHERE table_schema='"+db+"' ORDER BY table_name") for db in DATABASES},ensure_ascii=False,indent=2),encoding='utf8')
    print('Isolated infrastructure ready. Existing deployment was not modified.')
if __name__=='__main__':main()
