"""Pure helper tests: no Docker, HTTP, setup side effects or business writes."""
import importlib.util
import os
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import Mock, patch


def fixture_module():
    setup = types.ModuleType('setup')
    setup.PROJECT = 'tianji-compact'
    setup.PORTS = {'gateway': 25310}
    setup.mysql = Mock()
    spec = importlib.util.spec_from_file_location('motivation_fixture_under_test', Path(__file__).with_name('motivation_fixture.py'))
    module = importlib.util.module_from_spec(spec)
    with patch.dict(sys.modules, {'setup': setup}):
        spec.loader.exec_module(module)
    return module, setup


class MotivationFixtureTest(unittest.TestCase):
    def setUp(self):
        self.fixture, self.setup = fixture_module()

    def test_charset_introduced_literals_roundtrip_utf8_quotes_backslashes_and_empty_values(self):
        for value in ['', 'learning.exam', '雪地里的课程', "quote' slash\\ newline\n; --", 'a1b2c3d4-e5f6-4778-9900-aabbccddeeff']:
            with self.subTest(value=value):
                encoded = self.fixture.literal(value)
                self.assertRegex(encoded, r"^_utf8mb4 X'[0-9a-f]*'$", 'Must remain a literal, not a CONVERT expression with conflicting collation coercibility')
                hexadecimal = encoded.removeprefix("_utf8mb4 X'").removesuffix("'")
                self.assertEqual(value, bytes.fromhex(hexadecimal).decode('utf8'))

    def test_primary_deployment_rejects_the_flag_while_only_explicit_clones_or_ci_are_allowed(self):
        cases = [('tianji-compact', '0', 'false', False), ('tianji-compact', '1', 'false', False),
                 ('motivation-clone', '0', 'false', False), ('motivation-clone', '1', 'false', True),
                 ('tianji-compact', '0', 'true', True)]
        for project, flag, ci, allowed in cases:
            with self.subTest(project=project, flag=flag, ci=ci), patch.dict(os.environ, {'TJ_MOTIVATION_FIXTURE': flag, 'GITHUB_ACTIONS': ci}):
                self.setup.PROJECT = project
                if allowed:
                    self.fixture.require_isolated()
                else:
                    with self.assertRaises(RuntimeError):
                        self.fixture.require_isolated()
        self.setup.mysql.assert_not_called()

    def test_schema_selection_is_allowlisted_before_any_statement_is_sent(self):
        self.setup.mysql.return_value = '1'
        self.assertEqual('1', self.fixture.sql('tj_learning', 'SELECT 1'))
        self.setup.mysql.assert_called_once_with('USE `tj_learning`;\nSELECT 1')
        for database in ['mysql', 'tj_learning;DROP DATABASE tj_learning', 'arbitrary']:
            with self.subTest(database=database), self.assertRaises(ValueError):
                self.fixture.sql(database, 'SELECT 1')
        self.assertEqual(1, self.setup.mysql.call_count)

    def test_identifiers_reject_sql_and_keep_large_values_exact(self):
        self.assertEqual('850000000000000001', self.fixture.identifier(850000000000000001))
        for value in ['0', '-1', '1;SELECT 1', '1 OR 1=1', None, 1.5, '9223372036854775808']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.fixture.identifier(value)

    def test_reward_failure_diagnostics_preserve_predicates_without_returning_identities(self):
        self.setup.mysql.return_value = '{"started":0,"ownerMatches":1}'
        self.assertEqual('{"started":0,"ownerMatches":1}', self.fixture.reward_diagnostics('synthetic-request', '101', '102', '103'))
        statement = self.setup.mysql.call_args.args[0]
        for predicate in ['receiptSucceeded', 'ticketMatches', 'ownerMatches', 'couponMatches', 'stockMatches', 'unused', 'started', 'unexpired', 'sessionTimeZone', 'shanghaiNow']:
            self.assertIn("'" + predicate + "'", statement)
        self.assertIn('u.term_begin_time<=NOW()', statement)
        self.assertIn('u.term_end_time>NOW()', statement)
        self.assertNotIn("'userId'", statement)
        self.assertNotIn("'requestId'", statement)
        self.setup.mysql.return_value = ''
        self.assertEqual('{"receiptMissing":true}', self.fixture.reward_diagnostics('synthetic-request', '101', '102', '103'))


if __name__ == '__main__':
    unittest.main()
