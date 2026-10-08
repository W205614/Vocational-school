import contextlib
import io
import json
from pathlib import Path
import runpy
import sys
import tempfile
import types
import unittest
from unittest.mock import patch
from evidence import bind, utcnow


class ReleaseScopeTest(unittest.TestCase):
    def run_gate(self, scope, mutate=None, live_mismatch=False):
        with tempfile.TemporaryDirectory() as directory:
            local=Path(directory)
            expected=dict(schemaVersion=1,releaseRunId='current',sourceCommit='current-source',
                          imageDigests={'app':'sha256:current'},configFingerprint='config',
                          baseSnapshotFingerprint='snapshot',snapshotPath='snapshot.sql',createdAt=utcnow())
            (local/'release-run.json').write_text(json.dumps(expected))
            reports=local/'evidence/current';reports.mkdir(parents=True)
            for name in ('security-smoke','browser-result','backend-result','audit-smoke',
                         'redis-recovery-proof','recovery-acceptance','search-rollback-acceptance','history-preservation'):
                (reports/(name+'.json')).write_text(json.dumps(bind({'status':'PASSED'},expected)))
            if mutate:mutate(reports,expected)
            captured=[]
            setup=types.ModuleType('setup');setup.LOCAL=local;setup.ROOT=local;setup.COMPOSE=[]
            live={**expected,'configFingerprint':'changed'} if live_mismatch else expected
            with patch.dict(sys.modules,{'setup':setup}),patch.object(sys,'argv',['release_gate.py','--scope',scope]),\
                 patch('evidence.manifest',return_value=live),patch('evidence.verify_publication',return_value=True),\
                 patch('evidence.save_report',side_effect=lambda home,name,value:captured.append(value)),\
                 patch('subprocess.check_output',return_value='current-source\n'),contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit) as terminal:
                    runpy.run_path(str(Path(__file__).with_name('release_gate.py')),run_name='__main__')
            return terminal.exception.code,captured[0]

    def test_functional_delivery_defers_metrics_while_default_full_requires_them(self):
        code,report=self.run_gate('functional')
        self.assertEqual(0,code);self.assertEqual('PASSED',report['status'])
        self.assertEqual('functional',report['acceptanceScope']);self.assertEqual('DEFERRED',report['performanceValidation'])
        self.assertEqual(3,len(report['deferredChecks']));self.assertNotIn('formal-performance',report['checks'])
        code,report=self.run_gate('full')
        self.assertEqual(1,code);self.assertEqual('FAILED',report['status'])
        self.assertFalse(report['checks']['formal-performance']);self.assertEqual([],report['deferredChecks'])

    def test_functional_scope_still_rejects_failed_missing_old_and_mismatched_proofs(self):
        for name in ('security-smoke','browser-result','backend-result','audit-smoke','redis-recovery-proof',
                     'recovery-acceptance','search-rollback-acceptance','history-preservation'):
            for damage in ('missing','failed','batch','source','image','snapshot'):
                def mutate(folder,expected):
                    path=folder/(name+'.json')
                    if damage=='missing':path.unlink();return
                    proof=json.loads(path.read_text())
                    if damage=='failed':proof['status']='FAILED'
                    else:proof['evidence'][{'batch':'releaseRunId','source':'sourceCommit','image':'imageDigests','snapshot':'baseSnapshotFingerprint'}[damage]]='wrong'
                    path.write_text(json.dumps(proof))
                with self.subTest(name=name,damage=damage):
                    code,report=self.run_gate('functional',mutate)
                    self.assertEqual(1,code);self.assertFalse(report['checks'][name])
        code,report=self.run_gate('functional',live_mismatch=True)
        self.assertEqual(1,code);self.assertFalse(report['checks']['live-build-config-snapshot'])


if __name__=='__main__':unittest.main()
