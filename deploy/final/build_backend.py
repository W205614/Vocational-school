"""Build selected packaged backend artifacts without putting runtime secrets into the image."""
from pathlib import Path
import argparse,hashlib,json,re,shutil,subprocess,sys,zipfile
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[1];LOCAL=BASE/'.local'
sys.path.insert(0,str(BASE.parent/'acceptance'))
from start_service import MODULES

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--only',required=True);parser.add_argument('--tag',required=True);args=parser.parse_args()
 names=args.only.split(',')
 if any(name not in MODULES for name in names) or not re.fullmatch('[a-z0-9][a-z0-9._-]{0,127}',args.tag):raise ValueError('Unknown service or invalid image tag')
 manifest_path=LOCAL/'backend-images.json'
 manifest=json.loads(manifest_path.read_text(encoding='utf8')) if manifest_path.exists() else {}
 for name in names:
  module=MODULES[name][0];jar=ROOT/module/'target'/(Path(module).name+'.jar')
  with zipfile.ZipFile(jar) as packaged:
   if not any(path.startswith('BOOT-INF/classes/') for path in packaged.namelist()):raise RuntimeError('Build a packaged Spring Boot artifact first')
  context=LOCAL/'backend-build'/name;context.mkdir(parents=True,exist_ok=True)
  shutil.copyfile(jar,context/'app.jar');shutil.copyfile(BASE.parent/'acceptance/docker/Dockerfile.jvm',context/'Dockerfile')
  with (context/'build.log').open('wb') as log:
   subprocess.run(['javac','-encoding','UTF-8','-d',str(context),str(BASE.parent/'acceptance/docker/HealthProbe.java')],check=True,stdout=log,stderr=subprocess.STDOUT)
   tag='tianji-final/'+name+':'+args.tag;subprocess.run(['docker','build','--pull=false','-t',tag,str(context)],check=True,stdout=log,stderr=subprocess.STDOUT)
  manifest[name]={'image':tag,'finalTag':tag,'imageId':subprocess.check_output(['docker','image','inspect',tag,'--format','{{.Id}}']).decode().strip(),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest()}
  print('Built '+tag,flush=True)
 manifest_path.write_text(json.dumps(manifest,indent=2),encoding='utf8')
if __name__=='__main__':main()
