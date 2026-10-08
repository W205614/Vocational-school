import subprocess
import tempfile
from pathlib import Path
import unittest
from build_provenance import clean_commit,packaged,require_package

class PackageProvenanceTest(unittest.TestCase):
 def test_profile_or_dirty_production_cannot_be_relabelled_as_a_fresh_build(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory)
   def git(*args):return subprocess.check_output(['git','-C',directory,*args],stderr=subprocess.DEVNULL,text=True).strip()
   git('init');git('config','user.email','test@example.invalid');git('config','user.name','Package test')
   (root/'app/src/main').mkdir(parents=True);code=root/'app/src/main/service.java';code.write_text('original')
   git('add','.');git('commit','-m','source');commit=clean_commit(root)
   local=root/'.local';local.mkdir();jar=local/'fixture.jar';jar.write_bytes(b'packaged fixture')
   packaged(root,local,commit,'compact',{'app':jar});self.assertEqual(commit,require_package(local,commit,'compact')['sourceCommit'])
   with self.assertRaises(RuntimeError):require_package(local,commit,'standalone')
   (root/'README.md').write_text('pending documentation');self.assertEqual(commit,clean_commit(root))
   code.write_text('uncommitted change')
   with self.assertRaises(RuntimeError):clean_commit(root)
   with self.assertRaises(RuntimeError):packaged(root,local,commit,'compact',{'app':jar})

if __name__=='__main__':unittest.main()
