"""Protect suite selection and the existing explicit-test CLI contract."""
import contextlib
import io
import unittest
import setup  # Adds the acceptance tools to the import path, without preparing data.
from run_tests import parse_arguments
from test_suites import CORE_TESTS, REGRESSION_TESTS


class TestSelectionTest(unittest.TestCase):
    def test_old_default_and_explicit_selection_remain_available(self):
        default = parse_arguments([])
        self.assertEqual(('tj-common', 'PageQueryTest,ReliabilityDatabaseTest'),
                         (default.modules, default.tests))
        explicit = parse_arguments(['--modules', 'tj-exam', '--tests', 'ExamReliabilityTest'])
        self.assertEqual(('tj-exam', 'ExamReliabilityTest'), (explicit.modules, explicit.tests))

    def test_named_suites_select_the_reactor_and_preserve_core_coverage(self):
        for name in ('core', 'regression'):
            selected = parse_arguments(['--suite', name])
            self.assertEqual('all', selected.modules)
            self.assertEqual(len(selected.tests.split(',')), len(set(selected.tests.split(','))))
        self.assertTrue(set(CORE_TESTS).issubset(REGRESSION_TESTS))
        self.assertNotIn('DelayTaskTest', REGRESSION_TESTS)

    def test_conflicting_and_unknown_selections_fail_before_execution(self):
        for arguments in (['--suite', 'core', '--tests', 'ExamReliabilityTest'],
                          ['--suite', 'unknown']):
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as result:
                parse_arguments(arguments)
            self.assertEqual(2, result.exception.code)


if __name__ == '__main__':
    unittest.main()
