"""Build each UI into its own image; keep credentials outside the build context."""
from pathlib import Path
import shutil,subprocess,json,hashlib
BASE=Path(__file__).resolve().parent;ROOT=BASE.parents[1];LOCAL=BASE/'.local'
def main():
 manifest={}
 for app in ['student','admin']:
  dist=ROOT/'frontend/apps'/app/'dist'
  if not (dist/'index.html').exists():raise RuntimeError('Build the frontend before preparing deployment')
  context=LOCAL/'web'/app;context.mkdir(parents=True,exist_ok=True)
  shutil.copytree(dist,context/'dist',dirs_exist_ok=True)
  for source,target in [('Dockerfile.web','Dockerfile'),('nginx.conf.template','nginx.conf.template')]:shutil.copyfile(BASE.parent/'acceptance/docker'/source,context/target)
  tag='tianji-final/'+app+':ee045c7'
  with (LOCAL/(app+'-image.log')).open('wb') as log:
   subprocess.run(['docker','build','--pull=false','-t',tag,str(context)],check=True,stdout=log,stderr=subprocess.STDOUT)
  manifest[app]={'image':tag,'imageId':subprocess.check_output(['docker','image','inspect',tag,'--format','{{.Id}}']).decode().strip(),'assets':{str(p.relative_to(dist)):hashlib.sha256(p.read_bytes()).hexdigest() for p in dist.rglob('*') if p.is_file()}}
 (LOCAL/'web-images.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
 print('Student and admin images built separately from current frontend assets',flush=True)
if __name__=='__main__':main()
