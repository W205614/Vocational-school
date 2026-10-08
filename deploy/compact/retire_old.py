"""Retire explicitly named legacy/test containers without deleting recovery data."""
import argparse, datetime, json, subprocess, sys
from pathlib import Path
from setup import LOCAL, PROJECT, ROOT, COMPOSE
from evidence import validate,manifest

sys.path.insert(0,str(ROOT/'deploy/final'))
from final_backup import verify

def output(args):
 return subprocess.check_output(args).decode('utf8').strip()

def inspect(ids):
 return json.loads(output(['docker','inspect',*ids])) if ids else []

def main():
 parser=argparse.ArgumentParser()
 parser.add_argument('--old-backup',type=Path,required=True)
 parser.add_argument('--compact-backup',type=Path,required=True)
 parser.add_argument('--projects',nargs='+',required=True)
 args=parser.parse_args()
 if PROJECT!='tianji-compact':raise RuntimeError('Retirement requires the final compact project')
 projects=set(args.projects)
 if not projects or any(p not in {'tianji-final','tianji-desktop','vocational-acceptance','tianji-acceptance'} and not p.startswith(('tianji-recovery-','tianji-redis-proof-','tianji-opt-')) for p in projects):
  raise RuntimeError('Only explicitly named legacy or recovery test projects are accepted')
 for backup in [args.old_backup,args.compact_backup]:
  if not verify(backup.resolve()).get('quiesced'):raise RuntimeError('A complete quiescent backup is required')
 expected=json.loads((LOCAL/'release-run.json').read_text(encoding='utf8'))
 gate=json.loads((LOCAL/'evidence'/expected['releaseRunId']/'release-gate.json').read_text(encoding='utf8'))
 if not validate(gate,expected):raise RuntimeError('The final bound release gate must pass before retirement')
 live=manifest(LOCAL,COMPOSE,ROOT,expected['snapshotPath'])
 if any(live[key]!=expected[key] for key in ('sourceCommit','imageDigests','configFingerprint','baseSnapshotFingerprint')):raise RuntimeError('Final deployment changed after acceptance')
 browser=json.loads((LOCAL/'browser-result.json').read_text(encoding='utf8'))
 if browser.get('status')!='PASSED' or browser.get('failed')!=0:raise RuntimeError('Browser regression must pass before retirement')
 active=inspect(output(['docker','ps','-aq','--filter','label=com.docker.compose.project='+PROJECT]).splitlines())
 if len(active)!=11 or any(c['State'].get('Health',{}).get('Status')!='healthy' for c in active):
  raise RuntimeError('All 11 final containers must be healthy')
 ids=[]
 for project in sorted(projects):ids.extend(output(['docker','ps','-aq','--filter','label=com.docker.compose.project='+project]).splitlines())
 containers=inspect(ids)
 if not containers:raise RuntimeError('No explicitly named containers remain')
 for container in containers:
  labels=container['Config']['Labels']
  files=labels.get('com.docker.compose.project.config_files','').split(',')
  if not files or any(not Path(path).resolve().is_relative_to(ROOT) for path in files):raise RuntimeError('Legacy container does not belong to this workspace')
 volumes={m['Name'] for c in containers for m in c['Mounts'] if m['Type']=='volume'}
 for project in projects:volumes.update(output(['docker','volume','ls','-q','--filter','label=com.docker.compose.project='+project]).splitlines())
 at=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
 folder=LOCAL/'retirement'/at;folder.mkdir(parents=True)
 # Inspect data contains private environment values; this folder is Git-ignored.
 (folder/'containers-private.json').write_text(json.dumps(containers,indent=2),encoding='utf8')
 plan={'projects':sorted(projects),'containerIds':[c['Id'] for c in containers],'preservedVolumes':sorted(volumes),
       'backups':[str(args.old_backup.resolve()),str(args.compact_backup.resolve())]}
 (folder/'plan.json').write_text(json.dumps(plan,indent=2),encoding='utf8')
 for c in containers:
  fresh=inspect([c['Id']])[0]
  if fresh['Config']['Labels'].get('com.docker.compose.project') not in projects or fresh['Config']['Labels']!=c['Config']['Labels']:raise RuntimeError('Container ownership changed')
  if fresh['State']['Running']:
   subprocess.run(['docker','stop','-t','60',c['Id']],check=True,stdout=subprocess.DEVNULL)
  subprocess.run(['docker','rm',c['Id']],check=True,stdout=subprocess.DEVNULL) # Deliberately no -v.
 for project in projects:
  if output(['docker','ps','-aq','--filter','label=com.docker.compose.project='+project]):raise RuntimeError('Obsolete containers remain')
 available=set(output(['docker','volume','ls','-q']).splitlines())
 if not volumes.issubset(available):raise RuntimeError('A retained volume is missing')
 active=inspect(output(['docker','ps','-aq','--filter','label=com.docker.compose.project='+PROJECT]).splitlines())
 if len(active)!=11 or any(c['State'].get('Health',{}).get('Status')!='healthy' for c in active):raise RuntimeError('Final deployment became unhealthy')
 result={'status':'PASSED','removedContainers':len(containers),'retainedVolumes':len(volumes),
         'finalContainers':len(active),'finalHealthy':len(active),'volumeDeletion':False,'imageDeletion':False,
         'projects':sorted(projects),'at':at,'backupsVerified':2}
 (folder/'result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
 (LOCAL/'retirement-result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
 print(json.dumps(result))

if __name__=='__main__':main()
