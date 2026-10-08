"""Retire only an owned measurement cohort, retaining its data and evidence."""
import datetime
import json
from pathlib import Path
import subprocess

def validate_cohort(home, project, containers, root):
    home=Path(home).resolve(); owned=Path(root).resolve()/'deploy/compact/.local/optimization'
    if home==owned or not home.is_relative_to(owned):
        raise RuntimeError('Measurement home is outside the owned audit directory')
    if project not in {'tianji-opt-'+stage+'-'+mode for stage in ('pre','post') for mode in ('compact','standalone')}:
        raise RuntimeError('Only explicit pre/post measurement cohorts may be retired')
    for container in containers:
        labels=container['Config'].get('Labels',{})
        if labels.get('com.docker.compose.project')!=project:
            raise RuntimeError('Container project differs from the measurement cohort')
        configs=labels.get('com.docker.compose.project.config_files','').split(',')
        if len(configs)!=1 or Path(configs[0]).resolve()!=home/'compose.yaml':
            raise RuntimeError('Container belongs to a different Compose file')
    return home

def retire(home,project):
    from setup import ROOT
    home=validate_cohort(home,project,[],ROOT)
    ids=subprocess.check_output(['docker','ps','-aq','--filter','label=com.docker.compose.project='+project],text=True).split()
    if not ids:return
    containers=json.loads(subprocess.check_output(['docker','inspect',*ids],text=True))
    validate_cohort(home,project,containers,ROOT)
    folders=home/'.local/container-retirement';folders.mkdir(parents=True,exist_ok=True)
    stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    journal=folders/(stamp+'.json')
    record={'status':'STARTED','project':project,'volumesRemoved':0,'imagesRemoved':0,
            'containers':[{'id':c['Id'],'name':c['Name'],'image':c['Image'],'mounts':c['Mounts']} for c in containers]}
    journal.write_text(json.dumps(record,indent=2),encoding='utf8')
    volumes_before=set(subprocess.check_output(['docker','volume','ls','-q'],text=True).split())
    running=[c['Id'] for c in containers if c['State']['Running']]
    if running:subprocess.run(['docker','stop','-t','60',*running],check=True,stdout=subprocess.DEVNULL)
    fresh=json.loads(subprocess.check_output(['docker','inspect',*ids],text=True))
    validate_cohort(home,project,fresh,ROOT)
    if any(c['State']['Running'] for c in fresh):raise RuntimeError('A cohort container restarted; refusing removal')
    subprocess.run(['docker','rm',*ids],check=True,stdout=subprocess.DEVNULL)
    volumes_after=set(subprocess.check_output(['docker','volume','ls','-q'],text=True).split())
    if not volumes_before.issubset(volumes_after):raise RuntimeError('A volume disappeared during cohort retirement')
    record.update(status='PASSED',retiredContainers=len(ids),finishedAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
    journal.write_text(json.dumps(record,indent=2),encoding='utf8')
    print('Measurement containers retired; volumes, images and raw evidence retained: '+project,flush=True)
