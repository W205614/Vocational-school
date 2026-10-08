import copy
import tempfile
from pathlib import Path
import subprocess
import unittest
from evidence import bind,utcnow,verify_publication
from performance_acceptance import compare,validate_raw,baseline_complete
import test_perf_protocol

class BoundPerformanceTest(unittest.TestCase):
 def report(self):
  value=test_perf_protocol.PerformanceContractTest().report();value['status']='PASSED'
  manifest={'schemaVersion':1,'releaseRunId':'current','sourceCommit':'measured','imageDigests':dict(mysql='m',redis='r',rabbitmq='q',elasticsearch='s'),'configFingerprint':'config','baseSnapshotFingerprint':'base','createdAt':utcnow()}
  value.update(manifest=manifest,workloadFingerprint='w',fixtureFingerprint='f',workload={'seed':20261007})
  return bind(value,manifest,manifest['createdAt'])
 def test_missing_old_batch_or_image_mismatch_is_rejected(self):
  value=self.report();self.assertFalse(validate_raw(value,value['manifest']))
  self.assertTrue(validate_raw(value,{**value['manifest'],'releaseRunId':'new'}))
  changed=copy.deepcopy(value);changed['evidence']['imageDigests']={**changed['evidence']['imageDigests'],'mysql':'different'};self.assertTrue(validate_raw(changed,value['manifest']))
  changed=copy.deepcopy(value);changed['runs'][3]['repeat']=2;self.assertFalse(baseline_complete(changed))
  changed=copy.deepcopy(value);changed['runs'][1]['contaminated']=True;self.assertFalse(baseline_complete(changed))
 def test_failed_latency_and_mismatched_environment_cannot_be_handwritten_passes(self):
  standalone=self.report();compact=copy.deepcopy(standalone)
  for run in compact['runs']:run['javaRssMedianBytes']=700
  self.assertEqual('PASSED',compare(standalone,compact)['status'])
  compact['runs'][-1]['latencyMs']['async/notes:ok']['p95']=3001
  self.assertEqual('FAILED',compare(standalone,compact)['status'])
  compact=copy.deepcopy(standalone);compact['evidence']['sourceCommit']='another-build'
  self.assertEqual('FAILED',compare(standalone,compact)['status'])
 def test_readme_commit_preserves_measured_source_but_code_change_is_rejected(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory)
   def git(*args):return subprocess.check_output(['git','-C',directory,*args],stderr=subprocess.DEVNULL,text=True).strip()
   git('init');git('config','user.email','test@example.invalid');git('config','user.name','Evidence test')
   (root/'service.java').write_text('first');git('add','.');git('commit','-m','code');measured=git('rev-parse','HEAD')
   (root/'README.md').write_text('measured '+measured);git('add','.');git('commit','-m','docs')
   self.assertTrue(verify_publication(root,measured))
   (root/'service.java').write_text('second');git('add','.');git('commit','-m','different code')
   self.assertFalse(verify_publication(root,measured))

if __name__=='__main__':unittest.main()
