"""A recorded permission migration must not hide a missing lock singleton."""
from pathlib import Path
import hashlib
import sqlite3
import unittest
from unittest.mock import patch
import setup  # Adds acceptance tools without preparing or changing runtime data.
import migrate


class PermissionGuardMigrationTest(unittest.TestCase):
    def test_forward_seed_repairs_recorded_v002_and_preserves_existing_state(self):
        folder = Path(__file__).resolve().parents[1] / 'acceptance/migrations/tj_auth'
        old = folder / 'V002__permission_publication.sql'
        repair = folder / 'V004__seed_permission_guard.sql'
        checksum = hashlib.sha256(old.read_bytes()).hexdigest()
        for initial in ('empty', 'existing', 'absent'):
            with self.subTest(initial=initial), sqlite3.connect(':memory:') as connection:
                # Exercise the real checksum runner and SQL with only MySQL dialect spelling adapted.
                def mysql(sql, database):
                    self.assertEqual('acceptance_auth', database)
                    sql = sql.replace('DATETIME(3)', 'TEXT').replace('CURRENT_TIMESTAMP(3)', 'CURRENT_TIMESTAMP')
                    sql = sql.replace('INSERT IGNORE', 'INSERT OR IGNORE')
                    if sql.lstrip().startswith('SELECT'):
                        return '\n'.join('\t'.join(str(value) for value in row) for row in connection.execute(sql))
                    connection.executescript(sql)
                    return ''

                with patch.object(migrate, 'mysql', mysql):
                    mysql("CREATE TABLE schema_migration(version VARCHAR(128) PRIMARY KEY,checksum CHAR(64),applied_at TEXT);"
                          "CREATE TABLE role_privilege(role_id INT,privilege_id INT);"
                          "INSERT INTO role_privilege VALUES(51,61);"
                          "INSERT INTO schema_migration VALUES('tj_auth/V002__permission_publication.sql','" + checksum + "','2026-10-06');", 'acceptance_auth')
                    if initial != 'absent':
                        mysql('CREATE TABLE auth_cache_guard(id INT PRIMARY KEY);', 'acceptance_auth')
                    if initial == 'existing':
                        mysql('INSERT INTO auth_cache_guard VALUES(1);', 'acceptance_auth')
                    self.assertEqual(['acceptance_auth/tj_auth/V004__seed_permission_guard.sql'],
                                     migrate.apply_migrations('acceptance_auth', [old, repair]))
                    self.assertEqual([], migrate.apply_migrations('acceptance_auth', [old, repair]))
                    # The seed itself also tolerates a retry before migration bookkeeping commits.
                    mysql(repair.read_text(encoding='utf8'), 'acceptance_auth')
                    self.assertEqual('1', mysql('SELECT id FROM auth_cache_guard', 'acceptance_auth'))
                    self.assertEqual('51\t61', mysql('SELECT * FROM role_privilege', 'acceptance_auth'))
                    self.assertEqual(checksum + '\t2026-10-06', mysql("SELECT checksum,applied_at FROM schema_migration WHERE version='tj_auth/V002__permission_publication.sql'", 'acceptance_auth'))


if __name__ == '__main__':
    unittest.main()
