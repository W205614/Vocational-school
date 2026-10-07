"""Validate persisted audit actors, requests and asynchronous terminal linkage after browser workflow."""
import json
from setup import mysql,LOCAL
accounts=json.loads((LOCAL/'accounts.json').read_text(encoding='utf8'));checks=[]
for alias,role,path in [('exam','admin','/api/v2/admin/exam-papers'),('exam','teacher','/api/v2/teacher/exam-attempts/%/grades'),('trade','admin','/refund-apply/approval')]:
 actor=accounts[role]['id'];count=mysql("SELECT COUNT(*) FROM tj_"+alias+".admin_audit WHERE actor_id="+actor+" AND object_path LIKE '"+path+"' AND result='SUCCEEDED' AND request_id<>'' AND operation_id IS NOT NULL")
 if int(count)==0:raise RuntimeError('Required asynchronous audit missing: '+alias+'/'+role)
 checks.append(alias+'/'+role)
 mismatches=mysql("SELECT COUNT(*) FROM tj_"+alias+".admin_audit a JOIN tj_"+alias+".reliability_operation o USING(operation_id) WHERE o.status IN('SUCCEEDED','FAILED') AND a.result<>o.status")
 if int(mismatches):raise RuntimeError('Audit terminal result disagrees with operation')
(LOCAL/'audit-smoke.json').write_text(json.dumps({'status':'PASSED','checks':checks},indent=2),encoding='utf8');print('Persisted audit terminal outcomes and actor attribution passed')
