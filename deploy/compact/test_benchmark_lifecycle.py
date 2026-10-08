from pathlib import Path
import tempfile
import unittest
from benchmark_lifecycle import validate_cohort

class CohortOwnershipTest(unittest.TestCase):
 def test_foreign_project_and_main_deployment_cannot_be_retired(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);home=root/'deploy/compact/.local/optimization/pre-compact'
   labels={'com.docker.compose.project':'tianji-opt-pre-compact','com.docker.compose.project.config_files':str(home/'compose.yaml')}
   container={'Config':{'Labels':labels}}
   self.assertEqual(home.resolve(),validate_cohort(home,'tianji-opt-pre-compact',[container],root))
   with self.assertRaises(RuntimeError):validate_cohort(home,'tianji-compact',[],root)
   labels['com.docker.compose.project']='another-project'
   with self.assertRaises(RuntimeError):validate_cohort(home,'tianji-opt-pre-compact',[container],root)
 def test_sibling_repository_and_other_compose_file_are_rejected(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);home=root/'deploy/compact/.local/optimization/pre-compact'
   with self.assertRaises(RuntimeError):validate_cohort(root.parent/'another-repository','tianji-opt-pre-compact',[],root)
   labels={'com.docker.compose.project':'tianji-opt-pre-compact','com.docker.compose.project.config_files':str(home.parent/'other/compose.yaml')}
   with self.assertRaises(RuntimeError):validate_cohort(home,'tianji-opt-pre-compact',[{'Config':{'Labels':labels}}],root)

if __name__=='__main__':unittest.main()
