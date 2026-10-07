"""Closed-loop full gateway workload. Reports admission/business/system errors separately.
Formal protocol: --formal uses 10/50/100/200 x 1800s x 3. Default is a 60s smoke.
Generated credentials and cookies stay in memory/ignored files; reports contain no tokens.
"""
import sys
if '--protocol' in sys.argv:
 from mixed_load import main
 raise SystemExit(main())
import argparse,concurrent.futures,collections,json,math,random,threading,time,uuid,requests,subprocess,datetime
from setup import LOCAL,PORTS,COMPOSE,secrets_config,GROUPS,mysql
parser=argparse.ArgumentParser();parser.add_argument('--formal',action='store_true');parser.add_argument('--users',type=int,default=50);parser.add_argument('--seconds',type=int,default=60);parser.add_argument('--repeat',type=int,default=1);parser.add_argument('--base-url');args=parser.parse_args()
if not 1<=args.users<=200 or args.seconds<1 or args.repeat<1:raise ValueError('Invalid workload')
base=args.base_url or 'http://127.0.0.1:'+str(PORTS['gateway']);accounts=json.loads((LOCAL/'load-accounts.json').read_text(encoding='utf8'));fixture=json.loads((LOCAL/'browser-fixture.json').read_text(encoding='utf8'));fixture['notesCourse']=mysql('SELECT course_id FROM tj_learning.learning_lesson WHERE user_id='+accounts[0]['id']+' ORDER BY id LIMIT 1').strip();token=secrets_config()[0]['ACCEPTANCE_INTERNAL_TOKEN'];lock=threading.Lock();stop=threading.Event()
# Exact millisecond histograms avoid unbounded per-request retention.
def percentile(hist,p):
 target=math.ceil(sum(hist.values())*p);n=0
 for ms,count in sorted(hist.items()):
  n+=count
  if n>=target:return ms
 return None
class Stats:
 def __init__(self):self.times=collections.defaultdict(collections.Counter);self.counts=collections.Counter()
 def add(self,kind,elapsed,status):
  with lock:self.times[kind][int(elapsed*1000)]+=1;self.times[kind+':'+status][int(elapsed*1000)]+=1;self.counts[kind+':'+status]+=1
 def result(self):return {'counts':dict(self.counts),'latencyMs':{k:{'count':sum(h.values()),'p50':percentile(h,.5),'p95':percentile(h,.95),'p99':percentile(h,.99)} for k,h in self.times.items()}}
def classify(r):return 'ok' if r.ok else 'admission' if r.status_code==429 else 'business' if 400<=r.status_code<500 else 'system'
def session(a):
 s=requests.Session();r=s.post(base+'/api/v2/auth/accounts/login',json=dict(type=1,username=a['username'],password=a['password']),timeout=15)
 if not r.ok:raise RuntimeError('Load login failed HTTP '+str(r.status_code))
 s.headers['Authorization']='Bearer '+r.json()['data'];return s

def worker(index,s,end,stats,warm=False):
 next_refresh=time.monotonic()+180;cycle=0
 paths=['/api/v2/services/user/users/me','/api/v2/services/learning/lessons/page','/api/v2/notes','/api/v2/services/course/course/'+fixture['course']]
 while time.monotonic()<end and not stop.is_set():
  start=time.monotonic()
  try:
   if start>=next_refresh:
    r=s.get(base+'/api/v2/auth/accounts/refresh?audience=student',timeout=10)
    if not r.ok:raise RuntimeError('Refresh unavailable')
    s.headers['Authorization']='Bearer '+r.json()['data'];next_refresh=time.monotonic()+180
   if cycle%10==9 and not warm:
    r=s.post(base+'/api/v2/notes',json={'courseId':fixture['notesCourse'],'content':'load '+str(index)+' '+uuid.uuid4().hex},headers={'Idempotency-Key':str(uuid.uuid4())},timeout=10);stats.add('writeAcceptance',time.monotonic()-start,classify(r))
    if r.ok:
     op=r.json()['data'];deadline=time.monotonic()+30
     while op['status']=='PENDING' and time.monotonic()<deadline:
      time.sleep(.2);response=s.get(base+'/api/v2/operations/learning/'+op['operationId'],timeout=10)
      if not response.ok:stats.add('operationPoll',time.monotonic()-start,classify(response));break
      op=response.json()['data']
     stats.add('asyncCompletion',time.monotonic()-start,'ok' if op['status']=='SUCCEEDED' else 'business' if op['status']=='FAILED' and op.get('errorCode')=='BUSINESS_FAILED' else 'system')
   else:
    r=s.get(base+paths[cycle%len(paths)],timeout=10);stats.add('query',time.monotonic()-start,classify(r))
  except Exception:stats.add('transport',time.monotonic()-start,'system')
  cycle+=1;time.sleep(.25)

def resources():
 ids=subprocess.check_output(COMPOSE+['ps','-q']).decode().splitlines()
 raw=subprocess.check_output(['docker','stats','--no-stream','--format','{{json .}}',*ids]).decode();sample={'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'containers':[json.loads(line) for line in raw.splitlines()],'metrics':{},'alerts':[]}
 for group,aliases in GROUPS.items():
  for alias in aliases:
   try:
    r=requests.get('http://127.0.0.1:'+str(PORTS[group])+'/_modules/'+alias+'/actuator/prometheus',headers={'X-Internal-Token':token},timeout=4)
    if not r.ok:sample['alerts'].append(alias+' metrics HTTP '+str(r.status_code))
    if r.ok:sample['metrics'][alias]=[line for line in r.text.splitlines() if not line.startswith('#') and any(x in line for x in ['hikaricp_connections_pending','executor_queued_tasks','reliability_operation_queue','reliability_outbox_queue','tj_events_pending','tj_events_oldest_seconds','tj_operations_pending'])]
   except Exception:sample['alerts'].append(alias+' metrics unavailable')
 redis=subprocess.check_output(COMPOSE+['exec','-T','redis','redis-cli','INFO','memory']).decode();values=dict(l.split(':',1) for l in redis.splitlines() if ':' in l and not l.startswith('#'));used=int(values.get('used_memory',0));limit=int(values.get('maxmemory',0));sample['redis']={'usedBytes':used,'maxBytes':limit}
 if limit and used/limit>=.8:sample['alerts'].append('Redis memory >=80%')
 return sample
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ');output=LOCAL/'performance'/stamp;output.mkdir(parents=True,exist_ok=False)
results=[]
print('Starting '+('formal 12-run protocol (at least six hours)' if args.formal else str(args.users)+'-user '+str(args.seconds)+'-second smoke')+'; logins and warm-up precede measurement',flush=True)
try:
 for users in ([10,50,100,200] if args.formal else [args.users]):
  for repeat in range(3 if args.formal else args.repeat):
   # One request/1.1 seconds stays below the trusted-peer login limit.
   sessions=[]
   for a in accounts[:users]:sessions.append(session(a));time.sleep(1.1)
   for s in sessions:
    r=s.get(base+'/api/v2/auth/accounts/refresh?audience=student',timeout=10);r.raise_for_status();s.headers['Authorization']='Bearer '+r.json()['data']
   with concurrent.futures.ThreadPoolExecutor(max_workers=users) as pool:
    warm=Stats();end=time.monotonic()+10;list(pool.map(lambda pair:worker(*pair,end,warm,True),enumerate(sessions)))
    if warm.counts.get('query:ok',0)==0 or any(k.endswith(':system') or k.endswith(':business') for k in warm.counts):raise RuntimeError('Warm-up correctness failed; workload rejected')
    stats=Stats();duration=1800 if args.formal else args.seconds;started=time.monotonic();end=started+duration;futures=[pool.submit(worker,i,s,end,stats) for i,s in enumerate(sessions)];samples=[]
    while any(not f.done() for f in futures):
     samples.append(resources());time.sleep(min(10,max(.1,end-time.monotonic())))
    for f in futures:f.result()
   result={'users':users,'repeat':repeat+1,'requestedSeconds':duration,'elapsedSeconds':time.monotonic()-started,'workload':'distinct students; 90% queries, 10% asynchronous owned notes; 250ms think time','baseUrl':base,'formal':args.formal,**stats.result(),'samples':samples}
   result['targets']={'queryP95Within500ms':(result['latencyMs'].get('query:ok',{}).get('p95') or 999999)<=500,'asyncP95Within3000ms':(result['latencyMs'].get('asyncCompletion:ok',{}).get('p95') or 999999)<=3000,'systemErrors':sum(v for k,v in result['counts'].items() if k.endswith(':system'))}
   results.append(result);(output/'results.json').write_text(json.dumps(results,indent=2),encoding='utf8');print('Measured '+str(users)+' users: '+json.dumps(result['targets']),flush=True)
except KeyboardInterrupt:stop.set();raise
finally:
 (output/'protocol.json').write_text(json.dumps({'formalRequested':args.formal,'completedRuns':len(results),'expectedRuns':12 if args.formal else args.repeat,'sourceImages':json.loads((LOCAL/'images.json').read_text(encoding='utf8'))},indent=2),encoding='utf8')
print('Performance evidence: '+str(output))
raise SystemExit(0 if results and all(r['targets']['queryP95Within500ms'] and r['targets']['asyncP95Within3000ms'] and not any(v for k,v in r['counts'].items() if not k.endswith(':ok')) for r in results) else 1)
