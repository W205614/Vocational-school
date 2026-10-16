import subprocess
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
from build_provenance import clean_commit,packaged,require_package,retain_previous_image,new_web_context

class PackageProvenanceTest(unittest.TestCase):
 def test_new_web_build_cannot_ship_assets_from_a_previous_build(self):
  with tempfile.TemporaryDirectory() as directory:
   local=Path(directory);old=local/'images/student/dist/assets/previous.js'
   old.parent.mkdir(parents=True);old.write_text('previous build')
   first=new_web_context(local,'student');(first/'dist').mkdir();(first/'dist/current.js').write_text('first build')
   second=new_web_context(local,'student')
   self.assertNotEqual(first,second)
   self.assertEqual([],list(second.iterdir()))
   self.assertTrue(second.is_relative_to(local/'images/student'))
   self.assertEqual('previous build',old.read_text())
   self.assertEqual('first build',(first/'dist/current.js').read_text())
  with self.assertRaises(ValueError):new_web_context(Path('unused'),'../other-project')
 def test_mutable_image_replacement_retains_previous_id_and_refuses_other_projects(self):
  old='sha256:'+'a'*64
  with patch('build_provenance.subprocess.check_output',return_value=old+'\n'),patch('build_provenance.subprocess.run') as run:
   self.assertEqual('tianji-rollback/education:before-'+'a'*16,retain_previous_image('education'))
   self.assertEqual(['docker','tag',old,'tianji-rollback/education:before-'+'a'*16],run.call_args.args[0])
  with patch('build_provenance.subprocess.check_output',return_value=''),patch('build_provenance.subprocess.run') as run:
   self.assertIsNone(retain_previous_image('student'));run.assert_not_called()
  with self.assertRaises(ValueError):retain_previous_image('other-project')
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
