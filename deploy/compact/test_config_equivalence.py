import tempfile
from pathlib import Path
import unittest
import yaml
from config_equivalence import equivalent

class ConfigurationPromotionTest(unittest.TestCase):
 def home(self,base,offset,password,pool=4):
  home=Path(base);(home/'.local/configs').mkdir(parents=True)
  compose={'name':'owned-'+str(offset),'services':{'app-gateway':{'image':'sha256:a','environment':{'APP_PORT':str(24310+offset)},'ports':['127.0.0.1:'+str(24310+offset)+':'+str(24310+offset)]}},'volumes':{}}
  (home/'compose.yaml').write_text(yaml.safe_dump(compose))
  config={'server':{'port':24001+offset},'spring':{'datasource':{'password':password,'hikari':{'maximum-pool-size':pool}}},'tj':{'feign':{'internal-ports':str(25001+offset)},'routes':{'auth':'http://app-identity:'+str(24001+offset)+'/_modules/auth'}}}
  (home/'.local/configs/identity.yml').write_text(yaml.safe_dump(config));return home
 def test_only_port_and_local_credentials_can_differ(self):
  with tempfile.TemporaryDirectory() as base:
   left=self.home(Path(base)/'left',3000,'private-a');right=self.home(Path(base)/'right',0,'private-b')
   self.assertTrue(equivalent(left,right))
   p=right/'.local/configs/identity.yml';value=yaml.safe_load(p.read_text());value['spring']['datasource']['hikari']['maximum-pool-size']=8;p.write_text(yaml.safe_dump(value))
   self.assertFalse(equivalent(left,right))
 def test_different_image_or_unknown_security_config_cannot_be_promoted(self):
  with tempfile.TemporaryDirectory() as base:
   left=self.home(Path(base)/'left',3000,'a');right=self.home(Path(base)/'right',0,'b')
   (right/'.local/configs/extra.security').write_text('different policy');self.assertFalse(equivalent(left,right))
   (right/'.local/configs/extra.security').unlink()
   path=right/'compose.yaml';config=yaml.safe_load(path.read_text());config['services']['app-gateway']['image']='sha256:different';path.write_text(yaml.safe_dump(config))
   self.assertFalse(equivalent(left,right))

if __name__=='__main__':unittest.main()
