"""Reuse acceptance gates against the final isolated stack; defaults remain unchanged."""
from pathlib import Path
import sys,os,runpy
BASE=Path(__file__).resolve().parent;ACC=BASE.parent/'acceptance'
def configure():
 sys.path.insert(0,str(ACC))
 import prepare
 prepare.LOCAL=BASE/'.local'
 prepare.COMPOSE=['docker','compose','-p','tianji-final','-f',str(BASE/'compose.yaml'),'--env-file',str(BASE/'.env')]
 def secrets():
  values=dict(line.split('=',1) for line in (BASE/'.env').read_text(encoding='utf8').splitlines() if '=' in line)
  return dict(ACCEPTANCE_MQ_USERNAME='tianji',ACCEPTANCE_DB_PASSWORD=values['FINAL_DB_PASSWORD'],ACCEPTANCE_MQ_PASSWORD=values['FINAL_MQ_PASSWORD'],ACCEPTANCE_INTERNAL_TOKEN=values['FINAL_INTERNAL_TOKEN'],ACCEPTANCE_GRAFANA_PASSWORD=values['FINAL_GRAFANA_PASSWORD'])
 prepare.local_secrets=secrets
 return prepare
if __name__=='__main__':
 prepare=configure();name=sys.argv[1];sys.argv=sys.argv[1:]
 allowed={'migrate','seed_accounts','browser_fixture','api_smoke','financial_smoke','auxiliary_smoke','page_api_smoke','projection_security_smoke','concurrency_edges','prepare_search','rebuild_search','migration_audit','verify_observability'}
 if name not in allowed:raise ValueError('Unsupported final gate')
 runpy.run_path(str(ACC/(name+'.py')),run_name='__main__')
