"""Binary Redis DUMP snapshots via the container's internal port; no public credential logging."""
import base64,json,subprocess,sys
SEMANTIC_SCRIPT = r"""
def semantic_fingerprint(kind, values):
 import hashlib
 encode=lambda value:base64.b64encode(value).decode('ascii')
 if kind=='string':
  if len(values)!=1:raise RuntimeError('Invalid Redis string snapshot')
  canonical=[encode(values[0])]
 elif kind=='list':canonical=[encode(value) for value in values]
 elif kind=='set':canonical=[encode(value) for value in sorted(values)]
 elif kind=='hash':
  if len(values)%2:raise RuntimeError('Invalid Redis hash snapshot')
  canonical=[[encode(field),encode(value)] for field,value in sorted(zip(values[::2],values[1::2]))]
 elif kind=='zset':
  if len(values)%2:raise RuntimeError('Invalid Redis sorted-set snapshot')
  def score(value):
   number=float(value)
   return (0.0 if number==0 else number).hex()
  canonical=[[encode(member),score(value)] for member,value in sorted(zip(values[::2],values[1::2]))]
 else:raise RuntimeError('Unsupported Redis snapshot value type')
 data=json.dumps({'type':kind,'values':canonical},sort_keys=True,separators=(',',':')).encode('ascii')
 return hashlib.sha256(data).hexdigest()
"""

SCRIPT=r"""
import socket,json,base64,sys,time
s=socket.create_connection(('127.0.0.1',int(sys.argv[2])),5);f=s.makefile('rb')

def read():
 line=f.readline();kind=line[:1];value=line[1:-2]
 if kind==b'+':return value
 if kind==b'-':raise RuntimeError(value.decode())
 if kind==b':':return int(value)
 if kind==b'$':
  n=int(value)
  if n<0:return None
  out=f.read(n);f.read(2);return out
 if kind==b'*':return [read() for _ in range(int(value))]
 raise RuntimeError('Invalid Redis response')

def cmd(*args):
 args=[a if isinstance(a,bytes) else str(a).encode() for a in args];s.sendall(b'*'+str(len(args)).encode()+b'\r\n'+b''.join(b'$'+str(len(a)).encode()+b'\r\n'+a+b'\r\n' for a in args));return read()
encode=lambda v:base64.b64encode(v).decode()
""" + SEMANTIC_SCRIPT + r'''
# Capture DUMP, absolute expiry and logical contents atomically for each key.
CAPTURE_KEY="""
local kind=redis.call('TYPE',KEYS[1]).ok
if kind=='none' then return {} end
local values={}
if kind=='string' then values={redis.call('GET',KEYS[1])}
elseif kind=='hash' then values=redis.call('HGETALL',KEYS[1])
elseif kind=='set' then values=redis.call('SMEMBERS',KEYS[1])
elseif kind=='list' then values=redis.call('LRANGE',KEYS[1],0,-1)
elseif kind=='zset' then values=redis.call('ZRANGE',KEYS[1],0,-1,'WITHSCORES')
else return redis.error_reply('Unsupported Redis snapshot value type') end
return {redis.call('DUMP',KEYS[1]),redis.call('PEXPIRETIME',KEYS[1]),kind,values}
"""
if sys.argv[1]=='capture':
 records={}
 for db in range(16):
  cmd('SELECT',db);cursor=0
  while True:
   cursor,keys=cmd('SCAN',cursor,'COUNT',1000)
   for key in keys:
    row=cmd('EVAL',CAPTURE_KEY,1,key)
    if not row:continue
    value,expiry,kind,values=row;kind=kind.decode('ascii')
    record={'db':db,'key':encode(key),'value':encode(value),'expiresAt':expiry if expiry>=0 else None,'type':kind,'semanticSha256':semantic_fingerprint(kind,values)}
    records[(db,record['key'])]=record
   if int(cursor)==0:break
 print(json.dumps({'format':2,'records':[records[key] for key in sorted(records)]}))
else:
 data=json.load(sys.stdin)
 if data.get('format') not in (1,2) or not isinstance(data.get('records'),list):raise RuntimeError('Unsupported Redis recovery snapshot')
 for db in range(16):
  cmd('SELECT',db)
  if cmd('DBSIZE'):raise RuntimeError('Refusing restore into nonempty Redis')
 for record in data['records']:
  expiry=record['expiresAt']
  if expiry is not None and expiry<=int(time.time()*1000):continue
  cmd('SELECT',record['db'])
  if expiry is None:cmd('RESTORE',base64.b64decode(record['key']),0,base64.b64decode(record['value']))
  else:cmd('RESTORE',base64.b64decode(record['key']),expiry,base64.b64decode(record['value']),'ABSTTL')
 print('Redis binary snapshot restored')
'''

def transfer(runtime,path,restore=False):
 # Only the deployment's verified loopback mapping is used.
 container=subprocess.check_output(runtime.COMPOSE+['ps','-q','redis'],timeout=30).decode().strip()
 if not container:raise RuntimeError('Redis container missing')
 mapping=subprocess.check_output(['docker','port',container,'6379/tcp'],timeout=30).decode().strip()
 if not mapping.startswith('127.0.0.1:') or '\n' in mapping:raise RuntimeError('Redis must have one loopback-only mapping')
 command=[sys.executable,'-c',SCRIPT,'restore' if restore else 'capture',mapping.rsplit(':',1)[1]]
 result=subprocess.run(command,input=path.read_bytes() if restore else None,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=300)
 if result.returncode:raise RuntimeError('Redis transfer failed; snapshot was not published')
 if not restore:path.write_bytes(result.stdout)
