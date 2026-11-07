"""Exercise the actual exporter with real loopback HTTP schema responses."""

from contextlib import ExitStack
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import runpy
import sys
import tempfile
import threading
from types import ModuleType
import unittest
from unittest.mock import patch


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != '/v3/api-docs':
            self.send_error(404)
            return
        payload = json.dumps(self.server.document).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args):
        pass


class OpenApiBinaryExportTest(unittest.TestCase):
    def test_trial_streams_and_zip_downloads_keep_binary_content_and_json_keeps_envelope(
        self,
    ):
        def response():
            return {
                'responses': {
                    '200': {'description': 'OK'},
                    '206': {'description': 'Partial content'},
                }
            }

        specs = {
            'media': {
                'openapi': '3.1.0',
                'paths': {
                    '/trial-content/{id}': {'get': response()},
                    '/cloud-content/{id}': {'get': response()},
                    '/local-content/{key}': {'get': response()},
                    '/course-covers/{key}': {'get': response()},
                    '/api/v2/admin/audit': {'get': response()},
                    '/api/v2/admin/consumer-failures': {'get': response()},
                    '/medias/signature/play': {
                        'get': {
                            'responses': {
                                '200': {
                                    'description': 'OK',
                                    'content': {
                                        'application/json': {
                                            'schema': {
                                                'type': 'object',
                                                'properties': {
                                                    'expiresAt': {
                                                        'type': 'integer',
                                                        'format': 'int64',
                                                    }
                                                },
                                            }
                                        }
                                    },
                                }
                            }
                        }
                    },
                },
            },
            'user': {
                'openapi': '3.1.0',
                'paths': {
                    '/account-privacy/tasks/{id}/download': {'post': response()},
                    '/internal/privacy/export': {'get': response()},
                    '/account-privacy/requests': {'get': response()},
                    '/account-privacy/requests/{id}': {'put': response()},
                },
            },
            'promotion': {
                'openapi': '3.1.0',
                'paths': {
                    '/user-coupons/quote': {'post': response()},
                    '/user-coupons/available': {'post': response()},
                },
            },
        }
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            root = Path(directory)
            (root / 'frontend/openapi').mkdir(parents=True)
            modules = {}
            for name, spec in specs.items():
                server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
                server.document = spec
                worker = threading.Thread(target=server.serve_forever, daemon=True)
                worker.start()
                stack.callback(server.server_close)
                stack.callback(server.shutdown)
                modules[name] = (None, None, server.server_port)
            startup, prepare = ModuleType('start_service'), ModuleType('prepare')
            startup.MODULES = modules
            prepare.LOCAL, prepare.BASE = root / '.local', root / 'deploy/acceptance'
            script = (
                Path(__file__).resolve().parents[1] / 'acceptance/export_openapi.py'
            )
            with patch.dict(
                sys.modules, {'start_service': startup, 'prepare': prepare}
            ), patch.object(sys, 'argv', [str(script), *specs]):
                runpy.run_path(str(script), run_name='__main__')
            media = json.loads(
                (root / 'frontend/openapi/media.json').read_text(encoding='utf8')
            )
            user = json.loads(
                (root / 'frontend/openapi/user.json').read_text(encoding='utf8')
            )
            promotion = json.loads(
                (root / 'frontend/openapi/promotion.json').read_text(encoding='utf8')
            )
            self.assertNotIn(
                '/api/v2/services/promotion/user-coupons/quote', promotion['paths']
            )
            self.assertIn(
                '/api/v2/services/promotion/user-coupons/available', promotion['paths']
            )
            for path in ('/trial-content/{id}', '/cloud-content/{id}'):
                operation = media['paths']['/api/v2/services/media' + path]['get']
                for status in ('200', '206'):
                    self.assertEqual(
                        {
                            'video/mp4': {
                                'schema': {'type': 'string', 'format': 'binary'}
                            }
                        },
                        operation['responses'][status]['content'],
                    )
            local = media['paths']['/api/v2/services/media/local-content/{key}']['get'][
                'responses'
            ]['200']['content']
            self.assertIn('video/webm', local)
            self.assertNotIn('application/json', local)
            archive = user['paths'][
                '/api/v2/services/user/account-privacy/tasks/{id}/download'
            ]['post']['responses']['200']['content']
            self.assertEqual(
                {'application/zip': {'schema': {'type': 'string', 'format': 'binary'}}},
                archive,
            )
            self.assertFalse(any('/internal/' in path for path in user['paths']))
            self.assertIn('/api/v2/admin/user/account-privacy/requests', user['paths'])
            self.assertNotIn(
                '/api/v2/services/user/account-privacy/requests', user['paths']
            )
            self.assertIn('/api/v2/admin/audit/media', media['paths'])
            self.assertIn('/api/v2/admin/consumer-failures/media', media['paths'])
            schema = media['paths']['/api/v2/services/media/medias/signature/play'][
                'get'
            ]['responses']['200']['content']['application/json']['schema']
            self.assertEqual(['code', 'msg', 'data', 'requestId'], schema['required'])
            self.assertEqual(
                'string',
                schema['properties']['data']['properties']['expiresAt']['type'],
            )


if __name__ == '__main__':
    unittest.main()
