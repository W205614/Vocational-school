"""Remove only this project's obsolete containers after all validation gates pass.
No volume or image deletion. Other Compose projects are excluded.
"""
from pathlib import Path
import subprocess,json,hashlib,yaml,datetime
BASE=Path(__file__).resolve().parent;LOCAL=BASE/'.local'
def main():
 backup=json.loads((LOCAL/'backup/manifest.json').read_text(encoding='utf8'))
 if hashlib.sha256((LOCAL/'backup/baseline.sql').read_bytes()).hexdigest()!=backup['businessDumpSha256']:raise RuntimeError('Backup checksum mismatch')
 gates=json.loads((LOCAL/'functional-gates.json').read_text(encoding='utf8'))['gates']
 required=['api_smoke','financial_smoke','auxiliary_smoke','page_api_smoke','projection_security_smoke','concurrency_edges','verify_observability','migration_audit']
 if any(gates.get(g,{}).get('status')!='PASSED' for g in required):raise RuntimeError('Required functional gates must pass')
 if json.loads((LOCAL/'recovery.json').read_text(encoding='utf8'))['status']!='PASSED':raise RuntimeError('Restart recovery must pass')
 if '11 passed' not in (LOCAL/'playwright-final.log').read_text(encoding='utf8') or '4 passed' not in (LOCAL/'experience-final.log').read_text(encoding='utf8'):raise RuntimeError('Browser acceptance must pass')
 ids=subprocess.check_output(['docker','ps','-aq']).decode().split()
 items=json.loads(subprocess.check_output(['docker','inspect',*ids])) if ids else []
 services=set(yaml.safe_load((BASE/'compose.yaml').read_text(encoding='utf8'))['services'])
 active=[i for i in items if i['Config'].get('Labels',{}).get('com.docker.compose.project')=='tianji-final' and i['Config']['Labels'].get('com.docker.compose.service') in services]
 if len(active)!=22 or any(not i['State']['Running'] or i['State'].get('Health',{}).get('Status')!='healthy' for i in active):raise RuntimeError('Exactly 22 final services must be healthy')
 obsolete=[]
 for i in items:
  labels=i['Config'].get('Labels') or {};project=labels.get('com.docker.compose.project');service=labels.get('com.docker.compose.service')
  if project in ['tianji-desktop','vocational-acceptance'] or project=='tianji-final' and service not in services:
   obsolete.append(i)
 if any(i['State']['Running'] for i in obsolete):raise RuntimeError('Stop obsolete services before cleanup')
 volume_ids={m['Name'] for i in obsolete for m in i['Mounts'] if m['Type']=='volume'}
 record={'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'removed':[{'id':i['Id'],'name':i['Name'],'project':i['Config']['Labels']['com.docker.compose.project'],'image':i['Image']} for i in obsolete],'preservedVolumes':sorted(volume_ids),'finalContainers':len(active),'backupVerified':True,'otherProjectsUntouched':True}
 (LOCAL/'cleanup-planned.json').write_text(json.dumps(record,indent=2),encoding='utf8')
 if obsolete:subprocess.run(['docker','rm',*[i['Id'] for i in obsolete]],check=True,stdout=subprocess.DEVNULL)
 for name in volume_ids:subprocess.run(['docker','volume','inspect',name],check=True,stdout=subprocess.DEVNULL)
 if obsolete:(LOCAL/'cleanup-completed.json').write_text(json.dumps(record,indent=2),encoding='utf8')
 print('Removed '+str(len(obsolete))+' obsolete Tianji containers; recovery volumes and other projects preserved',flush=True)
if __name__=='__main__':main()
