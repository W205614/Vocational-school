"""Binary Redis DUMP snapshots via the container's internal port; no public credential logging."""
import base64,json,subprocess,sys
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
if sys.argv[1]=='capture':
 records=[]
 for db in range(16):
  cmd('SELECT',db);cursor=0
  while True:
   cursor,keys=cmd('SCAN',cursor,'COUNT',1000)
   for key in keys:
    value=cmd('DUMP',key);ttl=cmd('PTTL',key)
    if value is not None and ttl!=-2:records.append({'db':db,'key':encode(key),'value':encode(value),'expiresAt':int(time.time()*1000)+ttl if ttl>=0 else None})
   if int(cursor)==0:break
 print(json.dumps({'format':1,'records':records}))
else:
 data=json.load(sys.stdin)
 for db in range(16):
  cmd('SELECT',db)
  if cmd('DBSIZE'):raise RuntimeError('Refusing restore into nonempty Redis')
 for record in data['records']:
  ttl=max(1,record['expiresAt']-int(time.time()*1000)) if record['expiresAt'] else 0
  if record['expiresAt'] and record['expiresAt']<int(time.time()*1000):continue
  cmd('SELECT',record['db']);cmd('RESTORE',base64.b64decode(record['key']),ttl,base64.b64decode(record['value']))
 print('Redis binary snapshot restored')
"""
def transfer(runtime,path,restore=False):
 # Only the deployment's verified loopback mapping is used.
 container=subprocess.check_output(runtime.COMPOSE+['ps','-q','redis']).decode().strip()
 if not container:raise RuntimeError('Redis container missing')
 mapping=subprocess.check_output(['docker','port',container,'6379/tcp']).decode().strip()
 if not mapping.startswith('127.0.0.1:') or '\n' in mapping:raise RuntimeError('Redis must have one loopback-only mapping')
 command=[sys.executable,'-c',SCRIPT,'restore' if restore else 'capture',mapping.rsplit(':',1)[1]]
 result=subprocess.run(command,input=path.read_bytes() if restore else None,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if result.returncode:raise RuntimeError('Redis transfer failed; snapshot was not published')
 if not restore:path.write_bytes(result.stdout)
