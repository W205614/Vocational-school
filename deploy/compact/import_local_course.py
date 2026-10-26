"""Read-only source inventory and resumable local-course import through business APIs.

Defaults to plan; only --apply mutates the selected deployment. Never writes source files,
uses business SQL, broad cleanup, or publishes credentials/playback signatures.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import re
import sys
import time
from pathlib import Path, PurePosixPath
from urllib.parse import urlsplit
import requests
import yaml
from maintenance import atomic_json, maintenance_lock

OWNER = 'tianji-offline-course-import-v1'
DEFAULT_NAME = 'Java微服务实战'
SOURCE_LIMIT = 512 * 1024 * 1024


class ImportFailure(RuntimeError):
    pass


def checksum(path: Path) -> str:
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def fingerprint(value) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=True, separators=(',', ':')).encode()).hexdigest()


def natural(value):
    return [(0, int(piece)) if piece.isdigit() else (1, piece.lower()) for piece in re.split(r'(\d+)', value)]


def short_title(filename: str, index: int) -> str:
    stem = Path(filename).stem
    title = re.sub(r'^day\d+[-_. ]+\d+[.、 _-]*', '', stem, flags=re.I).strip() or stem
    # Course catalogue validates Java UTF-16 string length <=30.
    return (f'{index:02d} {title}').encode('utf-16-le')[:60].decode('utf-16-le', errors='ignore')


def source_inventory(root: Path, includes=(), limit=None, expected_files=161, expected_chapters=12):
    root = root.resolve(strict=True)
    if not root.is_dir():
        raise ImportFailure('Source root must be a directory')
    paths = []
    days = sorted([p for p in root.iterdir() if p.is_dir() and re.fullmatch(r'day\d{2}', p.name, re.I)], key=lambda p: natural(p.name))
    for day in days:
        if day.is_symlink() or (hasattr(day, 'is_junction') and day.is_junction()):
            raise ImportFailure('Source chapter links are forbidden')
        for path in sorted(day.iterdir(), key=lambda p: natural(p.name)):
            if path.suffix.lower() != '.mp4':
                continue
            if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root):
                raise ImportFailure('Source file escaped the fixed import root')
            paths.append(path)
    if expected_files is not None and len(paths) != expected_files:
        raise ImportFailure(f'Expected {expected_files} source videos; found {len(paths)}')
    if expected_chapters is not None and len(days) != expected_chapters:
        raise ImportFailure(f'Expected {expected_chapters} source chapters; found {len(days)}')
    if includes and limit:
        raise ImportFailure('Choose exact --include entries or --limit, not both')
    if includes:
        if len(set(includes)) != len(includes):
            raise ImportFailure('Duplicate explicit source selection')
        allowed = set()
        for entry in includes:
            relative = PurePosixPath(entry)
            if relative.is_absolute() or '..' in relative.parts or '\\' in entry or len(relative.parts) != 2:
                raise ImportFailure('Explicit selections must be dayXX/filename.mp4')
            allowed.add(entry)
        paths = [p for p in paths if p.relative_to(root).as_posix() in allowed]
        if len(paths) != len(allowed):
            raise ImportFailure('An explicit source selection was not found')
    elif limit:
        if not 1 <= limit <= 3:
            raise ImportFailure('Isolated --limit must be between 1 and 3')
        paths = paths[:limit]
    if not paths:
        raise ImportFailure('No source videos selected')
    if (includes or limit) and len(paths) > 3:
        raise ImportFailure('An isolated sample is limited to 3 source videos')
    records, order = [], {}
    for path in paths:
        before = path.stat()
        if before.st_size <= 0 or before.st_size > SOURCE_LIMIT:
            raise ImportFailure('Each offline video must contain 1 byte to 512 MiB')
        digest = checksum(path)
        after = path.stat()
        if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
            raise ImportFailure('Source changed while being inventoried')
        day = path.parent.name.lower()
        order[day] = order.get(day, 0) + 1
        records.append({'relativePath': path.relative_to(root).as_posix(), 'day': day, 'index': order[day],
                        'filename': path.name, 'sectionTitle': short_title(path.name, order[day]),
                        'size': before.st_size, 'mtimeNs': before.st_mtime_ns, 'sha256': digest})
    return {'sourceRoot': str(root), 'sample': bool(includes or limit), 'files': records,
            'sourceFingerprint': fingerprint(records)}


def verify_source(inventory, rehash=True):
    root = Path(inventory['sourceRoot']).resolve(strict=True)
    for entry in inventory['files']:
        relative = PurePosixPath(entry['relativePath'])
        if relative.is_absolute() or '..' in relative.parts or '\\' in entry['relativePath']:
            raise ImportFailure('Invalid manifest source path')
        path = root.joinpath(*relative.parts)
        if path.is_symlink() or not path.resolve(strict=True).is_relative_to(root):
            raise ImportFailure('Manifest source escaped the original root')
        stat = path.stat()
        if stat.st_size != entry['size'] or stat.st_mtime_ns != entry['mtimeNs']:
            raise ImportFailure('Source bytes or timestamps changed since inventory')
        if rehash and checksum(path) != entry['sha256']:
            raise ImportFailure('Source hash changed since inventory')


def mount_pair(value):
    if isinstance(value, dict):
        return value.get('source'), value.get('target')
    source, target = value.rsplit(':', 1)
    if target in {'ro', 'rw', 'z', 'Z'}:
        source, target = source.rsplit(':', 1)
    return source, target


class Runtime:
    def __init__(self, home):
        self.home = Path(home).resolve(strict=True)
        self.local = self.home / '.local'
        path = self.local / 'compose.runtime.yaml'
        if not path.is_file():
            path = self.home / 'compose.yaml'
        self.compose = yaml.safe_load(path.read_text(encoding='utf8'))
        self.project = self.compose.get('name')
        if not isinstance(self.project, str) or not self.project.startswith(('tianji-', 'vocational-')):
            raise ImportFailure('An explicitly owned Tianji deployment is required')
        services = self.compose['services']
        self.gateway = self.origin(services['app-gateway'])
        self.media = self.origin(services['app-support']) + '/_modules/media'
        self.objects = None
        for item in services['app-support'].get('volumes', []):
            source, target = mount_pair(item)
            if target == '/run/objects' and source and (Path(source).is_absolute() or source.startswith('.')):
                self.objects = (self.home / source).resolve()
        if self.objects is None:
            raise ImportFailure('Byte-level verification requires the existing local object bind mount')
        self.binding = {'home': str(self.home), 'project': self.project,
                        'mysqlVolumes': services['mysql'].get('volumes', []), 'objects': str(self.objects)}

    @staticmethod
    def origin(service):
        port = str(service['environment']['APP_PORT'])
        for mapping in service.get('ports', []):
            if not isinstance(mapping, str):
                continue
            pieces = mapping.split(':')
            if len(pieces) == 3 and pieces[0] == '127.0.0.1' and pieces[2].split('/')[0] == port:
                return 'http://127.0.0.1:' + pieces[1]
        raise ImportFailure('Only one verified IPv4 loopback application mapping is supported')


class Api:
    def __init__(self, runtime):
        self.runtime = runtime
        self.http = requests.Session()
        self.http.trust_env = False
        self.actor = None
        self.internal_token = None

    def request(self, method, path, body=None, params=None, key=None, internal=False):
        origin = self.runtime.media if internal else self.runtime.gateway + '/api/v2'
        parsed = urlsplit(origin)
        if parsed.scheme != 'http' or parsed.hostname != '127.0.0.1':
            raise ImportFailure('Import requests must stay on the verified local deployment')
        headers = {'Idempotency-Key': key} if key else {}
        if internal:
            headers.update({'X-Internal-Token': self.internal_token, 'user-info': self.actor, 'user-role': '1'})
        for attempt in range(4):
            try:
                response = self.http.request(method, origin + path, json=body, params=params, headers=headers, timeout=(5, 300 if internal else 90))
            except requests.RequestException:
                raise ImportFailure('Local API connection failed; rerun with the same manifest to resume') from None
            if response.status_code == 401 and self.actor and not internal and path != '/auth/accounts/refresh' and attempt == 0:
                token = self.request('GET', '/auth/accounts/refresh', params={'audience': 'admin'})
                self.http.headers['Authorization'] = 'Bearer ' + token
                continue
            if response.status_code == 429 and attempt < 3:
                try:
                    delay = min(60, max(1, int(response.headers.get('Retry-After', '30'))))
                except ValueError:
                    delay = 30
                time.sleep(delay)
                continue
            if not response.ok:
                raise ImportFailure(f'Local API rejected {method} {path} with HTTP {response.status_code}')
            try:
                result = response.json()
            except ValueError:
                raise ImportFailure('Local API did not return JSON') from None
            if isinstance(result, dict) and 'code' in result:
                if result['code'] != 200:
                    raise ImportFailure(f'Local API reported a business failure for {method} {path}')
                return result.get('data')
            if internal:
                return result
            raise ImportFailure('Missing versioned response envelope')
        raise ImportFailure('Local request quota remained exhausted')

    def login(self):
        accounts = json.loads((self.runtime.local / 'accounts.json').read_text(encoding='utf8'))
        account = accounts['admin']
        token = self.request('POST', '/auth/accounts/admin/login', {'type': 1, 'username': account['username'], 'password': account['password'], 'rememberMe': False})
        if not isinstance(token, str):
            raise ImportFailure('Administrator login did not return an access token')
        self.http.headers['Authorization'] = 'Bearer ' + token
        # A successful protected administrator endpoint confirms the account's current role.
        self.request('GET', '/admin/user/staffs/page', params={'pageNo': 1, 'pageSize': 1})
        profile = self.request('GET', '/services/user/users/me')
        self.actor = str(profile['id'])
        env = dict(line.split('=', 1) for line in (self.runtime.local / 'app.env').read_text(encoding='utf8').splitlines() if '=' in line)
        self.internal_token = env.get('TJ_INTERNAL_TOKEN', '')
        if not self.actor.isdigit() or len(self.internal_token) < 32:
            raise ImportFailure('Local service identity is incomplete')
        environment = self.request('GET', '/environment')
        if environment.get('mode') != 'SIMULATED':
            raise ImportFailure('Offline import is restricted to the explicit local simulator deployment')

    def pages(self, endpoint, params=None):
        result = []
        for page in range(1, 10001):
            body = self.request('GET', endpoint, params={**(params or {}), 'pageNo': page, 'pageSize': 100})
            rows = body.get('list') or []
            result.extend(rows)
            if len(result) >= int(body.get('total', 0)):
                return result
        raise ImportFailure('Pagination did not converge')

    def course_rows(self, name=None):
        rows = {}
        for status in (1, 2, 3, 4):
            for row in self.pages('/admin/course/courses/page', {'status': status, **({'keyword': name} if name else {})}):
                rows[str(row['id'])] = row
        return list(rows.values())

    def operation(self, operation):
        deadline = time.monotonic() + 300
        while operation.get('status') == 'PENDING' and time.monotonic() < deadline:
            time.sleep(1)
            operation = self.request('GET', '/operations/media/' + operation['operationId'])
        if operation.get('status') != 'SUCCEEDED' or not isinstance(operation.get('result'), dict):
            raise ImportFailure('Media registration is incomplete; retain the manifest and resume after repair')
        return operation['result']


def catalogue_payload(files):
    grouped = {}
    for entry in files:
        grouped.setdefault(entry['day'], []).append(entry)
    return [{'name': f'第{int(day[3:]):02d}章 · {day}', 'type': 1, 'index': index,
             'sections': [{'name': row['sectionTitle'], 'type': 2, 'index': row['index']} for row in grouped[day]]}
            for index, day in enumerate(sorted(grouped, key=natural), 1)]


def catalogue_mapping(files, tree):
    expected = catalogue_payload(files)
    if not isinstance(tree, list) or len(tree) != len(expected):
        raise ImportFailure('Owned course catalogue differs from the planned chapter count')
    mapping = {}
    grouped = {}
    for row in files:
        grouped.setdefault(row['day'], []).append(row)
    for wanted, actual, day in zip(expected, sorted(tree, key=lambda row: int(row['index'])), sorted(grouped, key=natural)):
        if actual.get('name') != wanted['name'] or int(actual.get('type', 0)) != 1 or int(actual['index']) != wanted['index']:
            raise ImportFailure('Owned course chapter identity changed')
        children = sorted(actual.get('sections') or [], key=lambda row: int(row['index']))
        if len(children) != len(grouped[day]):
            raise ImportFailure('Owned course catalogue differs from the planned section count')
        for planned, child in zip(grouped[day], children):
            if child.get('name') != planned['sectionTitle'] or int(child.get('type', 0)) != 2 or int(child['index']) != planned['index']:
                raise ImportFailure('Owned course section identity changed')
            mapping[planned['relativePath']] = child
    return mapping


class Importer:
    def __init__(self, runtime, manifest_path, inventory, options):
        self.runtime = runtime
        self.path = Path(manifest_path).resolve()
        if not self.path.is_relative_to(runtime.local.resolve()):
            raise ImportFailure('Import manifests must stay in this deployment ignored .local directory')
        if inventory['sample'] and runtime.project == 'tianji-compact':
            raise ImportFailure('Partial samples are allowed only in a separately named isolated deployment')
        if self.path.exists():
            self.document = json.loads(self.path.read_text(encoding='utf8'))
            if self.document.get('owner') != OWNER or self.document.get('inventory') != inventory or self.document.get('deployment') != runtime.binding or self.document.get('options') != options:
                raise ImportFailure('Manifest ownership, source, deployment or options changed; do not reuse its business IDs')
        else:
            self.document = {'owner': OWNER, 'format': 1, 'inventory': inventory, 'deployment': runtime.binding,
                             'options': options, 'status': 'PLANNED', 'state': {'media': {}}}
            self.save()
        self.api = Api(runtime)

    @property
    def state(self):
        return self.document['state']

    @property
    def files(self):
        return self.document['inventory']['files']

    @property
    def marker(self):
        return OWNER + ':' + self.document['inventory']['sourceFingerprint']

    def save(self):
        atomic_json(self.path, self.document)

    def plan(self):
        return {'status': self.document['status'], 'manifest': str(self.path), 'sample': self.document['inventory']['sample'],
                'files': len(self.files), 'chapters': len({row['day'] for row in self.files}),
                'sourceBytes': sum(row['size'] for row in self.files), 'largeFilesOver200MiB': sum(row['size'] > 200 * 1024 * 1024 for row in self.files),
                'sourceFingerprint': self.document['inventory']['sourceFingerprint'], 'businessMutation': False}

    def course_info(self, row):
        return self.api.request('GET', '/admin/course/courses/baseInfo/' + str(row['id']), params={'see': 'true' if int(row.get('status', 1)) in (2, 4) else 'false'})

    def owned_course(self):
        candidates = [row for row in self.api.course_rows(self.document['options']['name']) if row.get('name') == self.document['options']['name']]
        if len(candidates) > 1:
            raise ImportFailure('Multiple equal course names exist; refusing ambiguous adoption')
        if not candidates:
            if self.state.get('courseId'):
                raise ImportFailure('Previously recorded course is missing; refusing automatic replacement')
            return None
        row = candidates[0]
        info = self.course_info(row)
        if self.marker not in info.get('introduce', '') or (self.state.get('courseId') and str(row['id']) != self.state['courseId']):
            raise ImportFailure('An unrelated or changed course owns the requested name')
        self.state['courseId'] = str(row['id'])
        self.save()
        return row

    def resolve_category(self):
        categories = self.api.request('GET', '/services/course/categorys/list', params={'status': 1})
        leaves = []
        def visit(rows, names=()):
            for row in rows:
                path = names + (row.get('name', ''),)
                if int(row.get('status', 1)) != 1:
                    continue
                if int(row.get('level', len(path))) == 3 and not row.get('children'):
                    leaves.append((str(row['id']), path))
                else:
                    visit(row.get('children') or [], path)
        visit(categories)
        explicit = self.document['options']['categoryId']
        if explicit:
            if explicit not in {row[0] for row in leaves}:
                raise ImportFailure('Selected category is not an enabled third-level category')
            return explicit
        matching = [row for row in leaves if any('java' in part.lower() for part in row[1])]
        if not matching:
            raise ImportFailure('No enabled Java category was found; pass an explicit --category-id')
        return matching[0][0]

    def resolve_teacher(self):
        explicit = self.document['options']['teacherId']
        if not explicit:
            explicit = str(json.loads((self.runtime.local / 'accounts.json').read_text(encoding='utf8'))['teacher']['id'])
        teachers = self.api.pages('/admin/user/teachers/page', {'status': 1})
        if explicit not in {str(row['id']) for row in teachers}:
            raise ImportFailure('Selected teacher is not an active teacher in this deployment')
        return explicit

    def create_course(self):
        category = self.state.setdefault('categoryId', self.resolve_category())
        self.state.setdefault('teacherId', self.resolve_teacher())
        options = self.document['options']
        self.document['status'] = 'CREATING_COURSE'
        self.save()
        body = {'name': options['name'], 'thirdCateId': category, 'coverUrl': options['coverUrl'],
                'free': True, 'price': 0, 'validDuration': 0, 'purchaseEndTime': options['purchaseEnd'],
                'introduce': f'本地真实视频课程，{len(catalogue_payload(self.files))}章{len(self.files)}节。\n导入标识：{self.marker}',
                'usePeople': '学习Java后端与微服务项目的自学者',
                'detail': '使用本机已有课程视频。免费学习，无真实支付。文件来源、完整标题、哈希及真实探测时长保存在本机导入清单；内容未上传外部服务。'}
        result = self.api.request('POST', '/admin/course/courses/baseInfo/save', body=body, key='course-' + self.document['inventory']['sourceFingerprint'][:32])
        self.state['courseId'] = str(result['id'])
        self.save()

    def ensure_catalogue(self):
        course = self.state['courseId']
        tree = self.api.request('GET', '/admin/course/courses/catas/' + course, params={'see': 'false', 'withPractice': 'true'})
        if not tree:
            self.api.request('POST', '/admin/course/courses/catas/save/' + course + '/2', body=catalogue_payload(self.files))
            tree = self.api.request('GET', '/admin/course/courses/catas/' + course, params={'see': 'false', 'withPractice': 'true'})
        mapping = catalogue_mapping(self.files, tree)
        self.state['catalogueIds'] = {path: str(row['id']) for path, row in mapping.items()}
        self.save()

    def import_media(self):
        self.document['status'] = 'IMPORTING_MEDIA'
        self.save()
        for entry in self.files:
            relative = entry['relativePath']
            if relative in self.state['media']:
                cached = self.state['media'][relative]
                preview = self.api.request('GET', '/admin/media/medias/signature/preview', params={'mediaId': cached['id']})
                if preview.get('fileId') != cached['fileId']:
                    raise ImportFailure('Previously recorded media no longer matches the backend')
                continue
            verify_source({'sourceRoot': self.document['inventory']['sourceRoot'], 'files': [entry]}, rehash=False)
            key = 'offline-' + self.document['inventory']['sourceFingerprint'][:16] + '-' + fingerprint({'path': relative, 'sha256': entry['sha256']})
            operation = self.api.request('POST', '/internal/v2/media/local-import', body={'relativePath': relative, 'sha256': entry['sha256'], 'expectedBytes': entry['size']}, key=key, internal=True)
            result = self.api.operation(operation)
            if int(result.get('size', -1)) != entry['size'] or not math.isfinite(float(result.get('duration', 0))) or not 0 < float(result['duration']) <= 86400:
                raise ImportFailure('Registered media size or backend-probed duration is invalid')
            preview = self.api.request('GET', '/admin/media/medias/signature/preview', params={'mediaId': result['id']})
            file_id = preview.get('fileId')
            if not isinstance(file_id, str) or not re.fullmatch(r'[0-9a-f]{64}\.(mp4|webm)', file_id):
                raise ImportFailure('Registered media object key is invalid')
            self.state['media'][relative] = {'id': str(result['id']), 'duration': float(result['duration']), 'size': int(result['size']),
                                             'fileId': file_id, 'operationId': operation['operationId']}
            self.save()
            print(json.dumps({'stage': 'MEDIA_REGISTERED', 'completed': len(self.state['media']), 'total': len(self.files)}, ensure_ascii=True), flush=True)

    def associate_and_publish(self):
        course = self.state['courseId']
        # Do not publish a draft whose recorded objects have disappeared during an interrupted import.
        for entry in self.files:
            media = self.state['media'][entry['relativePath']]
            if not re.fullmatch(r'[0-9a-f]{64}\.(mp4|webm)', media['fileId']):
                raise ImportFailure('Manifest media object key is invalid')
            target = self.runtime.objects / media['fileId']
            if target.is_symlink() or target.resolve().parent != self.runtime.objects.resolve() or not target.is_file() or target.stat().st_size != entry['size']:
                raise ImportFailure('Imported media bytes are missing; refusing publication')
        # Existing course logic requires the complete section set in one transaction.
        links = [{'cataId': self.state['catalogueIds'][row['relativePath']], 'mediaId': self.state['media'][row['relativePath']]['id'],
                  'videoName': row['filename'], 'mediaDuration': math.ceil(self.state['media'][row['relativePath']]['duration']), 'trailer': False} for row in self.files]
        self.api.request('POST', '/admin/course/courses/media/save/' + course, body=links)
        if 'teacherId' not in self.state:
            self.state['teacherId'] = self.resolve_teacher()
        self.api.request('POST', '/admin/course/courses/teachers/save', body={'id': course, 'teachers': [{'id': self.state['teacherId'], 'isShow': True}]})
        self.api.request('GET', '/admin/course/courses/checkBeforeUpShelf/' + course)
        self.document['status'] = 'PUBLISHING'
        self.save()
        self.api.request('POST', '/admin/course/courses/upShelf', body={'id': course})
        self.api.request('POST', '/admin/search/courses/up', params={'courseIds': course})
        self.document['status'] = 'PUBLISHED'
        self.save()

    def verify(self):
        verify_source(self.document['inventory'])
        row = self.owned_course()
        if not row or int(row.get('status', 0)) != 2:
            raise ImportFailure('Owned course is not published')
        public = self.api.request('GET', '/public/courses/' + self.state['courseId'])
        if public.get('name') != self.document['options']['name'] or not public.get('free') or int(public.get('price', -1)) != 0 or int(public.get('sectionNum', -1)) != len(self.files):
            raise ImportFailure('Published course metadata differs from the free-course plan')
        tree = self.api.request('GET', '/admin/course/courses/catas/' + self.state['courseId'], params={'see': 'true', 'withPractice': 'true'})
        mapping = catalogue_mapping(self.files, tree)
        if set(self.state['media']) != {row['relativePath'] for row in self.files}:
            raise ImportFailure('Media manifest is incomplete')
        for entry in self.files:
            media = self.state['media'][entry['relativePath']]
            section = mapping[entry['relativePath']]
            if str(section.get('mediaId')) != media['id'] or int(section.get('mediaDuration', 0)) != math.ceil(media['duration']):
                raise ImportFailure('Published section media or CEIL duration differs from backend metadata')
            if not re.fullmatch(r'[0-9a-f]{64}\.(mp4|webm)', media['fileId']):
                raise ImportFailure('Manifest media object key is invalid')
            object_path = self.runtime.objects / media['fileId']
            if object_path.is_symlink() or object_path.resolve().parent != self.runtime.objects.resolve() or not object_path.is_file() or object_path.stat().st_size != entry['size'] or checksum(object_path) != entry['sha256']:
                raise ImportFailure('Imported object bytes differ from the source manifest')
        current = {str(row['id']) for row in self.api.course_rows()}
        if not set(self.state.get('baselineCourseIds', [])).issubset(current):
            raise ImportFailure('A pre-existing visible course is missing after import')
        deadline = time.monotonic() + 120
        indexed = False
        while time.monotonic() < deadline:
            page = self.api.request('GET', '/public/courses', params={'keyword': self.document['options']['name'], 'pageNo': 1, 'pageSize': 100})
            if self.state['courseId'] in {str(item['id']) for item in page.get('list', [])}:
                indexed = True
                break
            time.sleep(2)
        if not indexed:
            raise ImportFailure('Published course did not become discoverable in the search projection')
        proof = {'status': 'PASSED', 'courseId': self.state['courseId'], 'chapters': len(catalogue_payload(self.files)), 'sections': len(self.files),
                 'sourceBytes': sum(row['size'] for row in self.files),
                 'backendDurationSeconds': sum(row['duration'] for row in self.state['media'].values()),
                 'catalogueDurationSeconds': sum(math.ceil(row['duration']) for row in self.state['media'].values()),
                 'sourceHashesAndTimestampsMatched': True, 'allObjectHashesMatched': True,
                 'backendDurationsMatched': True, 'baselineCourseIdsPreserved': True, 'searchDiscoverable': True, 'sample': self.document['inventory']['sample']}
        self.document['verification'] = proof
        self.document['status'] = 'VERIFIED'
        self.save()
        return proof

    def apply(self, verify_only=False):
        with maintenance_lock(self.runtime.local):
            self.api.login()
            if verify_only:
                return self.verify()
            verify_source(self.document['inventory'], rehash=False)
            if 'baselineCourseIds' not in self.state:
                self.state['baselineCourseIds'] = [str(row['id']) for row in self.api.course_rows()]
                self.save()
            course = self.owned_course()
            if course is None:
                self.create_course()
                course = self.owned_course()
            if int(course['status']) == 2:
                # Resume after a lost publishing response without down-shelving or duplicating facts.
                self.api.request('POST', '/admin/search/courses/up', params={'courseIds': self.state['courseId']})
                return self.verify()
            if int(course['status']) != 1:
                raise ImportFailure('Owned course is no longer a new draft; refusing destructive reconciliation')
            self.ensure_catalogue()
            self.import_media()
            self.associate_and_publish()
            return self.verify()


def arguments(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group()
    action.add_argument('--apply', action='store_true', help='Mutate only the explicitly selected local deployment')
    action.add_argument('--verify', action='store_true', help='Verify an existing import without changing course or media data')
    parser.add_argument('--home', type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument('--source-root', required=True, type=Path)
    parser.add_argument('--manifest', type=Path)
    parser.add_argument('--course-name', default=DEFAULT_NAME)
    parser.add_argument('--category-id')
    parser.add_argument('--teacher-id')
    parser.add_argument('--cover-url', default='/brand-logo.png')
    parser.add_argument('--purchase-end', default='2099-12-31T23:59:59')
    parser.add_argument('--include', action='append', default=[], help='Exact dayXX/filename.mp4; isolated samples only, at most 3')
    parser.add_argument('--limit', type=int, help='First 1 to 3 source videos; isolated samples only')
    return parser.parse_args(argv)


def main(argv=None):
    args = arguments(argv)
    if not args.course_name.strip() or len(args.course_name) > 100:
        raise ImportFailure('Course name must contain 1 to 100 characters')
    runtime = Runtime(args.home)
    inventory = source_inventory(args.source_root, args.include, args.limit)
    options = {'name': args.course_name, 'categoryId': args.category_id, 'teacherId': args.teacher_id,
               'coverUrl': args.cover_url, 'purchaseEnd': args.purchase_end}
    folder = fingerprint({'source': inventory['sourceFingerprint'], 'name': args.course_name})[:24]
    importer = Importer(runtime, args.manifest or runtime.local / 'course-import' / folder / 'manifest.json', inventory, options)
    try:
        proof = importer.apply(verify_only=args.verify) if args.apply or args.verify else importer.plan()
    except Exception as error:
        importer.document['status'] = 'FAILED'
        importer.document['lastFailure'] = type(error).__name__
        importer.save()
        raise
    print(json.dumps(proof, ensure_ascii=True, indent=2))
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except ImportFailure as error:
        print(json.dumps({'status': 'FAILED', 'reason': str(error), 'sourceFilesModifiedByTool': False}, ensure_ascii=True), file=sys.stderr)
        raise SystemExit(1)
    except Exception as error:
        print(json.dumps({'status': 'FAILED', 'reason': type(error).__name__, 'privateManifestRetained': True}), file=sys.stderr)
        raise SystemExit(1)
