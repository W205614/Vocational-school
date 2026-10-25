"""Verify binary/type/expiry recovery in two disposable, separately named Redis projects."""

from runtime_compose import active_compose, compose_command
import datetime,json,subprocess,sys
from pathlib import Path
from types import SimpleNamespace
import yaml
from setup import LOCAL,SOURCE
sys.path.insert(0,str(SOURCE.parent/'final'))
from redis_snapshot import transfer
from recovery_upgrade import compare_redis_snapshots

stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S')
directory=LOCAL/('redis-proof-'+stamp);directory.mkdir(parents=True,exist_ok=False)
runtimes=[]
def command(runtime,*args,input=None):
 result=subprocess.run(runtime.COMPOSE+['exec','-T','redis','redis-cli','--raw',*args],input=input,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if result.returncode:raise RuntimeError('Redis proof command failed')
 return result.stdout
try:
 for role,port in [('source',26479),('target',26480)]:
  home=directory/role;home.mkdir()
  project='tianji-redis-proof-'+stamp+'-'+role
  compose={'name':project,'services':{'redis':{'image':'redis:7.4-alpine','ports':['127.0.0.1:'+str(port)+':6379'],'mem_limit':'64m','healthcheck':{'test':['CMD','redis-cli','ping'],'interval':'1s','timeout':'2s','retries':30}}}}
  file=active_compose(home);file.parent.mkdir(parents=True,exist_ok=True);file.write_text(yaml.safe_dump(compose),encoding='utf8')
  runtime=SimpleNamespace(COMPOSE=['docker','compose','--project-directory',str(home),'-p',project,'-f',str(file)])
  runtimes.append(runtime)
  subprocess.run(runtime.COMPOSE+['up','-d','--wait'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 source,target=runtimes;binary=bytes(range(256))*2
 command(source,'-x','SET','proof:binary',input=binary)
 command(source,'SET','proof:ttl','recovery-value','EX','600')
 # More than 512 fields forces hash-table encoding, whose DUMP field order may change.
 fields=[('first','5'),('second','6')]+[(f'field{i:04d}',f'value{i:04d}') for i in range(700)]
 command(source,'HSET','proof:hash',*[item for pair in fields for item in pair])
 command(source,'SADD','proof:set','one','two','three')
 command(source,'LPUSH','proof:list','three','two','one')
 command(source,'ZADD','proof:rank','1','memberA','2','memberB')
 snapshot=directory/'redis.json';transfer(source,snapshot);transfer(target,snapshot,restore=True)
 assert command(target,'GET','proof:binary')==binary+b'\n'
 assert command(target,'GET','proof:ttl')==b'recovery-value\n'
 assert command(target,'HGET','proof:hash','first')==b'5\n'
 assert command(target,'HGET','proof:hash','second')==b'6\n'
 assert command(target,'LRANGE','proof:list','0','-1')==b'one\ntwo\nthree\n'
 assert command(target,'ZSCORE','proof:rank','memberA')==b'1\n'
 assert command(target,'ZSCORE','proof:rank','memberB')==b'2\n'
 assert set(command(target,'SMEMBERS','proof:set').splitlines())=={b'one',b'two',b'three'}
 # Reinsert the exact logical hash in reverse order, preserving the original snapshot.
 command(target,'DEL','proof:hash')
 command(target,'HSET','proof:hash',*[item for pair in reversed(fields) for item in pair])
 actual=directory/'redis-restored.json';transfer(target,actual)
 semantic=compare_redis_snapshots(json.loads(snapshot.read_text()),json.loads(actual.read_text()),int(datetime.datetime.now(datetime.timezone.utc).timestamp()*1000))
 assert semantic['redisComparison']=='semantic'
 assert semantic['redisReserializedRecords']>=1
 expires=[record['expiresAt'] for record in json.loads(snapshot.read_text())['records'] if record['expiresAt']][0]
 remaining=int(command(target,'PTTL','proof:ttl'))
 assert remaining>0 and abs(expires-int(datetime.datetime.now(datetime.timezone.utc).timestamp()*1000)-remaining)<2000
 # Refusal to overwrite a nonempty target is part of the recovery contract.
 try:transfer(target,snapshot,restore=True)
 except RuntimeError:pass
 else:raise AssertionError('Nonempty Redis restore was allowed')
 proof={'status':'PASSED','checks':12,'records':6,'semanticComparison':semantic,'binaryAndTypesVerified':True,'absoluteExpiryVerified':True,'nonemptyTargetRejected':True}
 (LOCAL/'redis-recovery-proof.json').write_text(json.dumps(proof,indent=2),encoding='utf8')
 print('Redis binary, hash reserialization, set, list, sorted set, expiry and nonempty-target protection PASSED')
finally:
 for runtime in runtimes:subprocess.run(runtime.COMPOSE+['stop'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
