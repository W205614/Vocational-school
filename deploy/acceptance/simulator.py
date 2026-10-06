"""Isolated, persistent third-party simulator. Must be started explicitly; never used by production."""
from http.server import HTTPServer,BaseHTTPRequestHandler
from concurrent.futures import ThreadPoolExecutor
from threading import BoundedSemaphore,Event,Thread
from urllib.parse import urlparse
from datetime import datetime,timezone
import sqlite3,json,uuid,re,time,hmac
import requests
from prepare import LOCAL,local_secrets
SECRETS=local_secrets();DB=LOCAL/'simulator.sqlite';STOP=Event()
def connect():
 connection=sqlite3.connect(DB,timeout=5);connection.row_factory=sqlite3.Row;connection.execute('PRAGMA journal_mode=WAL');return connection
with connect() as db:
 db.executescript("""CREATE TABLE IF NOT EXISTS payment(id TEXT PRIMARY KEY,owner TEXT NOT NULL,amount INTEGER NOT NULL,request TEXT NOT NULL,status INTEGER NOT NULL DEFAULT 1,success_time TEXT);
 CREATE TABLE IF NOT EXISTS refund(id TEXT PRIMARY KEY,payment_id TEXT NOT NULL,amount INTEGER NOT NULL,request TEXT NOT NULL);
 CREATE TABLE IF NOT EXISTS event(id TEXT PRIMARY KEY,routing TEXT NOT NULL,payload TEXT NOT NULL,sent INTEGER NOT NULL DEFAULT 0);""")
def payment_result(row):
 return None if row is None else dict(status=row['status'],msg='Local simulator',bizOrderId=row['id'],payOrderNo=row['id'],payChannel='mockPay',successTime=row['success_time'])
def refund_result(row):
 return None if row is None else dict(status=3,msg='Local simulator',bizRefundOrderId=row['id'],bizPayOrderId=row['payment_id'],payOrderNo=row['payment_id'],refundOrderNo=row['id'],payChannel='mockPay',refundChannel='mockPay')
def enqueue(db,routing,key,payload):
 identity=str(uuid.uuid5(uuid.NAMESPACE_URL,'vocational-acceptance:'+key))
 envelope=dict(eventId=identity,businessKey=key,eventType=routing,schemaVersion=1,occurredAt=datetime.now(timezone.utc).isoformat(),payload=payload)
 db.execute('INSERT OR IGNORE INTO event(id,routing,payload) VALUES(?,?,?)',(identity,routing,json.dumps(envelope)))
def publish():
 while not STOP.wait(1):
  with connect() as db: rows=db.execute('SELECT * FROM event WHERE sent=0 LIMIT 20').fetchall()
  for row in rows:
   try:
    response=requests.post('http://127.0.0.1:23372/api/exchanges/%2F/pay.topic/publish',auth=('acceptance',SECRETS['ACCEPTANCE_MQ_PASSWORD']),json=dict(properties=dict(delivery_mode=2,content_type='application/json',message_id=row['id']),routing_key=row['routing'],payload=row['payload'],payload_encoding='string'),timeout=5)
    response.raise_for_status()
    if response.json().get('routed'):
     with connect() as db:db.execute('UPDATE event SET sent=1 WHERE id=?',(row['id'],))
   except requests.RequestException:pass
class Handler(BaseHTTPRequestHandler):
 def setup(self):
  self.request.settimeout(5)
  super().setup()
 def log_message(self,*args):pass
 def send(self,status,value,plain=False,envelope=False):
  body=(value if plain else json.dumps(dict(code=200,msg='Local simulator',data=value,requestId=uuid.uuid4().hex) if envelope else value)).encode()
  self.send_response(status);self.send_header('Content-Type',('text/plain' if plain else 'application/json')+';charset=utf-8');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def dispatch(self):
  path=urlparse(self.path).path;public=path.startswith('/api/v2/simulator/')
  if path=='/health':
   with connect() as db:db.execute('SELECT 1')
   return self.send(200,dict(status='UP',mode='SIMULATED'))
  if not public and not hmac.compare_digest(self.headers.get('X-Internal-Token',''),SECRETS['ACCEPTANCE_INTERNAL_TOKEN']):return self.send(403,dict(error='internal authentication required'))
  size=int(self.headers.get('Content-Length','0'))
  if size>65536:return self.send(413,dict(error='request too large'))
  body=json.loads(self.rfile.read(size)) if size else {}
  if not isinstance(body,dict):return self.send(400,dict(error='object required'))
  with connect() as db:
   db.execute('BEGIN IMMEDIATE')
   if path=='/pay-channels/list' and self.command=='GET':
    return self.send(200,[dict(id='1',name='本地模拟支付',channelCode='mockPay',channelPriority=1,channelIcon='',status=1)])
   if path=='/pay-orders' and self.command=='POST':
    if body.get('payChannelCode')!='mockPay' or int(body.get('amount',0))<=0:return self.send(400,dict(error='simulator channel and amount required'))
    identity=str(body['bizOrderNo']);request=json.dumps(body,sort_keys=True)
    row=db.execute('SELECT * FROM payment WHERE id=?',(identity,)).fetchone()
    if row and row['request']!=request:return self.send(409,dict(error='payment identity used by a different request'))
    db.execute('INSERT OR IGNORE INTO payment(id,owner,amount,request) VALUES(?,?,?,?)',(identity,str(body['bizUserId']),int(body['amount']),request))
    db.commit();return self.send(200,'/simulate-payment/'+identity,plain=True)
   match=re.fullmatch(r'/pay-orders/([0-9]+)/status',path)
   if match and self.command=='GET':return self.send(200,payment_result(db.execute('SELECT * FROM payment WHERE id=?',match.groups()).fetchone()))
   match=re.fullmatch(r'/api/v2/simulator/payments/([0-9]+)/confirm',path)
   if match and self.command=='POST':
    row=db.execute('SELECT * FROM payment WHERE id=?',match.groups()).fetchone()
    if not row or row['owner']!=self.headers.get('user-info'):return self.send(404,dict(code=404,msg='模拟支付记录不存在',data=None,requestId=uuid.uuid4().hex))
    db.execute('UPDATE payment SET status=3,success_time=COALESCE(success_time,?) WHERE id=?',(datetime.now().isoformat(timespec='seconds'),row['id']))
    updated=db.execute('SELECT * FROM payment WHERE id=?',match.groups()).fetchone();enqueue(db,'pay.success','sim-pay:'+row['id'],payment_result(updated));db.commit()
    return self.send(200,payment_result(updated),envelope=True)
   if path=='/refund-orders' and self.command=='POST':
    identity=str(body['bizRefundOrderNo']);payment=str(body['bizOrderNo']);amount=int(body['refundAmount']);request=json.dumps(body,sort_keys=True)
    row=db.execute('SELECT * FROM refund WHERE id=?',(identity,)).fetchone()
    if row and row['request']!=request:return self.send(409,dict(error='refund identity used by a different request'))
    if not row:
     paid=db.execute('SELECT * FROM payment WHERE id=? AND status=3',(payment,)).fetchone()
     prior=db.execute('SELECT COALESCE(SUM(amount),0) FROM refund WHERE payment_id=?',(payment,)).fetchone()[0]
     if not paid or amount<=0 or prior+amount>paid['amount']:return self.send(409,dict(error='refund exceeds paid amount'))
     db.execute('INSERT INTO refund VALUES(?,?,?,?)',(identity,payment,amount,request));row=db.execute('SELECT * FROM refund WHERE id=?',(identity,)).fetchone()
     enqueue(db,'refund.status.change','sim-refund:'+identity,refund_result(row));db.commit()
    return self.send(200,refund_result(row))
   match=re.fullmatch(r'/refund-orders/([0-9]+)/status',path)
   if match and self.command=='GET':return self.send(200,refund_result(db.execute('SELECT * FROM refund WHERE id=?',match.groups()).fetchone()))
  return self.send(404,dict(error='endpoint not found'))
 def do_GET(self):
  try:self.dispatch()
  except (KeyError,ValueError,json.JSONDecodeError):self.send(400,dict(error='invalid simulator request'))
  except Exception:self.send(503,dict(error='simulator unavailable'))
 do_POST=do_GET
class Server(HTTPServer):
 def __init__(self,*args):
  super().__init__(*args);self.pool=ThreadPoolExecutor(max_workers=8,thread_name_prefix='simulator');self.capacity=BoundedSemaphore(64)
 def process_request(self,request,address):
  if not self.capacity.acquire(False):
   request.sendall(b'HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n');self.shutdown_request(request);return
  def process():
   try:self.finish_request(request,address)
   finally:self.shutdown_request(request);self.capacity.release()
  self.pool.submit(process)
if __name__=='__main__':
 worker=Thread(target=publish,name='simulator-publisher',daemon=True);worker.start();server=Server(('127.0.0.1',23600),Handler)
 print('Explicit local simulator listening on 23600; persistence is isolated',flush=True)
 try:server.serve_forever()
 finally:STOP.set();server.server_close();server.pool.shutdown(wait=True);worker.join(timeout=6)
