import tempfile
from pathlib import Path
import unittest
import yaml
from recovery_configuration import recorded_configuration,translated


class RecordedRecoveryTest(unittest.TestCase):
    def test_old_transport_and_tuning_are_preserved_with_fresh_ports(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);configs=base/'saved';configs.mkdir()
            old={'server':{'port':27001},'spring':{'datasource':{'hikari':{'maximum-pool-size':4},'password':'unchanged:27001'}},
                 'tj':{'feign':{'url':'http://app-education:27003/_modules/learning'},'reliability':{'operation-interval-ms':750}}}
            (configs/'identity.yml').write_text(yaml.safe_dump(old),encoding='utf8')
            compose={'name':'original','services':{'app-gateway':{'environment':{'APP_PORT':'27310'},'ports':['127.0.0.1:27310:27310'],'volumes':['./.local/configs:/run/compact/configs:ro']},
                      'rabbitmq':{'volumes':['compact_rabbit:/var/lib/rabbitmq']}},'volumes':{'compact_rabbit':{}}}
            restored=recorded_configuration(compose,configs,base/'clone','tianji-recovery-test',1000,{'app-gateway':'sha256:gateway','rabbitmq':'sha256:broker'},'recorded-node')
            actual=yaml.safe_load((base/'clone/.local/configs/identity.yml').read_text())
            self.assertEqual(25001,actual['server']['port']);self.assertEqual('http://app-education:25003/_modules/learning',actual['tj']['feign']['url'])
            self.assertEqual(old['spring'],actual['spring']);self.assertEqual(old['tj']['reliability'],actual['tj']['reliability'])
            self.assertNotIn('internal-port',actual['tj'])
            self.assertEqual('25310',restored['services']['app-gateway']['environment']['APP_PORT'])
            self.assertEqual('tianji-recovery-test',restored['name']);self.assertEqual('recorded-node',restored['services']['rabbitmq']['hostname'])

    def test_nested_connector_ports_translate_but_shared_volumes_and_mounts_are_rejected(self):
        reservation={'sysctls':{'net.ipv4.ip_local_reserved_ports':'24004,25004,25005,25006,25007'}}
        self.assertEqual('38004,39004,39005,39006,39007',translated(reservation,0,14000)['sysctls']['net.ipv4.ip_local_reserved_ports'])
        value={'tj':{'compact':{'internal-port':28001},'feign':{'internal-ports':'28001,28002,28003,28004','url':'http://127.0.0.1:28001/_modules/user'}}}
        self.assertEqual('26001,26002,26003,26004',translated(value,3000,1000)['tj']['feign']['internal-ports'])
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'saved').mkdir()
            compose={'services':{'app-gateway':{'environment':{'APP_PORT':'27310'}}},'volumes':{'data':{'external':True}}}
            with self.assertRaises(RuntimeError):recorded_configuration(compose,root/'saved',root/'clone','tianji-recovery-test',1000,{'app-gateway':'image'},'node')
            compose['services']['app-gateway']['volumes']=['./.local/../../other-project:/data']
            with self.assertRaises(RuntimeError):recorded_configuration(compose,root/'saved',root/'clone','tianji-recovery-test',1000,{'app-gateway':'image'},'node')
            compose['volumes']={'data':{}};compose['services']['app-gateway']['volumes']=['E:/original-data:/data']
            with self.assertRaises(RuntimeError):recorded_configuration(compose,root/'saved',root/'clone','tianji-recovery-test',1000,{'app-gateway':'image'},'node')


if __name__=='__main__':unittest.main()
