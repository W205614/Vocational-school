import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import database


class Adapter:
    def __init__(self, answers=None):
        self.answers = answers or {}; self.calls = []; self.query_count = 0
    def mysql(self, sql, db=None):
        self.calls.append((sql, db)); self.query_count += 1
        for key, value in self.answers.items():
            if key in sql:
                return value
        return ""


class ManagedDatabaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(); self.addCleanup(self.temporary.cleanup)
        self.folder = Path(self.temporary.name); self.ca = self.folder / "ca.pem"; self.ca.write_text("synthetic-ca")
        self.options = self.folder / "migration.cnf"
        self.options.write_text("[client]\nhost=mysql.managed.acme.test\nuser=ddl_tianji\npassword=synthetic-private-password\nssl-ca=" + str(self.ca) + "\n", encoding="utf8")
        database.protect(self.options)

    def test_client_forces_verified_tls_host_and_keeps_password_out_of_arguments(self):
        command, user = database.client_options(self.options, "mysql.managed.acme.test", "mysql8")
        self.assertEqual("mysql8", command[0]); self.assertTrue(command[1].startswith("--defaults-extra-file="))
        self.assertIn("--ssl-mode=VERIFY_IDENTITY", command); self.assertIn("--host=mysql.managed.acme.test", command)
        self.assertIn("--skip-reconnect", command); self.assertIn("--local-infile=0", command)
        self.assertEqual("ddl_tianji", user); self.assertNotIn("synthetic-private-password", " ".join(command))

    def test_wrong_host_tls_downgrade_and_runtime_identity_are_rejected(self):
        with self.assertRaises(database.DatabaseError): database.client_options(self.options, "other.managed.acme.test")
        for replacement in [("ssl-mode=DISABLED\n", None), ("", "app_media"), ("", "root")]:
            content = self.options.read_text().replace("user=ddl_tianji", "user=" + (replacement[1] or "ddl_tianji")) + replacement[0]
            self.options.write_text(content)
            with self.assertRaises(database.DatabaseError): database.client_options(self.options, "mysql.managed.acme.test")
            self.options.write_text("[client]\nhost=mysql.managed.acme.test\nuser=ddl_tianji\npassword=synthetic-private-password\nssl-ca=" + str(self.ca) + "\n")

    def test_malformed_credentials_do_not_expose_file_contents(self):
        self.options.write_text("password=DO_NOT_DISCLOSE_THIS_VALUE")
        with self.assertRaises(database.DatabaseError) as error: database.client_options(self.options, "mysql.managed.acme.test")
        self.assertNotIn("DO_NOT_DISCLOSE", str(error.exception))

    def test_default_options_cannot_override_the_dedicated_client_group(self):
        self.options.write_text("[DEFAULT]\npassword=synthetic\n[client]\nhost=mysql.managed.acme.test\n")
        with self.assertRaises(database.DatabaseError): database.client_options(self.options, "mysql.managed.acme.test")

    def test_ddl_grants_require_real_privilege_lists_and_all_owned_scopes(self):
        for grants in ["GRANT `CREATE_ALTER_TRIGGER`@`%` TO `ddl_tianji`@`%`", "GRANT SELECT, INSERT, UPDATE, DELETE ON *.* TO `CREATE_ALTER_TRIGGER`@`%`", "GRANT CREATE, ALTER, TRIGGER ON `other`.* TO `ddl_tianji`@`%`"]:
            with self.assertRaises(database.DatabaseError): database.require_ddl_grants(grants)
        grants = "\n".join("GRANT SELECT, CREATE, ALTER, TRIGGER ON `" + name.replace("_", "\\_") + "`.* TO `ddl_tianji`@`%`" for name in database.DATABASES)
        database.require_ddl_grants(grants)
        database.require_ddl_grants("GRANT ALL PRIVILEGES ON *.* TO `ddl_tianji`@`%`")

    def test_public_init_is_schema_only_and_removes_destructive_dump_commands(self):
        sql = database.initialization_sql()
        self.assertNotIn("DROP TABLE", sql); self.assertNotIn("DEFAULT ENCRYPTION='N'", sql); self.assertNotIn("LOCK TABLES", sql)
        self.assertNotIn("INSERT INTO `user`", sql); self.assertNotIn("INSERT INTO `order`", sql)
        self.assertIn("INSERT INTO `role`", sql); self.assertIn("INSERT INTO `schema_migration`", sql)
        manifest = database.sources(); self.assertEqual(64, len(manifest["sha256"])); self.assertIn("deploy/compact/schema.sql", manifest["files"])

    def test_catalog_business_rows_fail_closed(self):
        root = self.folder / "repo"; (root / "deploy/compact").mkdir(parents=True)
        original = database.ROOT / "deploy/compact/schema.sql"
        (root / "deploy/compact/schema.sql").write_bytes(original.read_bytes())
        (root / "deploy/compact/catalog.sql").write_text("USE `tj_user`;\nINSERT INTO `user` VALUES (1);\n")
        with self.assertRaises(database.DatabaseError): database.initialization_sql(root)

    def test_occupied_init_sends_no_schema_mutation_or_migration(self):
        adapter = Adapter({"information_schema.schemata": "tj_user"})
        with self.assertRaises(database.DatabaseError): database.initialize(adapter, self.folder, lambda *_: self.fail("migration ran"))
        self.assertEqual(1, len(adapter.calls)); self.assertTrue(adapter.calls[0][0].startswith("SELECT"))

    def test_empty_init_imports_public_baseline_then_forward_migrations(self):
        adapter = Adapter({"SELECT COUNT(*) FROM `user`": "0"}); calls = []
        applied = database.initialize(adapter, self.folder, lambda connection, folder: calls.append(connection) or ["new-forward.sql"])
        self.assertEqual(["new-forward.sql"], applied); self.assertEqual([adapter], calls)
        self.assertTrue(any("CREATE DATABASE" in query for query, _ in adapter.calls))

    def test_advisory_lock_is_held_for_operation_and_released_on_error(self):
        adapter = Adapter({"GET_LOCK": "1", "RELEASE_LOCK": "1"})
        with self.assertRaisesRegex(RuntimeError, "synthetic"):
            with database.database_lock(adapter): raise RuntimeError("synthetic")
        self.assertIn("GET_LOCK", adapter.calls[0][0]); self.assertIn("RELEASE_LOCK", adapter.calls[-1][0])
        with self.assertRaises(database.DatabaseError):
            with database.database_lock(Adapter({"GET_LOCK": "0"})): self.fail("acquired lock")

    def test_protected_receipts_are_new_only_and_never_public(self):
        path = self.folder / "receipt.json"; database.new_json(path, {"secret": "private"})
        self.assertEqual(path.resolve(), database.private_file(path))
        with self.assertRaises(FileExistsError): database.new_json(path, {"secret": "replaced"})

    def test_protected_state_directories_remain_usable(self):
        folder = self.folder / "state"; folder.mkdir(); database.protect(folder)
        database.new_json(folder / "result.json", {"status": "synthetic"})
        self.assertTrue((folder / "result.json").is_file())

    def test_existing_forward_engine_rejects_duplicates_before_ddl(self):
        adapter = Adapter({"SELECT order_detail_id,COUNT(*)": "synthetic duplicate"})
        with self.assertRaisesRegex(RuntimeError, "duplicates"):
            database.forward_migrations(adapter, self.folder)
        self.assertEqual(1, len(adapter.calls)); self.assertTrue(adapter.calls[0][0].startswith("SELECT"))

    def test_existing_forward_engine_rejects_changed_checksums_without_replaying_body(self):
        adapter = Adapter({"SELECT checksum FROM schema_migration": "0" * 64})
        with self.assertRaisesRegex(RuntimeError, "checksum changed"):
            database.forward_migrations(adapter, self.folder)
        self.assertFalse(any(sql.startswith("ALTER TABLE") for sql, _ in adapter.calls))

    def test_admin_spec_requires_namespace_and_explicit_admin_role(self):
        path = self.folder / "admin.json"
        spec = {"namespace": "tianji-prod", "username": "tianji_prod_admin", "phone": "13900000000", "name": "Synthetic administrator", "role": "admin"}
        database.new_json(path, spec); self.assertEqual(spec, database.admin_spec(path, "tianji-prod"))
        for field, value in [("role", "teacher"), ("username", "unscoped_admin"), ("namespace", "another-prod")]:
            bad = dict(spec); bad[field] = value; database.overwrite_private_json(path, bad)
            with self.assertRaises(database.DatabaseError): database.admin_spec(path, "tianji-prod")

    def test_existing_admin_is_never_reset_and_no_receipt_is_written(self):
        adapter = Adapter({"FROM role": "1", "u.status=1": "1"}); receipt = self.folder / "receipt.json"
        with self.assertRaises(database.DatabaseError): database.bootstrap_admin(adapter, {}, receipt)
        self.assertFalse(receipt.exists()); self.assertTrue(all(sql.startswith("SELECT") for sql, _ in adapter.calls))

    def test_admin_receipt_precedes_transaction_and_contains_random_temporary_credential(self):
        import bcrypt
        adapter = Adapter({"FROM role": "1", "u.status=1": "0", "SELECT COUNT(*) FROM `user` WHERE": "0"})
        spec = {"namespace": "tianji-prod", "username": "tianji_prod_admin", "phone": "13900000000", "name": "Synthetic administrator", "role": "admin"}
        receipt = self.folder / "receipt.json"; database.bootstrap_admin(adapter, spec, receipt)
        result = json.loads(receipt.read_text()); self.assertEqual("APPLIED", result["status"]); self.assertTrue(result["mustChangePassword"])
        self.assertGreaterEqual(len(result["temporaryPassword"]), 32)
        sql = adapter.calls[-1][0]; self.assertIn("START TRANSACTION", sql); self.assertIn("INTERVAL 24 HOUR", sql); self.assertNotIn(result["temporaryPassword"], sql)
        self.assertNotIn("INSERT INTO `order`", sql); self.assertNotIn("student", sql)

    def test_mysql_session_framing_uses_stdin_and_refuses_unowned_database(self):
        session = database.MysqlSession(["mysql"], "ddl_tianji")
        class Process:
            stdin = io.BytesIO()
            def poll(self): return None
        session.process = Process()
        with patch.object(database.secrets, "token_hex", return_value="fixed"):
            session.lines.put("a\tb"); session.lines.put("__TIANJI_RESULT_fixed__")
            self.assertEqual("a\tb", session.mysql("SELECT 1", "tj_auth"))
        self.assertIn(b"USE `tj_auth`", session.process.stdin.getvalue())
        with self.assertRaises(database.DatabaseError): session.mysql("SELECT 1", "mysql")


if __name__ == "__main__": unittest.main()
