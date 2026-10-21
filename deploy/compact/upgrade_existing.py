"""Stage seven source-bound images, then migrate an existing deployment under one host lock."""

from runtime_compose import active_compose, compose_command
from pathlib import Path
import argparse
import json
import shutil
import subprocess
import sys
import yaml
import setup
from maintenance import atomic_json, maintenance_lock
from operations_config import upgrade, observability, MONITORS
from recovery_upgrade import load_upgrade


def stage():
    from build_provenance import clean_commit
    commit = clean_commit(setup.ROOT)
    journal = json.loads((setup.LOCAL/'package-source.json').read_text(encoding='utf8'))
    if journal.get('sourceCommit') != commit or journal.get('mode') != 'compact':
        raise RuntimeError('Build all compact images from the current committed source first')
    images = json.loads((setup.LOCAL/'images.json').read_text(encoding='utf8'))
    required = {'identity','commerce','education','support','gateway','student','admin'}
    if set(images) != required: raise RuntimeError('All seven fresh images are required')
    candidate = setup.LOCAL/'candidates'/commit
    candidate.mkdir(parents=True, exist_ok=True); (candidate/'.local').mkdir(exist_ok=True)
    binding = {('web-' if name in {'student','admin'} else 'app-')+name: info['imageId'] for name,info in images.items()}
    atomic_json(candidate/'.local/images.json', images)
    atomic_json(candidate/'.local/build-source.json', {'sourceCommit':commit,'imageDigests':binding})
    load_upgrade(candidate, setup.ROOT)
    print('Verified candidate: '+str(candidate))
    return candidate


def deploy(candidate, backup):
    candidate=Path(candidate).resolve(); backup=Path(backup).resolve()
    if not candidate.is_relative_to((setup.LOCAL/'candidates').resolve()):
        raise RuntimeError('Candidate must belong to this deployment project')
    sys.path.insert(0,str(setup.SOURCE.parent/'final'))
    from final_backup import verify
    saved=verify(backup)
    if not backup.is_relative_to((setup.LOCAL/'backup').resolve()) or not saved.get('quiesced'):
        raise RuntimeError('A verified project-local full backup is required')
    proof=load_upgrade(candidate,setup.ROOT)
    with maintenance_lock(setup.LOCAL):
        # Recheck and pin while holding the same lock as automatic retention.
        saved=verify(backup)
        if not saved.get('quiesced'):raise RuntimeError('A verified full backup is required')
        atomic_json(backup/'publication-pin.json',{'sourceCommit':proof['sourceCommit'],'purpose':'publication-rollback'})
        env=setup.secrets_config()[0]
        if any(not env.get(key) for key in ('ACCEPTANCE_INTERNAL_TOKEN','ACCEPTANCE_GRAFANA_PASSWORD')):
            raise RuntimeError('Existing deployment credentials are incomplete')
        planned=observability(yaml.safe_load((active_compose(setup.BASE)).read_text(encoding='utf8')),setup.OFFSET)
        monitor_images={name:subprocess.check_output(['docker','image','inspect',planned['services'][name]['image'],'--format','{{.Id}}'],text=True,timeout=30).strip() for name in MONITORS}
        runtime=setup.configure_acceptance()
        broker=subprocess.check_output(setup.COMPOSE+['ps','-aq','rabbitmq'],text=True,timeout=30).strip()
        if not broker or '\n' in broker:raise RuntimeError('Existing RabbitMQ node identity is required')
        broker_hostname=subprocess.check_output(['docker','inspect',broker,'--format','{{.Config.Hostname}}'],text=True,timeout=30).strip()
        running=subprocess.check_output(setup.COMPOSE+['ps','--status','running','--services'],text=True).splitlines()
        apps=[name for name in running if name.startswith(('app-','web-'))]
        if apps:setup.run(setup.COMPOSE+['stop','-t','90',*apps],'upgrade-stop')
        # No automatic restore of an old SQL snapshot: migrations and newly accepted facts are retained on failure.
        try:
            previous=upgrade(setup.BASE,env['ACCEPTANCE_INTERNAL_TOKEN'],setup.OFFSET,broker_hostname)
            import migrate
            migrate.main()
            compose=yaml.safe_load((active_compose(setup.BASE)).read_text(encoding='utf8'))
            for name,image in proof['imageDigests'].items():compose['services'][name]['image']=image
            for name in MONITORS:
                compose['services'][name]['image']=monitor_images[name]
            (active_compose(setup.BASE)).write_text(yaml.safe_dump(compose,sort_keys=False),encoding='utf8')
            for name in ('images.json','build-source.json'):
                shutil.copyfile(candidate/'.local'/name,setup.LOCAL/name)
            setup.up()
            atomic_json(setup.LOCAL/'upgrade-result.json',{'status':'PASSED','sourceCommit':proof['sourceCommit'],'backup':str(backup),'previousConfiguration':str(previous),'imageDigests':proof['imageDigests']})
            print('Existing deployment upgraded; original business volumes retained and services left running.')
        except Exception:
            atomic_json(setup.LOCAL/'upgrade-result.json',{'status':'FAILED','sourceCommit':proof['sourceCommit'],'backup':str(backup),'automaticDataRestore':False})
            raise


def main():
    parser=argparse.ArgumentParser();commands=parser.add_subparsers(dest='action',required=True)
    commands.add_parser('stage')
    apply=commands.add_parser('deploy');apply.add_argument('--candidate',type=Path,required=True);apply.add_argument('--backup',type=Path,required=True)
    args=parser.parse_args()
    if args.action=='stage':stage()
    else:deploy(args.candidate,args.backup)


if __name__=='__main__':main()
