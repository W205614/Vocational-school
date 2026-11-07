"""Execute configured HTTPS business checks against an isolated candidate.

No shell snippets or assertions supplied as executable code are accepted. A
measurement is derived from actual statuses and field comparisons; this runner
does not produce capacity, cloud failover or monthly-availability evidence.
"""

from __future__ import annotations

import argparse
import datetime as dt
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import ssl
import sys
import time
from urllib import error, request
from urllib.parse import urlsplit

import database
import yaml

CHECKS = {
    "payments": {
        "order_created",
        "payment_confirmed",
        "refund_confirmed",
        "amount_consistent",
        "entitlement_revoked",
    },
    "notifications": {"supplier_delivered"},
    "privacy": {
        "export_completed",
        "download_authorized",
        "cleanup_approved",
        "retention_preserved",
    },
    "media_revocation": {"revoked"},
    "identity_mfa": {"mfa_rejected", "mfa_accepted", "session_revoked"},
}
NAME = re.compile(r"[a-z][a-z0-9_]{0,49}")
PLACEHOLDER = re.compile(r"\$\{([a-z][a-z0-9_.]*)\}")


class CheckError(ValueError):
    pass


@dataclass(frozen=True)
class HttpResult:
    status: int
    body: bytes
    sha256: str
    size: int


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def field(value, pointer):
    if not isinstance(pointer, str) or not pointer.startswith("/"):
        raise CheckError("Assertions and bindings use JSON Pointer paths")
    for part in pointer[1:].split("/"):
        part = part.replace("~1", "/").replace("~0", "~")
        value = value[int(part)] if isinstance(value, list) else value[part]
    return value


def substitute(value, bindings):
    if isinstance(value, dict):
        return {key: substitute(item, bindings) for key, item in value.items()}
    if isinstance(value, list):
        return [substitute(item, bindings) for item in value]
    if not isinstance(value, str):
        return value
    exact = PLACEHOLDER.fullmatch(value)
    if exact:
        return bindings[exact.group(1)]
    return PLACEHOLDER.sub(lambda match: str(bindings[match.group(1)]), value)


def candidate(package):
    release = json.loads((package / "release.json").read_text(encoding="utf8"))
    raw = (package / "resources.yaml").read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != release["manifestSha256"] or not re.fullmatch(
        r"[a-f0-9]{40}", release["commit"]
    ):
        raise CheckError("Candidate manifest is not bound to the release source")
    docs = list(yaml.safe_load_all(raw))
    namespace = next(
        doc["metadata"]["name"] for doc in docs if doc["kind"] == "Namespace"
    )
    hosts = {
        host
        for doc in docs
        if doc["kind"] == "VirtualService"
        for host in doc["spec"]["hosts"]
    }
    return release["commit"], digest, namespace, hosts


def validate(scenario, namespace, hosts, allow_writes=False):
    if not isinstance(scenario, dict):
        raise CheckError("Runtime scenario must be a JSON object")
    suite = scenario.get("suite")
    if scenario.get("schema") != "zhixuetang-runtime-checks-v1" or suite not in CHECKS:
        raise CheckError("Unknown runtime scenario schema or functional suite")
    if (
        scenario.get("environment") != "isolated-production-candidate"
        or scenario.get("namespace") != namespace
    ):
        raise CheckError(
            "Scenario must explicitly identify the isolated candidate namespace"
        )
    identity = scenario.get("test_identity")
    if not isinstance(identity, str) or not identity.strip():
        raise CheckError(
            "An explicitly provisioned synthetic test identity is required"
        )
    origin = urlsplit(scenario.get("base_url", ""))
    if (
        origin.scheme != "https"
        or origin.hostname not in hosts
        or origin.username
        or origin.password
        or origin.query
        or origin.fragment
        or origin.path not in {"", "/"}
    ):
        raise CheckError(
            "base_url must be a TLS origin from the candidate VirtualService"
        )
    steps = scenario.get("steps", [])
    if not isinstance(steps, list) or not 1 <= len(steps) <= 40:
        raise CheckError("Supply 1..40 concrete runtime steps")
    names = set()
    for step in steps:
        if not isinstance(step, dict):
            raise CheckError("Every runtime step must be an object")
        name = step.get("name", "")
        if not NAME.fullmatch(name) or name in names:
            raise CheckError("Step names must be distinct safe identifiers")
        names.add(name)
        method = step.get("method", "GET")
        if method not in {"GET", "HEAD", "POST", "PUT", "DELETE"}:
            raise CheckError("Unsupported HTTP method")
        if method not in {"GET", "HEAD"} and not allow_writes:
            raise CheckError("Mutating candidate requests require --allow-writes")
        if (
            type(step.get("expected_status")) is not int
            or not 100 <= step["expected_status"] <= 599
        ):
            raise CheckError("Every step requires an exact expected_status")
        if (
            not isinstance(step.get("path"), str)
            or not (
                step["path"].startswith("/api/v2/")
                or PLACEHOLDER.fullmatch(step["path"])
            )
            or step["path"].startswith("//")
        ):
            raise CheckError("Steps must target explicit /api/v2/ paths")
        expiry = step.get("require_unexpired")
        if expiry is not None and not (
            type(expiry) is int
            or isinstance(expiry, str)
            and PLACEHOLDER.fullmatch(expiry)
        ):
            raise CheckError(
                "require_unexpired must be an epoch-millisecond value or response binding"
            )
        if (
            not step.get("assertions")
            and not step.get("expected_sha256")
            and step["expected_status"] < 400
            and method != "HEAD"
        ):
            raise CheckError(
                "Successful business checks require response field assertions or a binary SHA-256"
            )
        expected_hash = step.get("expected_sha256")
        if expected_hash is not None and (
            not isinstance(expected_hash, str)
            or not (
                re.fullmatch(r"[0-9a-f]{64}", expected_hash)
                or PLACEHOLDER.fullmatch(expected_hash)
            )
        ):
            raise CheckError(
                "expected_sha256 must be an exact digest or response binding"
            )
        if expected_hash is not None and (step.get("assertions") or step.get("save")):
            raise CheckError("Binary hashing does not parse or bind response JSON")
        assertions = step.get("assertions", [])
        if not isinstance(assertions, list) or any(
            not isinstance(item, dict)
            or set(item) != {"path", "equals"}
            or not isinstance(item["path"], str)
            or not item["path"].startswith("/")
            for item in assertions
        ):
            raise CheckError(
                "Assertions require an explicit JSON Pointer path and equals value"
            )
        bindings = step.get("save", {})
        if not isinstance(bindings, dict) or any(
            not re.fullmatch(r"[a-z][a-z0-9_.]*", key)
            or not isinstance(pointer, str)
            or not pointer.startswith("/")
            for key, pointer in bindings.items()
        ):
            raise CheckError(
                "Response bindings require safe names and JSON Pointer paths"
            )
        if step.get("check") not in CHECKS[suite]:
            raise CheckError("Each step must contribute a named check for its suite")
        if step.get("observation", "http") not in {
            "http",
            "business_query",
            "provider_receipt",
        }:
            raise CheckError("Unsupported observation source")
        if not isinstance(step.get("headers", {}), dict) or set(
            step.get("headers", {})
        ) - {"Range"}:
            raise CheckError("Only an explicit Range request header may be configured")
        if any(
            not isinstance(value, str)
            or len(value) > 200
            or "\r" in value
            or "\n" in value
            for value in step.get("headers", {}).values()
        ):
            raise CheckError("Invalid Range request header")
        attempts = step.get("attempts", 1)
        if (
            type(attempts) is not int
            or not 1 <= attempts <= 300
            or (attempts > 1 and method not in {"GET", "HEAD"})
        ):
            raise CheckError("Only read requests may poll, at most 300 attempts")
    if {step["check"] for step in steps} != CHECKS[suite]:
        raise CheckError("Scenario does not cover every required suite check")
    if suite == "notifications" and not re.fullmatch(
        r"1[3-9][0-9]{9}", scenario.get("test_recipient", "")
    ):
        raise CheckError("Notification checks require an explicit test_recipient")
    if suite == "notifications" and not any(
        step.get("observation") == "provider_receipt"
        and any(a.get("equals") == "DELIVERED" for a in step.get("assertions", []))
        for step in steps
    ):
        raise CheckError(
            "Notification proof must query an actual DELIVERED supplier receipt"
        )
    return scenario


def http(opener, url, method, headers, body):
    req = request.Request(
        url,
        data=None if body is None else json.dumps(body).encode(),
        headers=headers,
        method=method,
    )
    try:
        response = opener.open(req, timeout=15)
    except error.HTTPError as failure:
        response = failure
    with response:
        binary = headers.get("Accept") == "application/octet-stream"
        maximum = 1073741824 if binary else 2 * 1024 * 1024
        digest, chunks, size = hashlib.sha256(), [], 0
        deadline = time.monotonic() + 120
        while True:
            chunk = response.read(65536)
            if not chunk:
                break
            size += len(chunk)
            if size > maximum or time.monotonic() > deadline:
                raise CheckError(
                    "Runtime response exceeds the bounded byte/time budget"
                )
            digest.update(chunk)
            if not binary:
                chunks.append(chunk)
        return HttpResult(response.status, b"".join(chunks), digest.hexdigest(), size)


def execute(scenario, credentials, binding, *, caller=None, pause=time.sleep):
    started = dt.datetime.now(dt.timezone.utc)
    opener = request.build_opener(
        NoRedirect(), request.HTTPSHandler(context=ssl.create_default_context())
    )
    caller = caller or (lambda *args: http(opener, *args))
    bindings = {"credentials." + key: value for key, value in credentials.items()}
    observations = []
    outcomes = {name: True for name in CHECKS[scenario["suite"]]}
    for step in scenario["steps"]:
        name, check = step["name"], step["check"]
        try:
            path = substitute(step["path"], bindings)
            expiry = substitute(step.get("require_unexpired"), bindings)
            if expiry is not None and (
                type(expiry) not in {int, float} or expiry <= time.time() * 1000
            ):
                raise CheckError(
                    "The original playback ticket expired; revocation cannot be proven"
                )
            parsed = urlsplit(path)
            if (
                parsed.scheme
                or parsed.netloc
                or not parsed.path.startswith("/api/v2/")
                or parsed.fragment
            ):
                raise CheckError("Invalid substituted request path")
            headers = {"Accept": "application/json", "Content-Type": "application/json"}
            expected_hash = substitute(step.get("expected_sha256"), bindings)
            if expected_hash is not None:
                if not isinstance(expected_hash, str) or not re.fullmatch(
                    r"[0-9a-f]{64}", expected_hash
                ):
                    raise CheckError("Binary response digest binding is invalid")
                headers["Accept"] = "application/octet-stream"
            headers.update(substitute(step.get("headers", {}), bindings))
            if step.get("auth"):
                token = substitute(step["auth"], bindings)
                if token == step["auth"]:
                    token = credentials[token]
                if (
                    not isinstance(token, str)
                    or not token
                    or "\r" in token
                    or "\n" in token
                ):
                    raise CheckError("Invalid protected credential")
                headers["Authorization"] = "Bearer " + token
            if step.get("idempotency_key"):
                headers["Idempotency-Key"] = substitute(
                    step["idempotency_key"], bindings
                )
            passed = False
            for attempt in range(step.get("attempts", 1)):
                if expiry is not None and expiry <= time.time() * 1000:
                    raise CheckError(
                        "The original playback ticket expired during polling"
                    )
                observed = caller(
                    scenario["base_url"].rstrip("/") + path,
                    step.get("method", "GET"),
                    headers,
                    substitute(step.get("body"), bindings),
                )
                if isinstance(observed, HttpResult):
                    status, raw, response_hash = (
                        observed.status,
                        observed.body,
                        observed.sha256,
                    )
                else:
                    status, raw = observed
                    response_hash = hashlib.sha256(raw).hexdigest()
                if expiry is not None and expiry <= time.time() * 1000:
                    raise CheckError(
                        "The original ticket expired before the response; revocation is inconclusive"
                    )
                try:
                    value = (
                        json.loads(raw)
                        if raw and (step.get("assertions") or step.get("save"))
                        else None
                    )
                    comparisons = []
                    for assertion in step.get("assertions", []):
                        actual, expected = field(value, assertion["path"]), substitute(
                            assertion["equals"], bindings
                        )
                        comparisons.append(
                            actual == expected
                            and (type(actual) is bool) == (type(expected) is bool)
                        )
                    passed = (
                        status == step["expected_status"]
                        and all(comparisons)
                        and (expected_hash is None or expected_hash == response_hash)
                    )
                except (ValueError, KeyError, TypeError, IndexError):
                    passed = False
                observations.append(
                    {
                        "kind": step.get("observation", "http"),
                        "value": {
                            "step": name,
                            "attempt": attempt + 1,
                            "status": status,
                            "assertions_passed": passed,
                            "response_sha256": response_hash,
                            "observed_at": dt.datetime.now(dt.timezone.utc).isoformat(),
                        },
                    }
                )
                if passed:
                    for key, pointer in step.get("save", {}).items():
                        if not re.fullmatch(r"[a-z][a-z0-9_.]*", key):
                            raise CheckError("Invalid response binding")
                        bindings[key] = field(value, pointer)
                    break
                if attempt + 1 < step.get("attempts", 1):
                    pause(2)
            outcomes[check] &= passed
            if not passed:
                break
        except (OSError, ValueError, KeyError, TypeError, IndexError):
            outcomes[check] = False
            observations.append(
                {
                    "kind": "http",
                    "value": {"step": name, "error": "REQUEST_OR_ASSERTION_FAILED"},
                }
            )
            break
    executed = {item["value"].get("step") for item in observations}
    for step in scenario["steps"]:
        if step["name"] not in executed:
            outcomes[step["check"]] = False
    return {
        "schema": "zhixuetang-production-measurement-v1",
        "suite": scenario["suite"],
        "commit": binding[0],
        "manifest_sha256": binding[1],
        "execution": {
            "kind": "runtime-experiment",
            "environment": "isolated-production-candidate",
            "started_at": started.isoformat(),
            "completed_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "exit_code": 0 if all(outcomes.values()) else 1,
            "required_skips": 0,
        },
        "measurements": {"checks": outcomes},
        "observations": observations,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", required=True, type=Path)
    parser.add_argument("--scenario", required=True, type=Path)
    parser.add_argument("--credentials", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--allow-writes", action="store_true")
    args = parser.parse_args(argv)
    binding = candidate(args.package)
    scenario = validate(
        json.loads(database.private_file(args.scenario).read_text(encoding="utf8")),
        binding[2],
        binding[3],
        args.allow_writes,
    )
    credentials = json.loads(
        database.private_file(args.credentials).read_text(encoding="utf8")
    )
    if not isinstance(credentials, dict):
        raise CheckError("Protected credentials must be a token-alias object")
    database.new_json(
        args.output,
        {"status": "RUNNING", "commit": binding[0], "manifest_sha256": binding[1]},
    )
    report = execute(scenario, credentials, binding)
    database.overwrite_private_json(args.output, report)
    print(
        "Runtime checks "
        + ("PASSED" if report["execution"]["exit_code"] == 0 else "FAILED")
    )
    return report["execution"]["exit_code"]


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (
        CheckError,
        database.DatabaseError,
        OSError,
        ValueError,
        KeyError,
        TypeError,
        StopIteration,
    ):
        print(
            "Runtime checks BLOCKED: candidate, protected scenario or credentials are invalid; no passing report produced",
            file=sys.stderr,
        )
        raise SystemExit(2)
