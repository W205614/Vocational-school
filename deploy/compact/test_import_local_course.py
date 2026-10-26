import copy
import hashlib
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import import_local_course as module


class FakeApi:
    """Stateful API contract double; real service registration is tested separately."""
    def __init__(self, runtime, inventory):
        self.runtime = runtime
        self.inventory = inventory
        self.course = None
        self.tree = []
        self.media = {}
        self.create_calls = 0
        self.import_calls = 0
        self.publish_calls = 0
        self.index_calls = 0
        self.media_links = []

    def login(self):
        pass

    def course_rows(self, name=None):
        return [self.course] if self.course else []

    def operation(self, operation):
        return operation['result']

    def pages(self, endpoint, params=None):
        return [{'id': '3'}]

    def request(self, method, path, body=None, params=None, key=None, internal=False):
        if path == '/services/course/categorys/list':
            return [{'id': '1', 'level': 1, 'status': 1, 'name': 'Java', 'children': [{'id': '2', 'level': 2, 'status': 1, 'name': '实战', 'children': [{'id': '3', 'level': 3, 'status': 1, 'name': '后端'}]}]}]
        if path.endswith('/baseInfo/save'):
            self.create_calls += 1
            self.course = {'id': '9001', 'name': body['name'], 'status': 1, 'introduce': body['introduce']}
            return {'id': '9001'}
        if '/baseInfo/' in path:
            return dict(self.course)
        if '/catas/save/' in path:
            self.tree = copy.deepcopy(body)
            counter = 1000
            for chapter in self.tree:
                chapter['id'] = str(counter)
                counter += 1
                for section in chapter['sections']:
                    section['id'] = str(counter)
                    counter += 1
            return None
        if '/catas/' in path:
            return copy.deepcopy(self.tree)
        if path == '/internal/v2/media/local-import':
            self.import_calls += 1
            self.assert_key = key
            relative = body['relativePath']
            entry = next(row for row in self.inventory['files'] if row['relativePath'] == relative)
            media_id = str(5000 + len(self.media))
            file_id = hashlib.sha256(relative.encode()).hexdigest() + '.mp4'
            source = Path(self.inventory['sourceRoot']) / relative
            (self.runtime.objects / file_id).write_bytes(source.read_bytes())
            self.media[media_id] = {'id': media_id, 'size': entry['size'], 'duration': 9.25, 'fileId': file_id}
            return {'status': 'SUCCEEDED', 'operationId': 'operation-' + media_id, 'result': dict(self.media[media_id])}
        if path.endswith('/signature/preview'):
            return {'fileId': self.media[str(params['mediaId'])]['fileId']}
        if '/media/save/' in path:
            self.media_links = copy.deepcopy(body)
            by_id = {row['cataId']: row for row in body}
            for chapter in self.tree:
                for section in chapter['sections']:
                    link = by_id[section['id']]
                    section['mediaId'] = link['mediaId']
                    section['mediaDuration'] = link['mediaDuration']
            return None
        if path.endswith('/teachers/save') or '/checkBeforeUpShelf/' in path:
            return None
        if path.endswith('/upShelf'):
            self.publish_calls += 1
            self.course['status'] = 2
            return None
        if path == '/admin/search/courses/up':
            self.index_calls += 1
            return None
        if path == '/public/courses/9001':
            return {'id': '9001', 'name': self.course['name'], 'free': True, 'price': 0, 'sectionNum': len(self.inventory['files'])}
        if path == '/public/courses':
            return {'list': [{'id': '9001'}]}
        raise AssertionError('Unexpected API contract call: ' + path)


class ImportLocalCourseTest(unittest.TestCase):
    def fixture(self, base, sample=False):
        source = base / 'source'
        day = source / 'day01'
        day.mkdir(parents=True)
        (day / 'Day01-02.第二节.mp4').write_bytes(b'synthetic-source-two')
        (day / 'Day01-01.第一节.mp4').write_bytes(b'synthetic-source-one')
        inventory = module.source_inventory(source, expected_files=2, expected_chapters=1)
        inventory['sample'] = sample
        home = base / 'home'
        objects = home / '.local' / 'objects'
        objects.mkdir(parents=True)
        runtime = SimpleNamespace(home=home, local=home / '.local', objects=objects, project='tianji-import-unit', binding={'home': str(home), 'project': 'tianji-import-unit'})
        options = {'name': 'Java微服务实战-隔离验证', 'categoryId': '3', 'teacherId': '3', 'coverUrl': '/brand-logo.png', 'purchaseEnd': '2099-12-31T23:59:59'}
        return runtime, inventory, options

    def test_inventory_is_ordered_read_only_and_detects_content_or_timestamp_changes(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime, inventory, _ = self.fixture(Path(folder))
            self.assertEqual([1, 2], [row['index'] for row in inventory['files']])
            self.assertTrue(inventory['files'][0]['filename'].startswith('Day01-01'))
            module.verify_source(inventory)
            path = Path(inventory['sourceRoot']) / inventory['files'][0]['relativePath']
            path.write_bytes(b'changed')
            with self.assertRaises(module.ImportFailure):
                module.verify_source(inventory)

    def test_inventory_rejects_missing_default_count_or_unsafe_selection(self):
        with tempfile.TemporaryDirectory() as folder:
            _, inventory, _ = self.fixture(Path(folder))
            root = Path(inventory['sourceRoot'])
            with self.assertRaises(module.ImportFailure):
                module.source_inventory(root)
            for selection in ('../outside.mp4', 'day01/../outside.mp4', 'day01\\outside.mp4'):
                with self.subTest(selection=selection), self.assertRaises(module.ImportFailure):
                    module.source_inventory(root, [selection], expected_files=2, expected_chapters=1)

    def test_catalogue_titles_remain_unique_and_obey_java_utf16_length(self):
        first = module.short_title('Day01-01.' + '长标题' * 30 + '.mp4', 1)
        second = module.short_title('Day01-02.' + '长标题' * 30 + '.mp4', 2)
        emoji = module.short_title('Day01-03.' + '😀' * 30 + '.mp4', 3)
        self.assertNotEqual(first, second)
        self.assertLessEqual(len(first.encode('utf-16-le')) // 2, 30)
        self.assertLessEqual(len(emoji.encode('utf-16-le')) // 2, 30)

    def test_default_plan_does_not_authenticate_or_call_any_business_api(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime, inventory, options = self.fixture(Path(folder))
            importer = module.Importer(runtime, runtime.local / 'import' / 'manifest.json', inventory, options)
            importer.api = FakeApi(runtime, inventory)
            proof = importer.plan()
            self.assertFalse(proof['businessMutation'])
            self.assertEqual(0, importer.api.create_calls + importer.api.import_calls)

    def test_resume_reuses_owned_course_and_media_and_publishes_ceiled_real_duration(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime, inventory, options = self.fixture(Path(folder))
            path = runtime.local / 'import' / 'manifest.json'
            importer = module.Importer(runtime, path, inventory, options)
            api = FakeApi(runtime, inventory)
            importer.api = api
            proof = importer.apply()
            self.assertEqual('PASSED', proof['status'])
            self.assertEqual([10, 10], [row['mediaDuration'] for row in api.media_links])
            self.assertEqual((1, 2, 1), (api.create_calls, api.import_calls, api.publish_calls))
            second = module.Importer(runtime, path, inventory, options)
            second.api = api
            self.assertEqual('PASSED', second.apply()['status'])
            self.assertEqual((1, 2, 1), (api.create_calls, api.import_calls, api.publish_calls))
            self.assertNotIn('signature', path.read_text(encoding='utf8').lower())

    def test_unrelated_same_name_and_cross_deployment_manifest_reuse_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime, inventory, options = self.fixture(Path(folder))
            path = runtime.local / 'import' / 'manifest.json'
            importer = module.Importer(runtime, path, inventory, options)
            api = FakeApi(runtime, inventory)
            api.course = {'id': '42', 'name': options['name'], 'status': 2, 'introduce': 'unrelated'}
            importer.api = api
            with self.assertRaises(module.ImportFailure):
                importer.apply()
            changed = copy.copy(runtime)
            changed.binding = {**runtime.binding, 'project': 'different'}
            with self.assertRaises(module.ImportFailure):
                module.Importer(changed, path, inventory, options)

    def test_partial_sample_cannot_target_daily_project(self):
        with tempfile.TemporaryDirectory() as folder:
            runtime, inventory, options = self.fixture(Path(folder), sample=True)
            runtime.project = 'tianji-compact'
            with self.assertRaises(module.ImportFailure):
                module.Importer(runtime, runtime.local / 'import' / 'manifest.json', inventory, options)

    def test_quota_retry_keeps_same_key_and_does_not_log_or_return_credentials(self):
        runtime = SimpleNamespace(gateway='http://127.0.0.1:1', media='http://127.0.0.1:2/_modules/media')
        api = module.Api(runtime)
        first = SimpleNamespace(status_code=429, headers={'Retry-After': '1'}, ok=False)
        second = SimpleNamespace(status_code=202, headers={}, ok=True, json=lambda: {'status': 'PENDING', 'operationId': 'a'})
        with patch.object(api.http, 'request', side_effect=[first, second]) as request, patch.object(module.time, 'sleep'):
            api.actor, api.internal_token = '1', 'private-service-token'
            result = api.request('POST', '/internal/v2/media/local-import', body={'relativePath': 'day01/a.mp4'}, key='stable-key', internal=True)
            self.assertEqual('a', result['operationId'])
            self.assertEqual(['stable-key', 'stable-key'], [call.kwargs['headers']['Idempotency-Key'] for call in request.call_args_list])


if __name__ == '__main__':
    unittest.main()
