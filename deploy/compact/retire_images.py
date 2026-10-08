"""Archive and remove only unreferenced images proven to belong to this deployment."""
import datetime
import hashlib
import gzip
import json
import re
from pathlib import Path
import subprocess
import tarfile
from setup import ROOT, LOCAL, PROJECT, COMPOSE
from evidence import manifest, validate


def output(arguments):
    return subprocess.check_output(arguments, text=True).strip()


def candidates(images, owned, referenced):
    prefixes=('tianji-compact/', 'tianji-opt/', 'tianji-final/', 'tianji-acceptance/')
    infrastructure=('mysql:', 'redis:', 'rabbitmq:', 'docker.elastic.co/elasticsearch/elasticsearch:')
    return {item['Id'] for item in images if item['Id'] not in referenced and
            all(tag.startswith(prefixes+infrastructure) for tag in item.get('RepoTags') or []) and
            (item['Id'] in owned or any(tag.startswith(prefixes) for tag in item.get('RepoTags') or []))}


def verify_descriptor_graph(archive, expected, configs, platforms):
    """Bind containerd image IDs to their archived local-platform config/layers."""
    if not platforms or set(platforms)!=set(expected):raise RuntimeError('Recorded image platforms are required for OCI archives')
    index=json.load(archive.extractfile('index.json'))
    descriptors=index.get('manifests',[])
    if {item['digest'] for item in descriptors}!=set(expected):raise RuntimeError('Archived image roots differ from the removal inventory')
    checked=set()
    def blob(item):
        digest=item['digest']
        if not re.fullmatch(r'sha256:[0-9a-f]{64}',digest):raise RuntimeError('Invalid archived descriptor digest')
        name='blobs/sha256/'+digest.split(':')[1];member=archive.getmember(name)
        if not member.isfile() or member.size!=item['size']:raise RuntimeError('Incomplete archived descriptor')
        if digest not in checked:
            with archive.extractfile(member) as source:actual='sha256:'+hashlib.file_digest(source,'sha256').hexdigest()
            if actual!=digest:raise RuntimeError('Archived descriptor content differs from its image digest')
            checked.add(digest)
        return member
    def walk(item,platform,required=True):
        member=blob(item)
        with archive.extractfile(member) as source:document=json.load(source)
        if 'manifests' in document:
            result=set();matched=False
            for child in document['manifests']:
                child_platform=child.get('platform',{})
                local=all(child_platform.get(key)==value for key,value in platform.items())
                nested=not child_platform and 'index' in child.get('mediaType','')
                if local or nested:
                    result.update(walk(child,platform));matched=True
                elif child_platform.get('architecture')=='unknown':
                    # Attestations are checked when present; they are not executable platforms.
                    try:archive.getmember('blobs/sha256/'+child['digest'].split(':')[1])
                    except KeyError:continue
                    walk(child,platform,False)
            if required and (not matched or not result):raise RuntimeError('Recorded executable image platform is missing')
            return result
        if 'config' not in document or 'layers' not in document:raise RuntimeError('Unknown archived image descriptor')
        config_member=blob(document['config'])
        for layer in document['layers']:blob(layer)
        if document.get('artifactType'):return set()
        with archive.extractfile(config_member) as source:configuration=json.load(source)
        if any(configuration.get(key)!=value for key,value in platform.items()):raise RuntimeError('Archived configuration platform differs from inventory')
        return {document['config']['digest']}
    reachable=set()
    for item in descriptors:reachable.update(walk(item,platforms[item['digest']]))
    if reachable!=configs:raise RuntimeError('Archive configs are not bound to the recorded image roots')


def verify_archive(path, expected, platforms=None):
    configs=set();layers={}
    with tarfile.open(path, 'r') as archive:
        entries=json.load(archive.extractfile('manifest.json'))
        for entry in entries:
            if not archive.getmember(entry['Config']).isfile():raise RuntimeError('Invalid image config in archive')
            with archive.extractfile(entry['Config']) as source:
                config=source.read();configs.add('sha256:'+hashlib.sha256(config).hexdigest())
            roots=json.loads(config).get('rootfs',{})
            if roots.get('type')!='layers' or len(roots.get('diff_ids',[]))!=len(entry['Layers']):
                raise RuntimeError('Image root filesystem provenance is unavailable')
            # diff_ids bind the uncompressed tar bytes, including shared layers.
            for layer,diff_id in zip(entry['Layers'],roots['diff_ids']):
                if not archive.getmember(layer).isfile():raise RuntimeError('Incomplete image archive')
                if layer not in layers:
                    with archive.extractfile(layer) as source:
                        compressed=source.read(2)==b'\x1f\x8b';source.seek(0)
                        if compressed:
                            with gzip.GzipFile(fileobj=source) as plain:layers[layer]='sha256:'+hashlib.file_digest(plain,'sha256').hexdigest()
                        else:layers[layer]='sha256:'+hashlib.file_digest(source,'sha256').hexdigest()
                if layers[layer]!=diff_id:raise RuntimeError('Archived layer content differs from its image root filesystem')
        if configs!=set(expected):
            if 'index.json' not in archive.getnames():raise RuntimeError('Archived images differ from the removal inventory')
            verify_descriptor_graph(archive,expected,configs,platforms)
    with Path(path).open('rb') as source:return hashlib.file_digest(source, 'sha256').hexdigest()


def main():
    if PROJECT!='tianji-compact':raise RuntimeError('Image retirement requires the final deployment')
    expected=json.loads((LOCAL/'release-run.json').read_text(encoding='utf8'))
    gate=json.loads((LOCAL/'evidence'/expected['releaseRunId']/'release-gate.json').read_text(encoding='utf8'))
    if not validate(gate, expected):raise RuntimeError('Final bound acceptance must pass before image retirement')
    live=manifest(LOCAL, COMPOSE, ROOT, expected['snapshotPath'])
    if any(live[key]!=expected[key] for key in ('sourceCommit','imageDigests','configFingerprint','baseSnapshotFingerprint')):
        raise RuntimeError('Final deployment changed after acceptance')
    retirement=json.loads((LOCAL/'retirement-result.json').read_text(encoding='utf8'))
    if retirement.get('status')!='PASSED':raise RuntimeError('Container retirement must complete first')
    owned=set()
    for folder in (LOCAL/'retirement').iterdir():
        path=folder/'containers-private.json'
        if path.exists():owned.update(item['Image'] for item in json.loads(path.read_text(encoding='utf8')))
    for name in ('build-source.json','images.json','standalone-images.json'):
        for path in LOCAL.rglob(name):
            value=json.loads(path.read_text(encoding='utf8'))
            if name=='build-source.json':owned.update(value.get('imageDigests',{}).values())
            else:owned.update(item['imageId'] for item in value.values() if isinstance(item,dict) and 'imageId' in item)
    def referenced():
        ids=output(['docker','ps','-aq']).splitlines()
        return {item['Image'] for item in json.loads(output(['docker','inspect',*ids]))} if ids else set()
    ids=output(['docker','image','ls','-aq','--no-trunc']).splitlines()
    inventory=json.loads(output(['docker','image','inspect',*sorted(set(ids))])) if ids else []
    selected=candidates(inventory, owned, referenced())
    at=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    folder=LOCAL/'retirement'/('images-'+at);folder.mkdir(parents=True)
    archive=folder/'rollback-images.tar'
    plan={'imageIds':sorted(selected),'images':[{key:item.get(key) for key in ('Id','RepoTags','Size')} for item in inventory if item['Id'] in selected],
          'preservedReferencedImages':sorted(referenced()),'archive':str(archive),'releaseRunId':expected['releaseRunId']}
    (folder/'plan.json').write_text(json.dumps(plan,indent=2),encoding='utf8')
    if selected:
        subprocess.run(['docker','image','save','--output',str(archive),*sorted(selected)],check=True)
        platforms={item['Id']:{'architecture':item['Architecture'],'os':item['Os']} for item in inventory if item['Id'] in selected}
        checksum=verify_archive(archive, selected, platforms)
        (folder/'archive-verification.json').write_text(json.dumps({'status':'PASSED','sha256':checksum,'images':sorted(selected),'platforms':platforms,'scope':'Recorded local executable platforms and archived attestations'},indent=2),encoding='utf8')
        for item in inventory:
            if item['Id'] not in selected:continue
            if item['Id'] in referenced():raise RuntimeError('Image gained a container reference; removal stopped')
            tags=item.get('RepoTags') or []
            subprocess.run(['docker','image','rm','--no-prune',*(tags or [item['Id']])],check=True,stdout=subprocess.DEVNULL)
        remaining=set(output(['docker','image','ls','-aq','--no-trunc']).splitlines())
        if selected & remaining:raise RuntimeError('Unreferenced project images remain')
    result={'status':'PASSED','removedImages':len(selected),'archive':str(archive) if selected else None,
            'rollbackPreservedInArchive':bool(selected),'otherProjectContainersChanged':False,'volumeDeletion':False,'at':at}
    (folder/'result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
    (LOCAL/'image-retirement-result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
    print(json.dumps(result))


if __name__=='__main__':main()
