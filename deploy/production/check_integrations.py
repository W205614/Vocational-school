"""Check supplier configuration statically, optionally observing TLS connectivity.

Connectivity is DNS/TCP/TLS only and is never called SMS delivery, payment
acceptance, bucket authorization or database failover verification.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
from pathlib import Path
import socket
import ssl
import sys

import database
import integrations
import render
import yaml
from runtime_checks import candidate


def inspect(settings, spec, binding, *, connect=False, connector=None):
    settings = render.validate(settings)
    integrations.validate(spec)
    if settings["namespace"] != spec["namespace"] or binding[:3] != (
        settings["commit"],
        binding[1],
        settings["namespace"],
    ):
        raise ValueError(
            "Settings, mappings and package do not name the same candidate"
        )
    _, secrets = render.render(settings)
    required = {
        "TJ_PAY_TJ_PAY_ALI_APPID",
        "TJ_PAY_TJ_PAY_ALI_MERCHANTPRIVATEKEY",
        "TJ_PAY_TJ_PAY_ALI_PUBLICKEY",
        "ALIPAY_SELLER_ID",
    }
    if not required <= set(secrets["commerce-credentials"]):
        raise ValueError("Missing payment Secret contract")
    if not {
        "TJ_MESSAGE_TJ_SMS_ALI_ACCESSID",
        "TJ_MESSAGE_TJ_SMS_ALI_ACCESSSECRET",
    } <= set(secrets["support-credentials"]):
        raise ValueError("Missing SMS Secret contract")
    result = {
        "schema": "tianji-integration-preflight-v1",
        "commit": binding[0],
        "manifest_sha256": binding[1],
        "namespace": binding[2],
        "observed_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "status": "CONFIGURATION_VALID",
        "mappings_sha256": integrations.fingerprint(spec),
        "required_secret_keys": secrets,
        "supplier_acceptance": "NOT_RUN",
        "connections": [],
    }
    if not connect:
        return result
    connector = connector or tls_connect
    endpoints = [(host, 443) for host in settings["provider_hosts"]]
    endpoints += [
        (settings["managed"][kind]["host"], settings["managed"][kind]["port"])
        for kind in ("redis", "rabbitmq", "elasticsearch")
    ]
    endpoints += [
        (settings["public"][key], 443) for key in ("student_host", "admin_host")
    ]
    for host, port in sorted(set(endpoints)):
        try:
            connector(host, port)
            status = "TLS_CONNECTED"
        except (OSError, ValueError):
            status = "FAILED"
            result["status"] = "FAILED"
        result["connections"].append({"host": host, "port": port, "status": status})
    return result


def tls_connect(host, port):
    context = ssl.create_default_context()
    with socket.create_connection((host, port), timeout=10) as raw:
        with context.wrap_socket(raw, server_hostname=host):
            pass


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--settings", type=Path, required=True)
    parser.add_argument("--spec", type=Path, required=True)
    parser.add_argument("--package", type=Path, required=True)
    parser.add_argument("--result", type=Path, required=True)
    parser.add_argument("--connectivity", action="store_true")
    args = parser.parse_args(argv)
    settings = yaml.safe_load(args.settings.read_text(encoding="utf8"))
    resources, _ = render.render(settings)
    if resources != list(
        yaml.safe_load_all((args.package / "resources.yaml").read_text(encoding="utf8"))
    ):
        raise ValueError(
            "Rendered settings differ from the selected candidate manifest"
        )
    result = inspect(
        settings,
        json.loads(args.spec.read_text(encoding="utf8")),
        candidate(args.package),
        connect=args.connectivity,
    )
    database.new_json(args.result, result)
    print(
        "Integration preflight "
        + result["status"]
        + "; supplier acceptance was not performed"
    )
    return 1 if result["status"] == "FAILED" else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (
        OSError,
        ValueError,
        KeyError,
        TypeError,
        StopIteration,
        database.DatabaseError,
    ):
        print(
            "Integration preflight BLOCKED: invalid candidate or mappings",
            file=sys.stderr,
        )
        raise SystemExit(2)
