from pathlib import Path
import copy
import tempfile
import unittest
import yaml
from client_contexts import contexts, synchronize


class ClientContextsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.local = Path(self.temporary.name)
        (self.local / 'configs').mkdir()
        self.original = {'spring': {'cloud': {'openfeign': {'client': {'config': {
            'message-service': {'url': 'http://app-support:25004/_modules/message', 'readTimeout': 5000},
            'existing': {'url': 'http://custom', 'readTimeout': 42}
        }}}}}, 'custom': {'preserve': True}}
        self.path = self.local / 'configs/learning.yml'
        self.path.write_text(yaml.safe_dump(self.original), encoding='utf8')
        self.compose = self.local / 'compose.yaml'
        self.compose.write_text('original compose bytes', encoding='utf8')

    def test_adds_only_missing_context_and_preserves_exact_backup(self):
        before = self.path.read_bytes()
        result = synchronize(self.local, ['learning'], {'newNotification': 'message-service', 'existing': 'message-service'})
        expected = copy.deepcopy(self.original)
        clients = expected['spring']['cloud']['openfeign']['client']['config']
        clients['newNotification'] = copy.deepcopy(clients['message-service'])
        self.assertEqual(expected, yaml.safe_load(self.path.read_bytes()))
        self.assertEqual(['newNotification'], result['addedContexts'])
        self.assertEqual(before, next((self.local / 'client-context-sync').glob('*/learning.yml')).read_bytes())
        self.assertEqual('original compose bytes', self.compose.read_text(encoding='utf8'))
        after = self.path.read_bytes()
        self.assertEqual([], synchronize(self.local, ['learning'], {'newNotification': 'message-service'})['updatedModules'])
        self.assertEqual(after, self.path.read_bytes())

    def test_missing_trusted_target_fails_before_any_module_write(self):
        broken = copy.deepcopy(self.original)
        broken['spring']['cloud']['openfeign']['client']['config'].pop('message-service')
        (self.local / 'configs/exam.yml').write_text(yaml.safe_dump(broken), encoding='utf8')
        before = self.path.read_bytes()
        with self.assertRaises(ValueError):
            synchronize(self.local, ['learning', 'exam'], {'newNotification': 'message-service'})
        self.assertEqual(before, self.path.read_bytes())
        self.assertFalse((self.local / 'client-context-sync').exists())

    def test_discovers_contexts_and_rejects_conflicting_ownership(self):
        source = self.local / 'Client.java'
        source.write_text('@FeignClient(value="message-service", contextId="newNotification")', encoding='utf8')
        self.assertEqual({'newNotification': 'message-service'}, contexts([source]))
        source.write_text('@FeignClient("exam-service", contextId="newNotification")\n@FeignClient(name="message-service", contextId="newNotification")', encoding='utf8')
        with self.assertRaises(ValueError):
            contexts([source])


if __name__ == '__main__':
    unittest.main()
