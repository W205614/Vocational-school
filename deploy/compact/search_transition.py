"""Switch or roll back search from CURRENT MySQL facts, preserving all old volumes.

Run after core regression. Both directions require seven frozen application images
and their build-source manifest. Failure leaves application writes paused and records
an independent report; the operator can rerun with the recorded rollback images.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import uuid
import requests
import yaml
from setup import BASE, LOCAL, ROOT, PROJECT, COMPOSE, PORTS, OFFSET, GROUPS, mysql, run, configure_acceptance
from evidence import utcnow


def checked_owner():
    allowed = BASE == ROOT/'deploy/compact' or BASE.is_relative_to(ROOT/'deploy/compact/.local/optimization')
    if not allowed or PROJECT != 'tianji-compact' and not PROJECT.startswith('tianji-opt-'):
        raise RuntimeError('Search transition is restricted to this project and its audit clones')
    ids = subprocess.check_output(COMPOSE+['ps','-aq'],text=True).splitlines()
    if not ids:raise RuntimeError('An initialized deployment is required')
    for info in json.loads(subprocess.check_output(['docker','inspect',*ids],text=True)):
        labels=info['Config']['Labels']
        if labels.get('com.docker.compose.project') != PROJECT:raise RuntimeError('Container ownership mismatch')
        files=labels.get('com.docker.compose.project.config_files','').split(',')
        if not files or any(Path(path).resolve()!=BASE/'compose.yaml' for path in files):raise RuntimeError('Compose ownership mismatch')


def rebuild_facts():
    # These three tables are disposable search projections. Purchase and study facts
    # remain intact, and versions increase so delayed events cannot overwrite rebuilds.
    mysql("""START TRANSACTION;
    DELETE FROM tj_search.course_sale_detail;
    INSERT INTO tj_search.course_sale_detail(order_detail_id,course_id,active)
      SELECT d.id,d.course_id,IF(COALESCE(d.refund_status,0)=5 OR o.status=7,0,1)
      FROM tj_trade.order_detail d JOIN tj_trade.`order` o ON o.id=d.order_id
      WHERE d.status IN(2,4,5,6,7);
    INSERT INTO tj_search.course_sales_projection(course_id,sold,version,processed_version)
      SELECT c.id,COUNT(d.order_detail_id),1,0 FROM tj_course.course c
      LEFT JOIN tj_search.course_sale_detail d ON d.course_id=c.id AND d.active=1
      GROUP BY c.id
      ON DUPLICATE KEY UPDATE sold=VALUES(sold),version=course_sales_projection.version+1;
    UPDATE tj_search.course_sales_projection SET processed_version=0,status='PENDING',attempts=0,
      last_error=NULL,lease_token=NULL,lease_until=NULL,next_attempt_at=NOW(3);
    INSERT INTO tj_search.course_metadata_projection(course_id,version,processed_version)
      SELECT id,1,0 FROM tj_course.course
      ON DUPLICATE KEY UPDATE version=course_metadata_projection.version+1;
    UPDATE tj_search.course_metadata_projection SET processed_version=0,status='PENDING',attempts=0,
      last_error=NULL,lease_token=NULL,lease_until=NULL,next_attempt_at=NOW(3);
    COMMIT;""")


def verify_projection():
    url='http://127.0.0.1:'+str(24920+OFFSET)
    deadline=time.monotonic()+240
    pending="SELECT SUM(n) FROM (SELECT COUNT(*) n FROM tj_search.course_metadata_projection WHERE version>processed_version UNION ALL SELECT COUNT(*) FROM tj_search.course_sales_projection WHERE version>processed_version) p"
    while time.monotonic()<deadline:
        if mysql(pending)=='0':break
        time.sleep(1)
    else:raise RuntimeError('Search projections failed to converge; pending/dead rows retained')
    requests.post(url+'/course/_refresh',timeout=10).raise_for_status()
    rows=[line.split('\t') for line in mysql("SELECT c.id,c.name,COALESCE(s.sold,0) FROM tj_course.course c LEFT JOIN tj_search.course_sales_projection s ON s.course_id=c.id WHERE c.deleted=0 AND c.status IN(2,4) ORDER BY c.id").splitlines()]
    count=requests.get(url+'/course/_count',json={'query':{'term':{'available':True}}},timeout=10)
    count.raise_for_status()
    if count.json()['count']!=len(rows):raise RuntimeError('Search availability differs from current catalog facts')
    for start in range(0,len(rows),100):
        batch=rows[start:start+100]
        response=requests.post(url+'/course/_mget',json={'ids':[row[0] for row in batch]},timeout=10)
        response.raise_for_status()
        for row,doc in zip(batch,response.json()['docs']):
            source=doc.get('_source',{})
            if not doc.get('found') or not source.get('available') or source.get('name')!=row[1] or int(source.get('sold',0))!=int(row[2]):raise RuntimeError('Search metadata or absolute sales projection mismatch')
    return {'availableCourses':len(rows),'pendingProjections':0,'namesAndSalesVerified':True}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--major',type=int,choices=[7,9],required=True)
    parser.add_argument('--images',type=Path,required=True)
    parser.add_argument('--source-manifest',type=Path,required=True)
    args=parser.parse_args();checked_owner()
    images=json.loads(args.images.read_text(encoding='utf8'))
    source=json.loads(args.source_manifest.read_text(encoding='utf8'))
    subprocess.run(['git','-C',str(ROOT),'cat-file','-e',source['sourceCommit']+'^{commit}'],check=True)
    compose=yaml.safe_load((BASE/'compose.yaml').read_text(encoding='utf8'))
    expected={('web-' if alias in ('student','admin') else 'app-')+alias:details['imageId'] for alias,details in images.items()}
    if expected!=source['imageDigests'] or set(expected)!={'app-'+name for name in [*GROUPS,'gateway']}|{'web-student','web-admin'}:raise RuntimeError('All seven frozen application image/source bindings are required')
    for details in images.values():
        actual=subprocess.check_output(['docker','image','inspect',details['imageId'],'--format','{{.Id}}'],text=True).strip()
        if actual!=details['imageId']:raise RuntimeError('Recorded application image is unavailable')
    version='9.5.5' if args.major==9 else '7.17.29'
    tag='docker.elastic.co/elasticsearch/elasticsearch:'+version
    available=subprocess.run(['docker','image','inspect',tag,'--format','{{.Id}}'],capture_output=True,text=True)
    if available.returncode:
        run(['docker','pull',tag],'search-pull-'+str(args.major))
        digest=subprocess.check_output(['docker','image','inspect',tag,'--format','{{.Id}}'],text=True).strip()
    else:
        digest=available.stdout.strip()
    old_compose=(BASE/'compose.yaml').read_bytes()
    running=subprocess.check_output(COMPOSE+['ps','--status','running','--services'],text=True).splitlines()
    apps=[name for name in running if name.startswith(('app-','web-'))]
    at=utcnow();folder=LOCAL/'search-transitions'/uuid.uuid4().hex;folder.mkdir(parents=True)
    (folder/'previous-compose.yaml').write_bytes(old_compose)
    report={'status':'FAILED','startedAt':at,'targetMajor':args.major,'sourceCommit':source['sourceCommit'],
            'preservedOldSearchVolume':True,'restoredBusinessBackup':False}
    try:
        if apps:run(COMPOSE+['stop','-t','60',*apps],'search-pause-apps')
        sys.path.insert(0,str(ROOT/'deploy/final'));from final_backup import capture,verify
        backup=capture(configure_acceptance(),BASE,quiesced=True);verify(backup);report['backup']=str(backup)
        (folder/'previous-images.json').write_bytes((LOCAL/'images.json').read_bytes())
        (folder/'previous-build-source.json').write_bytes((LOCAL/'build-source.json').read_bytes())
        financial=mysql('SELECT id,status,deleted,pay_time,finish_time FROM tj_trade.`order` ORDER BY id')+'\n'+mysql('SELECT id,order_id,user_id,course_id,status,refund_status,valid_duration,course_expire_time FROM tj_trade.order_detail ORDER BY id')
        report['financialFingerprintBefore']=hashlib.sha256(financial.encode()).hexdigest()
        run(COMPOSE+['exec','-T','rabbitmq','rabbitmqctl','stop_app'],'search-pause-consumers')
        volume='compact_search_v'+str(args.major)
        owned=subprocess.check_output(['docker','volume','ls','-q','--filter','name=^'+PROJECT+'_'+volume+'$'],text=True).strip()
        if owned:volume+='_'+uuid.uuid4().hex[:12]
        compose['volumes'][volume]={}
        compose['services']['elasticsearch'].update(image=digest,volumes=[volume+':/usr/share/elasticsearch/data'])
        for name,image in expected.items():compose['services'][name]['image']=image
        (BASE/'compose.yaml').write_text(yaml.safe_dump(compose,sort_keys=False),encoding='utf8')
        run(COMPOSE+['up','-d','--wait','--no-deps','elasticsearch'],'search-new-volume')
        engine=requests.get('http://127.0.0.1:'+str(24920+OFFSET),timeout=10)
        engine.raise_for_status()
        if engine.json()['version']['number']!=version:raise RuntimeError('Search engine version mismatch')
        run([sys.executable,str(ROOT/'deploy/compact/prepare_search.py')],'search-fresh-mapping')
        rebuild_facts()
        (LOCAL/'images.json').write_text(json.dumps(images,indent=2),encoding='utf8')
        (LOCAL/'build-source.json').write_text(json.dumps(source,indent=2),encoding='utf8')
        infrastructure=json.loads((LOCAL/'infrastructure-images.json').read_text(encoding='utf8'))
        infrastructure['elasticsearch']=digest
        (LOCAL/'infrastructure-images.json').write_text(json.dumps(infrastructure,indent=2),encoding='utf8')
        # Resume consumers once the engine, schema and authoritative rebuild tasks
        # are in place. Public writers remain stopped until all projections converge.
        run(COMPOSE+['exec','-T','rabbitmq','rabbitmqctl','start_app'],'search-resume-consumers')
        run(COMPOSE+['up','-d','--wait','--no-deps',*['app-'+group for group in GROUPS]],'search-rebuild-apps')
        report.update(verify_projection())
        after=mysql('SELECT id,status,deleted,pay_time,finish_time FROM tj_trade.`order` ORDER BY id')+'\n'+mysql('SELECT id,order_id,user_id,course_id,status,refund_status,valid_duration,course_expire_time FROM tj_trade.order_detail ORDER BY id')
        report['financialFingerprintAfter']=hashlib.sha256(after.encode()).hexdigest()
        if report['financialFingerprintBefore']!=report['financialFingerprintAfter']:raise RuntimeError('Financial facts changed during quiesced search transition')
        run(COMPOSE+['up','-d','--wait','--no-deps','app-gateway','web-student','web-admin'],'search-resume-writers')
        report.update(status='PASSED',imageDigest=digest,newVolume=PROJECT+'_'+volume)
    except Exception as error:
        report['failureType']=type(error).__name__
        # A failed transition never silently starts writers against mixed versions.
        subprocess.run(COMPOSE+['stop','-t','60',*[name for name in compose['services'] if name.startswith(('app-','web-'))]],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        raise
    finally:
        report['finishedAt']=utcnow()
        (folder/'result.json').write_text(json.dumps(report,indent=2),encoding='utf8')
        (LOCAL/'search-transition.json').write_text(json.dumps(report,indent=2),encoding='utf8')
        print('Search transition '+report['status']+'; preserved source volumes and independent report: '+str(folder))

if __name__=='__main__':main()
