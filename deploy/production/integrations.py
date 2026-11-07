"""Validate and initialize the supported supplier mappings without demo records.

Credentials stay in the existing protected MySQL option file. This command never
sends a message, creates a payment, changes a cloud resource, or overwrites a
conflicting mapping. Run after database.py init/migrate and bootstrap-admin.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import secrets
import sys

import database


class IntegrationError(ValueError):
    pass


def text(value, field, maximum):
    if (
        not isinstance(value, str)
        or not value.strip()
        or len(value) > maximum
        or any(ord(c) < 32 or ord(c) > 65535 for c in value)
        or re.search(r"REPLACE|REQUIRED", value)
    ):
        raise IntegrationError("Invalid integration field: " + field)
    return value


def validate(value):
    if not isinstance(value, dict) or set(value) != {
        "namespace",
        "operator_id",
        "alipay",
        "sms",
    }:
        raise IntegrationError("Supply namespace, operator_id, alipay and sms mappings")
    namespace = value["namespace"]
    if not isinstance(namespace, str) or not re.fullmatch(
        r"[a-z][a-z0-9-]{1,50}", namespace
    ):
        raise IntegrationError("Invalid production namespace")
    operator = value["operator_id"]
    if (
        isinstance(operator, bool)
        or not isinstance(operator, int)
        or not 0 < operator < 2**63
    ):
        raise IntegrationError(
            "operator_id must identify the existing active administrator"
        )
    pay, sms = value["alipay"], value["sms"]
    if not isinstance(pay, dict) or set(pay) != {"name", "priority", "icon"}:
        raise IntegrationError("alipay requires name, priority and icon")
    text(pay["name"], "alipay.name", 50)
    text(pay["icon"], "alipay.icon", 255)
    if type(pay["priority"]) is not int or not 0 <= pay["priority"] <= 1000:
        raise IntegrationError("Invalid alipay.priority")
    if not isinstance(sms, dict) or set(sms) != {
        "sign_name",
        "template_code",
        "content",
    }:
        raise IntegrationError("sms requires sign_name, template_code and content")
    text(sms["sign_name"], "sms.sign_name", 50)
    text(sms["template_code"], "sms.template_code", 50)
    text(sms["content"], "sms.content", 255)
    if not re.fullmatch(r"SMS_[0-9A-Za-z]+", sms["template_code"]):
        raise IntegrationError("Use an approved Alibaba SMS template code")
    parameters = re.findall(r"\$\{([^}]+)\}", sms["content"])
    if parameters != ["code"] or sms["content"].count("${") != 1:
        raise IntegrationError(
            "Verification template must contain exactly one ${code} parameter"
        )
    return value


def fingerprint(value):
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, ensure_ascii=False).encode()
    ).hexdigest()


def ensure_row(session, schema, table, key, fields, operator, *, generated_id=False):
    """An advisory lock covers absent keys; a transaction covers all four rows."""
    quote = database.sql_text
    condition = " AND ".join("`" + k + "`=" + quote(v) for k, v in key.items())
    columns = ["id", *fields]
    pairs = ",".join(
        quote(k)
        + ","
        + (
            "CAST(`" + k + "` AS UNSIGNED)"
            if k == "id" or type(fields[k]) is int
            else "`" + k + "`"
        )
        for k in columns
    )
    raw = session.mysql(
        "SELECT JSON_OBJECT("
        + pairs
        + ") FROM `"
        + table
        + "` WHERE "
        + condition
        + " FOR UPDATE",
        schema,
    )
    rows = [json.loads(line) for line in raw.splitlines() if line]
    if len(rows) > 1:
        raise IntegrationError(
            schema
            + "."
            + table
            + ": duplicate business code; reconcile before retrying"
        )
    if rows:
        if any(rows[0].get(k) != v for k, v in fields.items()):
            raise IntegrationError(
                schema
                + "."
                + table
                + ": existing mapping conflicts; no update performed"
            )
        return rows[0]["id"], "UNCHANGED"
    values = {**key, **fields, "creater": operator, "updater": operator}
    if generated_id:
        values["id"] = secrets.randbelow(2**63 - 1) + 1
    session.mysql(
        "INSERT INTO `"
        + table
        + "`("
        + ",".join("`" + k + "`" for k in values)
        + ") VALUES("
        + ",".join(str(v) if type(v) is int else quote(v) for v in values.values())
        + ")",
        schema,
    )
    row_id = values.get("id") or int(session.mysql("SELECT LAST_INSERT_ID()", schema))
    return row_id, "CREATED"


def configure(session, spec):
    validate(spec)
    operator = spec["operator_id"]
    if (
        session.mysql(
            "SELECT COUNT(*) FROM `user` u JOIN user_detail d ON d.id=u.id "
            "WHERE u.id="
            + str(operator)
            + " AND u.type=1 AND u.status=1 AND d.role_id=1",
            "tj_user",
        )
        != "1"
    ):
        raise IntegrationError("operator_id is not an active administrator")
    changes = {}
    session.mysql("START TRANSACTION")
    try:
        pay = spec["alipay"]
        _, changes["alipay"] = ensure_row(
            session,
            "tj_pay",
            "pay_channel",
            {"channel_code": "aliPay"},
            {
                "name": pay["name"],
                "channel_priority": pay["priority"],
                "channel_icon": pay["icon"],
                "status": 1,
            },
            operator,
        )
        _, changes["sms_platform"] = ensure_row(
            session,
            "tj_message",
            "sms_third_platform",
            {"code": "aliYun"},
            {"name": "阿里云短信", "priority": 0, "status": 1},
            operator,
        )
        content = spec["sms"]["content"]
        template, changes["notice_template"] = ensure_row(
            session,
            "tj_message",
            "notice_template",
            {"code": "VERIFY_CODE"},
            {
                "name": "短信验证码",
                "type": 0,
                "status": 1,
                "content": content,
                "is_sms_template": 1,
            },
            operator,
            generated_id=True,
        )
        _, changes["supplier_template"] = ensure_row(
            session,
            "tj_message",
            "message_template",
            {"template_id": template, "platform_code": "aliYun"},
            {
                "name": "阿里云验证码",
                "sign_name": spec["sms"]["sign_name"],
                "third_template_code": spec["sms"]["template_code"],
                "content": content,
                "status": 1,
            },
            operator,
            generated_id=True,
        )
        session.mysql("COMMIT")
    except Exception:
        session.mysql("ROLLBACK")
        raise
    return changes


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["check", "apply"])
    parser.add_argument("--spec", type=Path, required=True)
    parser.add_argument("--defaults-extra-file", type=Path)
    parser.add_argument("--expected-host")
    parser.add_argument("--mysql", default="mysql")
    parser.add_argument("--result", type=Path, required=True)
    args = parser.parse_args(argv)
    spec = validate(json.loads(args.spec.read_text(encoding="utf8")))
    result = {
        "schema": "tianji-integrations-v1",
        "namespace": spec["namespace"],
        "spec_sha256": fingerprint(spec),
        "observed_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "status": "CONFIGURATION_VALID_NOT_APPLIED",
        "supplier_acceptance": "NOT_RUN",
    }
    if args.mode == "apply":
        if not args.defaults_extra_file or not args.expected_host:
            raise IntegrationError(
                "apply requires the protected MySQL option file and expected provider host"
            )
        command, user = database.client_options(
            args.defaults_extra_file, args.expected_host, args.mysql
        )
        if args.result.exists():
            raise IntegrationError("Result path already exists")
        database.new_json(args.result, {**result, "status": "RUNNING"})
        try:
            with database.MysqlSession(command, user) as session:
                with database.database_lock(session):
                    result["mappings"] = configure(session, spec)
            result["status"] = "APPLIED"
        except Exception:
            database.overwrite_private_json(args.result, {**result, "status": "FAILED"})
            raise
        database.overwrite_private_json(args.result, result)
    else:
        database.new_json(args.result, result)
    print(result["status"] + "; no SMS or payment request was sent")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (
        IntegrationError,
        database.DatabaseError,
        OSError,
        ValueError,
        KeyError,
        TypeError,
    ):
        print(
            "Integration configuration failed; inspect protected inputs and result; existing mappings are not overwritten",
            file=sys.stderr,
        )
        raise SystemExit(2)
