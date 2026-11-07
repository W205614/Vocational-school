"""Initialize or verify the actual production course index without deleting it."""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import json
from pathlib import Path
import ssl
import sys
from urllib import error, request
from urllib.parse import urlsplit

import database
from runtime_checks import NoRedirect


def mapping():
    fields = {
        key: {"type": "long"}
        for key in (
            "categoryIdLv1",
            "categoryIdLv2",
            "categoryIdLv3",
            "teacher",
            "sections",
            "sold",
            "price",
            "score",
            "salesVersion",
            "metadataVersion",
        )
    }
    fields.update(
        available={"type": "boolean"},
        id={"type": "keyword"},
        name={"type": "text", "analyzer": "standard"},
        free={"type": "boolean"},
        type={"type": "integer"},
        coverUrl={"type": "keyword"},
    )
    for key in ("publishTime", "updateTime"):
        fields[key] = {
            "type": "date",
            "format": "strict_date_optional_time||yyyy-MM-dd HH:mm:ss",
        }
    return fields


def check_properties(actual):
    for key, expected in mapping().items():
        if key not in actual or any(
            actual[key].get(name) != value for name, value in expected.items()
        ):
            raise ValueError("Existing course index has an incompatible field: " + key)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["check", "init"])
    parser.add_argument("--credentials", type=Path, required=True)
    parser.add_argument("--expected-host", required=True)
    parser.add_argument("--result", type=Path, required=True)
    args = parser.parse_args(argv)
    credentials = json.loads(
        database.private_file(args.credentials).read_text(encoding="utf8")
    )
    endpoint = urlsplit(credentials["endpoint"])
    if (
        endpoint.scheme != "https"
        or endpoint.hostname != args.expected_host
        or endpoint.username
        or endpoint.password
        or endpoint.path not in {"", "/"}
        or endpoint.query
        or endpoint.fragment
    ):
        raise ValueError("Search endpoint must be the expected HTTPS provider origin")
    username, password = credentials["username"], credentials["password"]
    if not username or not password or any(c in username for c in ":\r\n"):
        raise ValueError("A dedicated search identity is required")
    context = ssl.create_default_context(cafile=credentials.get("ca_file"))
    opener = request.build_opener(NoRedirect(), request.HTTPSHandler(context=context))
    auth = base64.b64encode((username + ":" + password).encode()).decode("ascii")
    headers = {"Authorization": "Basic " + auth, "Content-Type": "application/json"}
    base = credentials["endpoint"].rstrip("/") + "/course"
    database.new_json(
        args.result,
        {
            "schema": "zhixuetang-production-search-v1",
            "status": "RUNNING",
            "host": args.expected_host,
        },
    )
    try:
        with opener.open(
            request.Request(base + "/_mapping", headers=headers), timeout=15
        ) as response:
            observed = json.load(response)
        check_properties(observed["course"]["mappings"]["properties"])
        status = "UNCHANGED"
    except error.HTTPError as failure:
        if failure.code != 404 or args.mode != "init":
            raise
        payload = {
            "settings": {"number_of_shards": 1, "number_of_replicas": 1},
            "mappings": {"properties": mapping()},
        }
        with opener.open(
            request.Request(
                base, data=json.dumps(payload).encode(), method="PUT", headers=headers
            ),
            timeout=15,
        ) as response:
            if json.load(response).get("acknowledged") is not True:
                raise ValueError("Search index creation was not acknowledged")
        status = "CREATED"
    database.overwrite_private_json(
        args.result,
        {
            "schema": "zhixuetang-production-search-v1",
            "status": status,
            "observed_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "host": args.expected_host,
            "index": "course",
            "capacity_proven": False,
        },
    )
    print("Production course index " + status + "; no existing index was overwritten")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError, database.DatabaseError):
        print(
            "Search initialization/check failed; existing index retained",
            file=sys.stderr,
        )
        raise SystemExit(2)
