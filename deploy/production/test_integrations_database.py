"""Explicit opt-in tests against a disposable synthetic MySQL container only."""

import copy
from pathlib import Path
import os
import re
import subprocess
import threading
import unittest

import database
import integrations
from test_integrations import spec


@unittest.skipUnless(
    os.getenv("ZXT_READINESS_MYSQL_CONTAINER"),
    "requires explicit isolated MySQL container",
)
class IntegrationDatabaseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        container = os.environ["ZXT_READINESS_MYSQL_CONTAINER"]
        if not re.fullmatch(r"zhixuetang-readiness-test-[a-z0-9-]+", container):
            raise ValueError("Only the disposable readiness test container is allowed")
        password = os.environ["ZXT_READINESS_MYSQL_PASSWORD"]

        class Session(database.MysqlSession):
            def __enter__(self):
                self.process = subprocess.Popen(
                    [
                        "docker",
                        "exec",
                        "-i",
                        "-e",
                        "MYSQL_PWD=" + password,
                        container,
                        "mysql",
                        "-uroot",
                        "--batch",
                        "--raw",
                        "--silent",
                        "--unbuffered",
                        "--skip-column-names",
                        "--default-character-set=utf8mb4",
                    ],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.DEVNULL,
                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
                )

                def read():
                    try:
                        for line in iter(self.process.stdout.readline, b""):
                            self.lines.put(line.decode("utf8").rstrip("\r\n"))
                    finally:
                        self.lines.put(None)

                threading.Thread(target=read, daemon=True).start()
                return self

            def mysql(self, sql, database=None):
                target = (
                    "acceptance_bootstrap_" + database.removeprefix("tj_")
                    if database
                    else None
                )
                if target:
                    sql = "USE `" + target + "`; " + sql
                return super().mysql(sql)

            def __exit__(self, *args):
                super().__exit__(*args)
                self.process.stdout.close()

        cls.Session = Session
        schema = (
            Path(__file__).resolve().parents[2] / "deploy/compact/schema.sql"
        ).read_text(encoding="utf8")
        with Session([], "root") as session:
            for domain, tables in {
                "pay": ["pay_channel"],
                "message": [
                    "sms_third_platform",
                    "notice_template",
                    "message_template",
                ],
                "user": ["user", "user_detail"],
            }.items():
                session.mysql(
                    "CREATE DATABASE IF NOT EXISTS acceptance_bootstrap_" + domain
                )
                for table in tables:
                    create = re.search(
                        r"CREATE TABLE `" + table + r"` \(.*?;", schema, re.S
                    ).group()
                    session.mysql(
                        create.replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS", 1),
                        "tj_" + domain,
                    )
            session.mysql(
                "INSERT IGNORE INTO `user`(id,username,cell_phone,password,type,status) VALUES(123,'synthetic_bootstrap_admin','13900000000','synthetic-password-hash',1,1)",
                "tj_user",
            )
            session.mysql(
                "INSERT IGNORE INTO user_detail(id,type,name,role_id) VALUES(123,1,'synthetic admin',1)",
                "tj_user",
            )

    def test_actual_mysql_initialization_idempotence_conflict_and_transaction_rollback(
        self,
    ):
        with self.Session([], "root") as session:
            with database.database_lock(session):
                first = integrations.configure(session, spec())
                self.assertEqual(
                    {"CREATED", "UNCHANGED"} & set(first.values()), set(first.values())
                )
                again = integrations.configure(session, spec())
                self.assertEqual({"UNCHANGED"}, set(again.values()))
                self.assertEqual(
                    "1",
                    session.mysql(
                        "SELECT COUNT(*) FROM notice_template WHERE code='VERIFY_CODE'",
                        "tj_message",
                    ),
                )
                modified = spec()
                modified["sms"]["sign_name"] = "另一签名"
                with self.assertRaises(integrations.IntegrationError):
                    integrations.configure(session, modified)
                self.assertEqual(
                    "测试学堂",
                    session.mysql(
                        "SELECT sign_name FROM message_template WHERE platform_code='aliYun'",
                        "tj_message",
                    ),
                )
                session.mysql(
                    "DELETE FROM pay_channel WHERE channel_code='aliPay'", "tj_pay"
                )
                with self.assertRaises(integrations.IntegrationError):
                    integrations.configure(session, modified)
                self.assertEqual(
                    "0",
                    session.mysql(
                        "SELECT COUNT(*) FROM pay_channel WHERE channel_code='aliPay'",
                        "tj_pay",
                    ),
                )
                integrations.configure(session, spec())
                session.mysql(
                    "UPDATE pay_channel SET status=2 WHERE channel_code='aliPay'",
                    "tj_pay",
                )
                try:
                    with self.assertRaises(integrations.IntegrationError):
                        integrations.configure(session, spec())
                finally:
                    session.mysql(
                        "UPDATE pay_channel SET status=1 WHERE channel_code='aliPay'",
                        "tj_pay",
                    )
                invalid = spec()
                invalid["operator_id"] = 456
                with self.assertRaises(integrations.IntegrationError):
                    integrations.configure(session, invalid)


if __name__ == "__main__":
    unittest.main()
