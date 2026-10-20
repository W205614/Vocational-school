import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import scheduled_backup


class ScheduledBackupTest(unittest.TestCase):
    @unittest.skipUnless(os.name == 'nt', 'Windows console regression')
    def test_real_worker_and_its_child_have_no_console_window(self):
        with tempfile.TemporaryDirectory(prefix='windowless backup ') as directory:
            home = Path(directory)
            (home / 'compose.yaml').touch()
            (home / 'maintenance.py').write_text(
                "import ctypes,json,subprocess,sys\n"
                "from pathlib import Path\n"
                "child=subprocess.check_output([sys.executable,'-c',"
                "'import ctypes;print(ctypes.windll.kernel32.GetConsoleWindow())'],text=True)\n"
                "Path('console.json').write_text(json.dumps([ctypes.windll.kernel32.GetConsoleWindow(),int(child)]))\n"
            )
            with patch.object(scheduled_backup, '__file__', str(home / 'scheduled_backup.py')):
                self.assertEqual(0, scheduled_backup.run(home, 'tianji-test', 0, sys.executable))
            self.assertEqual([0, 0], json.loads((home / 'console.json').read_text()))

    def test_worker_is_windowless_and_preserves_arguments_environment_and_backup_status(self):
        with tempfile.TemporaryDirectory(prefix='backup home ') as directory:
            home = Path(directory)
            (home / 'compose.yaml').touch()
            status = home / '.local/ops-status/backup.json'
            status.parent.mkdir(parents=True)
            status.write_text('{"lastSuccess":123}')
            with patch.object(scheduled_backup.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)) as child:
                self.assertEqual(0, scheduled_backup.run(home, 'tianji-test', 1000, 'C:/Python Space/python.exe'))
            args, kwargs = child.call_args
            self.assertEqual('C:/Python Space/python.exe', args[0][0])
            self.assertEqual('scheduled', args[0][-1])
            self.assertEqual(str(home.resolve()), kwargs['env']['TJ_COMPACT_HOME'])
            self.assertEqual('tianji-test', kwargs['env']['TJ_COMPACT_PROJECT'])
            self.assertEqual('1000', kwargs['env']['TJ_COMPACT_PORT_OFFSET'])
            self.assertEqual(subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0, kwargs['creationflags'])
            self.assertEqual(subprocess.DEVNULL, kwargs['stdout'])
            self.assertEqual('{"lastSuccess":123}', status.read_text())
            self.assertEqual(0, json.loads(status.with_name('scheduler.json').read_text())['exitCode'])
            self.assertFalse((home / '.local/maintenance.log').exists())

    def test_nonzero_worker_exit_is_propagated_and_check_status_is_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            (home / 'compose.yaml').touch()
            with patch.object(scheduled_backup.subprocess, 'run', return_value=subprocess.CompletedProcess([], 7)):
                self.assertEqual(7, scheduled_backup.run(home, 'tianji-test', 0, 'python.exe'))
            with patch.object(scheduled_backup.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)):
                self.assertEqual(0, scheduled_backup.run(home, 'tianji-test', 0, 'python.exe'))
            self.assertEqual(0, json.loads((home / '.local/ops-status/scheduler.json').read_text())['exitCode'])
            self.assertEqual(1, len(list((home / '.local/ops-status').glob('scheduler*'))))

    def test_missing_worker_reports_failure_without_logging_private_exception(self):
        with tempfile.TemporaryDirectory() as directory:
            home = Path(directory)
            (home / 'compose.yaml').touch()
            with patch.object(scheduled_backup.subprocess, 'run', side_effect=FileNotFoundError('private path or credential')):
                self.assertEqual(1, scheduled_backup.run(home, 'tianji-test', 0, 'missing.exe'))
            log = (home / '.local/maintenance.log').read_text()
            self.assertNotIn('private path or credential', log)


if __name__ == '__main__':
    unittest.main()
