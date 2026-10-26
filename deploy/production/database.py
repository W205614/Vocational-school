"""Explicit managed-MySQL bootstrap and checked forward migration.

Uses the MySQL 8 client with a protected option file and verified server identity.
Never imports Docker configuration, demo data, or runtime application credentials.
"""
from __future__ import annotations

import argparse
import configparser
from contextlib import contextmanager, redirect_stdout
import hashlib
import importlib.util
import io
import ipaddress
import json
import os
from pathlib import Path
import queue
import re
import secrets
import stat
import subprocess
import sys
import threading

ROOT = Path(__file__).resolve().parents[2]
ACCEPTANCE = ROOT / "deploy/acceptance"
DATABASES = ("tj_auth", "tj_user", "tj_course", "tj_learning", "tj_trade", "tj_promotion",
             "tj_exam", "tj_remark", "tj_pay", "tj_search", "tj_message", "tj_media")
LOCK = "tianji.production.database.v1"


class DatabaseError(RuntimeError):
    pass


def private_file(path):
    path = Path(path)
    if path.is_symlink() or not path.is_file():
        raise DatabaseError("A regular protected file is required")
    if os.name == "nt":
        import win32api, win32con, win32security
        current = win32security.GetTokenInformation(win32security.OpenProcessToken(win32api.GetCurrentProcess(), win32con.TOKEN_QUERY), win32security.TokenUser)[0]
        allowed = {win32security.ConvertSidToStringSid(current), "S-1-5-18", "S-1-5-32-544"}
        descriptor = win32security.GetNamedSecurityInfo(str(path), win32security.SE_FILE_OBJECT, win32security.DACL_SECURITY_INFORMATION)
        acl = descriptor.GetSecurityDescriptorDacl()
        if acl is None:
            raise DatabaseError("Protected files must not have a NULL DACL")
        for index in range(acl.GetAceCount()):
            ace = acl.GetAce(index)
            if ace[0][0] in {win32security.ACCESS_ALLOWED_ACE_TYPE, win32security.ACCESS_ALLOWED_OBJECT_ACE_TYPE} and win32security.ConvertSidToStringSid(ace[-1]) not in allowed:
                raise DatabaseError("Protected file grants access outside its operator, SYSTEM and Administrators")
    elif stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise DatabaseError("Protected file must have mode 0600 or stricter")
    return path.resolve()


def protect(path):
    if os.name == "nt":
        import win32api, win32con, win32security
        current = win32security.GetTokenInformation(win32security.OpenProcessToken(win32api.GetCurrentProcess(), win32con.TOKEN_QUERY), win32security.TokenUser)[0]
        acl = win32security.ACL()
        for sid in [current, win32security.ConvertStringSidToSid("S-1-5-18"), win32security.ConvertStringSidToSid("S-1-5-32-544")]:
            if Path(path).is_dir():
                acl.AddAccessAllowedAceEx(win32security.ACL_REVISION, win32con.OBJECT_INHERIT_ACE | win32con.CONTAINER_INHERIT_ACE, win32con.GENERIC_ALL, sid)
            else:
                acl.AddAccessAllowedAce(win32security.ACL_REVISION, win32con.GENERIC_ALL, sid)
        win32security.SetNamedSecurityInfo(str(path), win32security.SE_FILE_OBJECT,
            win32security.DACL_SECURITY_INFORMATION | win32security.PROTECTED_DACL_SECURITY_INFORMATION,
            None, None, acl, None)
    else:
        os.chmod(path, 0o700 if Path(path).is_dir() else 0o600)


def new_json(path, value):
    path = Path(path)
    if not path.parent.is_dir() or path.is_symlink():
        raise DatabaseError("Output parent must already exist; links are forbidden")
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        protect(path)
        with os.fdopen(fd, "w", encoding="utf8") as output:
            fd = None
            json.dump(value, output, ensure_ascii=False, indent=2)
            output.flush(); os.fsync(output.fileno())
    finally:
        if fd is not None:
            os.close(fd)


def overwrite_private_json(path, value):
    path = private_file(path)
    with path.open("w", encoding="utf8") as output:
        json.dump(value, output, ensure_ascii=False, indent=2)
        output.flush(); os.fsync(output.fileno())


def client_options(defaults, expected_host, executable="mysql"):
    defaults = private_file(defaults)
    parser = configparser.ConfigParser(interpolation=None, strict=True)
    try:
        parser.read(defaults, encoding="utf8")
    except configparser.Error:
        raise DatabaseError("Invalid protected MySQL option file") from None
    if parser.sections() != ["client"] or parser.defaults():
        raise DatabaseError("Use one dedicated [client] option group")
    values = dict(parser["client"])
    if set(values) - {"host", "port", "user", "password", "ssl-ca", "ssl-mode"}:
        raise DatabaseError("Unexpected option in the migration credential file")
    host = values.get("host", "").lower()
    if not re.fullmatch(r"[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?", expected_host or "") or expected_host.lower() != host or host == "localhost":
        raise DatabaseError("Expected host must exactly match the protected provider DNS hostname")
    try:
        ipaddress.ip_address(host)
    except ValueError:
        pass
    else:
        raise DatabaseError("A provider DNS hostname is required for TLS identity verification")
    user = values.get("user", "")
    if not user or len(user) > 32 or user.lower() in {"root", "admin"} or user.lower().startswith("app_") or not values.get("password"):
        raise DatabaseError("Use a separate named migration identity, never a runtime application or root identity")
    if values.get("ssl-mode", "VERIFY_IDENTITY") != "VERIFY_IDENTITY":
        raise DatabaseError("TLS downgrade is forbidden")
    ca = Path(values.get("ssl-ca", ""))
    if not ca.is_absolute() or not ca.is_file():
        raise DatabaseError("An explicit absolute trusted CA file is required")
    port = values.get("port", "3306")
    if not port.isdigit() or not 1 <= int(port) <= 65535:
        raise DatabaseError("Invalid MySQL port")
    command = [executable, "--defaults-extra-file=" + str(defaults),
        "--protocol=TCP", "--host=" + host, "--port=" + port, "--user=" + user,
        "--ssl-mode=VERIFY_IDENTITY", "--ssl-ca=" + str(ca), "--connect-timeout=10",
        "--batch", "--silent", "--skip-column-names", "--unbuffered", "--raw", "--binary-mode",
        "--skip-reconnect", "--local-infile=0", "--init-command=", "--default-character-set=utf8mb4"]
    return command, user


def require_ddl_grants(grants):
    """Check direct privilege lists and scopes, never account/role names."""
    required = {"CREATE", "ALTER", "TRIGGER"}
    granted = {name: set() for name in DATABASES}
    for line in grants.splitlines():
        match = re.match(r"^GRANT (.+?) ON (.+?) TO ", line, re.I)
        if not match:
            continue
        permissions = {item.strip().upper() for item in match.group(1).split(",")}
        if "ALL PRIVILEGES" in permissions:
            permissions |= required
        scope = match.group(2).replace("`", "").replace("\\_", "_")
        for name in DATABASES:
            if scope in {"*.*", name + ".*"}:
                granted[name] |= permissions
    if any(not required <= permissions for permissions in granted.values()):
        raise DatabaseError("Migration identity requires direct DDL and trigger grants on every owned schema")


class MysqlSession:
    """One TCP/TLS session keeps the advisory lock across implicit-commit DDL."""
    def __init__(self, command, user, factory=subprocess.Popen):
        self.command, self.user, self.factory = command, user, factory
        self.process = None; self.lines = queue.Queue(maxsize=10000); self.query_count = 0

    def __enter__(self):
        env = {k: v for k, v in os.environ.items() if k not in {"MYSQL_PWD", "MYSQL_HOST", "MYSQL_TCP_PORT"}}
        # MySQL 8.0 lacks --no-login-paths. Direct it away from an operator's
        # personal encrypted login file instead of silently inheriting that identity.
        defaults = Path(self.command[1].split("=", 1)[1])
        env["MYSQL_TEST_LOGIN_FILE"] = str(defaults.parent / (".disabled-login-paths-" + secrets.token_hex(24)))
        env["MYSQL_HISTFILE"] = os.devnull
        self.process = self.factory(self.command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL, env=env, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        def read():
            try:
                for line in iter(self.process.stdout.readline, b""):
                    self.lines.put(line.decode("utf8", errors="strict").rstrip("\r\n"))
            finally:
                self.lines.put(None)
        threading.Thread(target=read, name="managed-mysql-results", daemon=True).start()
        try:
            version = self.mysql("SELECT VERSION()")
            if not re.match(r"8\.[0-9]+\.", version):
                raise DatabaseError("This deployment requires a MySQL 8 server")
            current = self.mysql("SELECT CURRENT_USER()")
            if current.split("@", 1)[0] != self.user:
                raise DatabaseError("The connected database identity differs from the migration identity")
            grants = self.mysql("SHOW GRANTS FOR CURRENT_USER()")
            require_ddl_grants(grants)
            if self.mysql("SHOW SESSION STATUS LIKE 'Ssl_cipher'").split("\t")[-1] in {"", "NULL"}:
                raise DatabaseError("The MySQL session is not encrypted")
            self.mysql("SET SESSION time_zone='+08:00'")
        except Exception:
            self.__exit__(); raise
        return self

    def mysql(self, sql, database=None):
        if database is not None and database not in DATABASES:
            raise DatabaseError("Database target is outside the owned schemas")
        if self.process.poll() is not None:
            raise DatabaseError("Managed MySQL session ended; no automatic retry")
        marker = "__TIANJI_RESULT_" + secrets.token_hex(24) + "__"
        prefix = "USE `" + database + "`;\n" if database else ""
        self.query_count += 1
        try:
            self.process.stdin.write((prefix + sql.rstrip().rstrip(";") + ";\nSELECT '" + marker + "';\n").encode("utf8")); self.process.stdin.flush()
            rows = []
            while True:
                line = self.lines.get(timeout=300)
                if line is None:
                    raise DatabaseError("Managed MySQL rejected a statement; inspect private server diagnostics")
                if line == marker:
                    return "\n".join(rows).strip()
                if len(rows) >= 5000:
                    raise DatabaseError("Database result exceeded the migration output limit")
                rows.append(line)
        except (OSError, queue.Empty) as error:
            raise DatabaseError("Managed MySQL request failed or timed out; no automatic retry") from None

    def __exit__(self, *args):
        if self.process:
            try:
                self.process.stdin.close(); self.process.wait(timeout=5)
            except (OSError, subprocess.TimeoutExpired):
                self.process.kill(); self.process.wait(timeout=5)


@contextmanager
def database_lock(adapter):
    if adapter.mysql("SELECT GET_LOCK('" + LOCK + "',10)") != "1":
        raise DatabaseError("Another database deployment holds the migration lock")
    try:
        yield
    finally:
        adapter.mysql("SELECT RELEASE_LOCK('" + LOCK + "')")


def sources(root=ROOT):
    paths = [root / "deploy/production/database.py", root / "deploy/acceptance/migrate.py", root / "deploy/compact/schema.sql", root / "deploy/compact/catalog.sql", *sorted((root / "deploy/acceptance/migrations").glob("*/*.sql"))]
    manifest = {path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}
    return {"files": manifest, "sha256": hashlib.sha256(json.dumps(manifest, sort_keys=True).encode()).hexdigest()}


def initialization_sql(root=ROOT):
    schema = (root / "deploy/compact/schema.sql").read_text(encoding="utf8")
    catalog = (root / "deploy/compact/catalog.sql").read_text(encoding="utf8")
    databases = set(re.findall(r"^CREATE DATABASE .*?`(tj_[a-z]+)`", schema, re.M))
    if databases != set(DATABASES) or re.search(r"^(INSERT|REPLACE|UPDATE|DELETE|LOAD|CALL)\b", schema, re.M | re.I):
        raise DatabaseError("Public baseline is not an empty owned-schema bootstrap")
    current = None
    allowed = {"role", "menu", "privilege", "role_privilege", "role_menu", "schema_migration"}
    for line in catalog.splitlines():
        use = re.match(r"USE `(tj_[a-z]+)`;", line)
        if use:
            current = use.group(1)
            if current not in DATABASES:
                raise DatabaseError("Catalog database is outside the owned schemas")
        insert = re.match(r"INSERT INTO `([a-z_]+)`", line)
        if insert and (insert.group(1) not in allowed or insert.group(1) != "schema_migration" and current != "tj_auth"):
            raise DatabaseError("Catalog contains business, demo or user records")
        if re.match(r"(?:UPDATE|DELETE|REPLACE|CREATE|DROP|TRUNCATE|LOAD|CALL|GRANT)\b", line, re.I):
            raise DatabaseError("Catalog contains unexpected executable statements")
    # Even though init is empty-only, never send DROP or suppress an occupied DB error.
    schema = re.sub(r"^DROP TABLE IF EXISTS .*?;\s*$", "", schema, flags=re.M)
    schema = schema.replace("/*!32312 IF NOT EXISTS*/ ", "")
    schema = re.sub(r"/\*!80016 DEFAULT ENCRYPTION='N' \*/", "", schema)
    catalog = re.sub(r"^(?:LOCK TABLES .*?|UNLOCK TABLES);\s*$", "", catalog, flags=re.M)
    return schema + "\n" + catalog


def forward_migrations(adapter, state_directory):
    # Reuse the existing checksum runner AND its duplicate preflight/main flow.
    sys.path.insert(0, str(ACCEPTANCE))
    spec = importlib.util.spec_from_file_location("tianji_managed_forward_migrations", ACCEPTANCE / "migrate.py")
    engine = importlib.util.module_from_spec(spec); spec.loader.exec_module(engine)
    engine.mysql = adapter.mysql; engine.LOCAL = Path(state_directory); engine.BASE = ACCEPTANCE; engine.DATABASES = list(DATABASES)
    with redirect_stdout(io.StringIO()):
        engine.main()
    result = engine.LOCAL / "migrations.json"
    protect(result)
    return json.loads(result.read_text(encoding="utf8"))


def initialize(adapter, state_directory, migrate=forward_migrations):
    occupied = adapter.mysql("SELECT SCHEMA_NAME FROM information_schema.schemata WHERE LEFT(SCHEMA_NAME,3)='tj_'")
    if occupied:
        raise DatabaseError("Initialization refuses every existing owned schema; use migrate")
    adapter.mysql(initialization_sql())
    applied = migrate(adapter, state_directory)
    if adapter.mysql("SELECT COUNT(*) FROM `user`", "tj_user") != "0":
        raise DatabaseError("Initialization unexpectedly produced user records")
    return applied


def admin_spec(path, namespace):
    value = json.loads(private_file(path).read_text(encoding="utf8"))
    if set(value) != {"namespace", "username", "phone", "name", "role"} or value["namespace"] != namespace or value["role"] != "admin":
        raise DatabaseError("Administrator specification must explicitly match namespace and admin role")
    prefix = namespace.replace("-", "_") + "_"
    if not re.fullmatch(r"[a-z][a-z0-9_]{2,31}", value["username"]) or not value["username"].startswith(prefix):
        raise DatabaseError("Administrator username must start with namespace_ and fit 32 characters")
    if not re.fullmatch(r"1[3-9][0-9]{9}", value["phone"]) or not isinstance(value["name"], str) or not 1 <= len(value["name"]) <= 64:
        raise DatabaseError("Administrator phone or display name is invalid")
    return value


def sql_text(value):
    return "CONVERT(0x" + str(value).encode("utf8").hex() + " USING utf8mb4)"


def bootstrap_admin(adapter, spec, receipt):
    import bcrypt
    if adapter.mysql("SELECT COUNT(*) FROM role WHERE id=1 AND code='admin' AND deleted=0", "tj_auth") != "1":
        raise DatabaseError("Administrator role must exist before bootstrap")
    if adapter.mysql("SELECT COUNT(*) FROM `user` u JOIN user_detail d ON d.id=u.id WHERE u.status=1 AND d.role_id=1", "tj_user") != "0":
        raise DatabaseError("An active administrator already exists; bootstrap never resets accounts")
    if adapter.mysql("SELECT COUNT(*) FROM `user` WHERE username=" + sql_text(spec["username"]) + " OR cell_phone=" + sql_text(spec["phone"]), "tj_user") != "0":
        raise DatabaseError("Bootstrap administrator identity is occupied")
    password = secrets.token_urlsafe(32)
    digest = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=12)).decode()
    uid = secrets.randbelow(2**63 - 1) + 1
    value = {"status": "PREPARED_NOT_APPLIED", "namespace": spec["namespace"], "username": spec["username"], "id": str(uid),
             "temporaryPassword": password, "expiresAfterHours": 24, "mustChangePassword": True,
             "next": "Set initial password through the admin audience; enroll and confirm MFA before business login."}
    new_json(receipt, value)  # Durable protected credential exists before the database transaction.
    sql = "START TRANSACTION; INSERT INTO `user`(id,username,cell_phone,password,type,status,must_change_password,temporary_password_expires_at) VALUES(" + ",".join([str(uid), sql_text(spec["username"]), sql_text(spec["phone"]), sql_text(digest), "1", "1", "1", "DATE_ADD(NOW(3),INTERVAL 24 HOUR)"]) + "); INSERT INTO user_detail(id,type,name,role_id) VALUES(" + str(uid) + ",1," + sql_text(spec["name"]) + ",1); COMMIT;"
    adapter.mysql(sql, "tj_user")
    value["status"] = "APPLIED"; overwrite_private_json(receipt, value)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["init", "migrate", "bootstrap-admin"])
    parser.add_argument("--defaults-extra-file", required=True, type=Path)
    parser.add_argument("--expected-host", required=True)
    parser.add_argument("--namespace", required=True)
    parser.add_argument("--mysql", default="mysql")
    parser.add_argument("--result", required=True, type=Path)
    parser.add_argument("--admin-spec", type=Path)
    parser.add_argument("--receipt", type=Path)
    args = parser.parse_args(argv)
    if not re.fullmatch(r"[a-z][a-z0-9-]{1,50}", args.namespace):
        raise DatabaseError("An explicit production namespace is required")
    if args.mode == "bootstrap-admin" and (not args.admin_spec or not args.receipt):
        raise DatabaseError("Bootstrap requires a protected administrator specification and new receipt path")
    command, user = client_options(args.defaults_extra_file, args.expected_host, args.mysql)
    if args.result.exists():
        raise DatabaseError("Refusing to replace an existing deployment result")
    # The state report has no SQL or credentials and shares the protected result directory.
    state = args.result.parent / (".database-state-" + secrets.token_hex(12))
    state.mkdir(mode=0o700)
    protect(state)
    result = {"mode": args.mode, "namespace": args.namespace, "status": "RUNNING", "sources": sources(),
              "tlsMode": "VERIFY_IDENTITY", "externalTlsAcceptance": "NOT_PROVEN", "queries": 0}
    new_json(args.result, result)
    try:
        with MysqlSession(command, user) as session:
            with database_lock(session):
                if args.mode == "init":
                    applied = initialize(session, state)
                elif args.mode == "migrate":
                    applied = forward_migrations(session, state)
                else:
                    bootstrap_admin(session, admin_spec(args.admin_spec, args.namespace), args.receipt); applied = []
            if sources() != result["sources"]:
                raise DatabaseError("Database source artifacts changed during execution; inspect before retrying")
            result.update(status="PASSED", queries=session.query_count, appliedMigrations=applied)
    except Exception:
        result.update(status="FAILED", queries=locals().get("session").query_count if "session" in locals() else 0)
        overwrite_private_json(args.result, result)
        raise DatabaseError("Production database operation failed; protected result retained; no automatic rollback or replay") from None
    overwrite_private_json(args.result, result)
    print("Production database operation completed; protected result written. No availability or supplier acceptance implied.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("Database command refused: " + (str(error) if isinstance(error, DatabaseError) else "invalid protected input or local file state"), file=sys.stderr)
        raise SystemExit(1)
