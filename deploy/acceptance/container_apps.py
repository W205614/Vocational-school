"""Generate private Linux configurations and reproducible images; full startup is an explicit action."""
import argparse,json,shutil,subprocess,hashlib,yaml
from pathlib import Path
from prepare import BASE,LOCAL,local_secrets
from start_service import MODULES
ROOT=BASE.parents[1];DIR=LOCAL/'containers'
def command(args,log=None):
 if log:
  with log.open('w',encoding='utf8') as output:return subprocess.run(args,check=True,stdout=output,stderr=subprocess.STDOUT)
 return subprocess.run(args,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
def rewrite(value,mode):
 if isinstance(value,dict):return {k:rewrite(v,mode) for k,v in value.items()}
 if isinstance(value,list):return [rewrite(v,mode) for v in value]
 if isinstance(value,str):
  for name,(_,_,port,_) in MODULES.items():
   value=value.replace('http://127.0.0.1:'+str(port),'http://'+('host.docker.internal' if mode=='hybrid' else 'app-'+name)+':'+str(port))
 return value
def prepare():
 DIR.mkdir(parents=True,exist_ok=True)
 secret=local_secrets();(DIR/'env').write_text('TJ_INTERNAL_TOKEN='+secret['ACCEPTANCE_INTERNAL_TOKEN']+'\n',encoding='utf8')
 shutil.copyfile(LOCAL/'acceptance-signing.jks',DIR/'signing.jks')
 probe=DIR/'probe';probe.mkdir(exist_ok=True)
 command(['E:/Program Files/jdk/bin/javac.exe','-encoding','UTF-8','-d',str(probe),str(BASE/'docker/HealthProbe.java')],DIR/'probe-build.log')
 records={}
 for name,(module,main,port,db) in MODULES.items():
  metadata=json.loads((LOCAL/(name+'-pid.json')).read_text(encoding='utf8'))
  jar=LOCAL/'runtime'/name/metadata['artifactSha256']/'app.jar'
  if hashlib.sha256(jar.read_bytes()).hexdigest()!=metadata['artifactSha256']:raise RuntimeError('Snapshot mismatch '+name)
  context=DIR/'build'/name;context.mkdir(parents=True,exist_ok=True)
  shutil.copyfile(jar,context/'app.jar');shutil.copyfile(BASE/'docker/Dockerfile.jvm',context/'Dockerfile');shutil.copyfile(probe/'HealthProbe.class',context/'HealthProbe.class')
  for mode in ['hybrid','full']:
   target=DIR/mode;target.mkdir(exist_ok=True)
   config=rewrite(yaml.safe_load((LOCAL/(name+'-application.yml')).read_text(encoding='utf8')),mode)
   config['server']['address']='0.0.0.0'
   spring=config['spring']
   if 'datasource' in spring:spring['datasource']['url']=spring['datasource']['url'].replace('127.0.0.1:23316','mysql:3306')
   spring['data']['redis'].update(host='redis',port=6379)
   spring['rabbitmq'].update(host='rabbitmq',port=5672)
   if name=='search':spring['elasticsearch']['uris']='http://elasticsearch:9200'
   if name=='auth':config['encrypt']['key-store']['location']='file:/run/acceptance/signing.jks'
   if name=='media':config['tj']['local-storage']['directory']='/run/objects'
   (target/(name+'.yml')).write_text(yaml.safe_dump(config,sort_keys=False,allow_unicode=True),encoding='utf8')
  records[name]=dict(jarSha256=metadata['artifactSha256'],image='vocational-acceptance/'+name+':java21')
 for app in ['student','admin']:
  context=DIR/'build'/app;context.mkdir(parents=True,exist_ok=True)
  shutil.copytree(ROOT/'frontend/apps'/app/'dist',context/'dist',dirs_exist_ok=True)
  shutil.copyfile(BASE/'docker/Dockerfile.web',context/'Dockerfile');shutil.copyfile(BASE/'docker/nginx.conf.template',context/'nginx.conf.template')
  records[app]=dict(image='vocational-acceptance/'+app+':v2')
 (DIR/'images.json').write_text(json.dumps(records,indent=2),encoding='utf8')
 print('Private Linux configurations and image contexts prepared',flush=True)
def build(names):
 records=json.loads((DIR/'images.json').read_text(encoding='utf8'))
 for name in names:
  record=records[name];command(['docker','build','--pull=false','-t',record['image'],str(DIR/'build'/name)],DIR/(name+'-build.log'))
  record['imageId']=command(['docker','image','inspect',record['image'],'--format','{{.Id}}']).stdout.decode().strip()
  print('Built '+name,flush=True)
 (DIR/'images.json').write_text(json.dumps(records,indent=2),encoding='utf8')
def main():
 parser=argparse.ArgumentParser();parser.add_argument('action',choices=['prepare','build']);parser.add_argument('--only');args=parser.parse_args()
 names=args.only.split(',') if args.only else list(MODULES)+['student','admin']
 if any(n not in list(MODULES)+['student','admin'] for n in names):raise ValueError('Unknown application')
 if args.action=='prepare':prepare()
 else:build(names)
if __name__=='__main__':main()
