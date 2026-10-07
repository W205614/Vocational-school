"""Prove process recovery and full-group restart retain business data and media bytes."""
from pathlib import Path
import json,subprocess,time,hashlib
from runtime import configure
BASE=Path(__file__).resolve().parent;LOCAL=BASE/'.local';p=configure()
def run(args,log):
 with (LOCAL/log).open('wb') as f:
  subprocess.run(args,check=True,stdout=f,stderr=subprocess.STDOUT,timeout=480)
def state():
 tables={'tj_user':['user','user_detail'],'tj_trade':['order','order_detail','refund_apply','payment_fact','payment_conflict'],'tj_pay':['pay_order','refund_order'],'tj_learning':['learning_lesson','learning_record','course_note','learning_entitlement'],'tj_exam':['exam_attempt']}
 values={}
 for db,names in tables.items():
  for name in names:values[db+'.'+name]=int(p.mysql('SELECT COUNT(*) FROM `'+name+'`',db))
 return {'counts':values,'media':{str(f.relative_to(LOCAL/'objects')):hashlib.sha256(f.read_bytes()).hexdigest() for f in (LOCAL/'objects').rglob('*') if f.is_file()}}
def main():
 before=state();begin=time.monotonic()
 target=subprocess.check_output(p.COMPOSE+['ps','-q','app-learning']).decode().strip()
 if not target:raise RuntimeError('Learning container identity missing')
 run(['docker','kill','--signal','KILL',target],'learning-kill.log')
 run(p.COMPOSE+['up','-d','--wait','--wait-timeout','300','app-learning'],'learning-recovery.log')
 crash_seconds=round(time.monotonic()-begin,2)
 if state()!=before:raise AssertionError('Crash recovery changed business counts or media')
 begin=time.monotonic();run(p.COMPOSE+['restart'],'group-restart.log')
 run(p.COMPOSE+['up','-d','--wait','--wait-timeout','420'],'group-recovery.log')
 if state()!=before:raise AssertionError('Group restart changed business counts or media')
 report={'status':'PASSED','learningCrashRecoverySeconds':crash_seconds,'groupRestartRecoverySeconds':round(time.monotonic()-begin,2),'verifiedTables':len(before['counts']),'verifiedMediaFiles':len(before['media']),'state':before}
 (LOCAL/'recovery.json').write_text(json.dumps(report,indent=2),encoding='utf8')
 print('Killed learning process recovered; full group restarted; business counts and media hashes match',flush=True)
if __name__=='__main__':main()
