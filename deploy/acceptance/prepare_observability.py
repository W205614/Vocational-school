"""Generate isolated scrape credentials; never store secrets in tracked provisioning."""
from prepare import LOCAL,local_secrets
from start_service import MODULES
import yaml
def main():
 secret=local_secrets();directory=LOCAL/'observability';directory.mkdir(parents=True,exist_ok=True)
 (directory/'internal-token').write_text(secret['ACCEPTANCE_INTERNAL_TOKEN'],encoding='utf8')
 config={'global':{'scrape_interval':'5s'},'rule_files':['/etc/prometheus/alerts.yml'],'scrape_configs':[]}
 for name,(_,_,port,_) in MODULES.items():
  if name=='gateway':continue
  config['scrape_configs'].append({'job_name':name,'metrics_path':'/actuator/prometheus','http_headers':{'X-Internal-Token':{'files':['/etc/prometheus/internal-token']}},'static_configs':[{'targets':['host.docker.internal:'+str(port)]}]})
 (directory/'prometheus.yml').write_text(yaml.safe_dump(config,sort_keys=False),encoding='utf8')
 print('Isolated observability configuration generated.')
if __name__=='__main__':main()
