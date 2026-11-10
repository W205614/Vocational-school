import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from cryptography.fernet import Fernet, InvalidToken
from secret_store import write_values, read_values, environment


class HostCredentialStorageTest(unittest.TestCase):
    def test_real_encryption_roundtrip_tamper_and_legacy_preservation(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ):
            home = Path(temporary)
            os.environ['ZXT_SECRET_KEY'] = Fernet.generate_key().decode()
            values = {'ACCEPTANCE_DB_PASSWORD': 'synthetic-database-password-918273'}
            write_values(home, 'environment', values)
            path = home / '.local/environment.enc'
            self.assertNotIn(values['ACCEPTANCE_DB_PASSWORD'].encode(), path.read_bytes())
            self.assertEqual(values, read_values(home, 'environment'))
            self.assertEqual(values, environment(home))
            path.write_bytes(path.read_bytes()[:-8] + b'corrupt!')
            with self.assertRaises(InvalidToken):
                read_values(home, 'environment')
            if os.name == 'nt':
                key = home / '.local/secrets.key.dpapi'
                original = key.with_name('retained-key.dpapi')
                key.rename(original)
                with self.assertRaisesRegex(RuntimeError, 'DPAPI key is missing'):
                    read_values(home, 'environment')
                self.assertFalse(key.exists())
                original.rename(key)
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ):
            home = Path(temporary)
            legacy = home / '.env'
            legacy.write_text('ACCEPTANCE_DB_PASSWORD=legacy-fixture\n')
            before = legacy.read_bytes()
            self.assertEqual('legacy-fixture', environment(home)['ACCEPTANCE_DB_PASSWORD'])
            self.assertEqual(before, legacy.read_bytes())
            self.assertFalse((home / '.local/environment.enc').exists())

    def test_wrong_external_key_cannot_decrypt(self):
        if os.name == 'nt':
            self.skipTest('Windows default uses the current account DPAPI key')
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ):
            os.environ['ZXT_SECRET_KEY'] = Fernet.generate_key().decode()
            write_values(temporary, 'environment', {'value': 'synthetic'})
            os.environ['ZXT_SECRET_KEY'] = Fernet.generate_key().decode()
            with self.assertRaises(InvalidToken):
                read_values(temporary, 'environment')
