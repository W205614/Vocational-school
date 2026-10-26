"""Prepare private directed-edge keyrings; never contacts or mutates a cluster.

Every command writes a NEW explicit directory. Rotation preserves both receiver
keys; retiring the previous receiver keys requires a separate explicit command.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat
import subprocess
import sys
import uuid


ROOT = Path(__file__).resolve().parents[2]
POLICIES = ROOT / "tj-common/src/main/resources/META-INF/tianji"
GROUPS = ("identity", "commerce", "education", "support")
APPLICATIONS = GROUPS + ("gateway",)
WORKLOADS = APPLICATIONS + ("observability", "media-worker")
OWNERS = {alias: group for group, aliases in {
    "identity": ("auth", "user"), "commerce": ("trade", "pay", "promotion"),
    "education": ("course", "learning", "exam", "remark"),
    "support": ("media", "search", "message", "data")}.items() for alias in aliases}
OWNER = "tianji-directed-identities-v1"
TOKEN = re.compile(r"[0-9a-f]{64}")


class IdentityError(ValueError):
    """Messages contain no credential values or input contents."""


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise IdentityError("Duplicate JSON keys are forbidden")
        result[key] = value
    return result


def _json(text):
    try:
        return json.loads(text, object_pairs_hook=_pairs)
    except json.JSONDecodeError:
        raise IdentityError("Invalid private artifact JSON") from None


def _normalized(path):
    return path.read_bytes().replace(b"\r\n", b"\n").replace(b"\r", b"\n")


def policy_fingerprint():
    entries = [hashlib.sha256(_normalized(POLICIES / name)).hexdigest() + "  " + name
               for name in ("gateway-module-policy.txt", "service-rpc-policy.txt")]
    return hashlib.sha256(("\n".join(entries) + "\n").encode()).hexdigest()


def required_edges():
    """Derive credential edges from reviewed static grants, never grant new RPC methods."""
    edges = set()
    for filename in ("service-rpc-policy.txt", "gateway-module-policy.txt"):
        for raw in (POLICIES / filename).read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("|")
            if len(parts) != 3 or parts[1] not in {"GET", "POST", "PUT", "DELETE", "PATCH"}:
                raise IdentityError("Malformed reviewed RPC inventory")
            match = re.match(r"^/_modules/([a-z]+)/", parts[2])
            if not match or match[1] not in OWNERS:
                raise IdentityError("Unknown reviewed receiver module")
            callers = parts[0].split(",")
            if len(callers) != len(set(callers)) or any(c not in APPLICATIONS for c in callers):
                raise IdentityError("Invalid or duplicate reviewed caller")
            if filename == "gateway-module-policy.txt" and callers != ["gateway"]:
                raise IdentityError("Gateway target inventory contains a peer grant")
            edges.update((caller, OWNERS[match[1]]) for caller in callers)
    # Current monitoring template scrapes these five apps, not the worker.
    # Worker has no internal RPC contract and receives an empty keyring.
    edges.update(("observability", receiver) for receiver in APPLICATIONS)
    return frozenset(edges)


def validate_keyrings(keyrings, phase, sender_index=0):
    edges = required_edges()
    if not isinstance(keyrings, dict) or set(keyrings) != set(WORKLOADS):
        raise IdentityError("Workload keyrings do not match their required identities")
    count = 2 if phase == "rotation-prepared" else 1
    seen = {}
    for receiver in WORKLOADS:
        ring = keyrings[receiver]
        incoming = {caller for caller, target in edges if target == receiver}
        outgoing = {target for caller, target in edges if caller == receiver}
        if not isinstance(ring, dict) or set(ring) != {"outbound", "inbound"}:
            raise IdentityError("A workload keyring must contain only outbound and inbound maps")
        if not isinstance(ring["outbound"], dict) or set(ring["outbound"]) != outgoing:
            raise IdentityError("Unexpected or missing outbound receiver")
        if not isinstance(ring["inbound"], dict) or set(ring["inbound"]) != incoming:
            raise IdentityError("Unexpected or missing inbound caller")
        for caller, values in ring["inbound"].items():
            if not isinstance(values, list) or len(values) != count or any(not isinstance(value, str) or not TOKEN.fullmatch(value) for value in values) or len(set(values)) != count:
                raise IdentityError("Invalid receiver rotation window")
            for value in values:
                if not isinstance(value, str) or not TOKEN.fullmatch(value):
                    raise IdentityError("An edge token must use the generated 256-bit encoding")
                edge = (caller, receiver)
                if value in seen and seen[value] != edge:
                    raise IdentityError("A token must never be shared between directed edges")
                seen[value] = edge
    for caller, receiver in edges:
        outbound = keyrings[caller]["outbound"][receiver]
        if not isinstance(outbound, str) or not TOKEN.fullmatch(outbound) or outbound != keyrings[receiver]["inbound"][caller][sender_index]:
            raise IdentityError("Caller outbound token does not match its receiver")


def _rings(tokens, previous=None, old_senders=False):
    result = {name: {"outbound": {}, "inbound": {}} for name in WORKLOADS}
    for (caller, receiver), token in sorted(tokens.items()):
        result[caller]["outbound"][receiver] = previous[(caller, receiver)] if old_senders else token
        result[receiver]["inbound"][caller] = [token] + ([previous[(caller, receiver)]] if previous else [])
    return result


def _new_tokens():
    tokens, used = {}, set()
    for edge in sorted(required_edges()):
        token = secrets.token_hex(32)
        while token in used:
            token = secrets.token_hex(32)
        tokens[edge] = token
        used.add(token)
    return tokens


def _link(path):
    return path.is_symlink() or getattr(path, "is_junction", lambda: False)()


def _private_directory(path):
    if not path.is_absolute() or path.name in {"", ".", ".."}:
        raise IdentityError("An explicit absolute new private output directory is required")
    parent = path.parent.resolve(strict=True)
    if not parent.is_dir():
        raise IdentityError("The private output parent must already exist")
    output = parent / path.name
    if output.is_relative_to(ROOT) and not output.is_relative_to(ROOT / ".local"):
        raise IdentityError("Repository output is restricted to the ignored root .local directory")
    output.mkdir(mode=0o700, exist_ok=False)
    # Establish privacy before writing any secret. Failures leave the new empty
    # directory for inspection; never recursively delete or reuse a partial output.
    if os.name == "nt":
        who = subprocess.run(["whoami", "/user", "/fo", "csv", "/nh"], capture_output=True, text=True, timeout=10)
        rows = list(csv.reader(who.stdout.splitlines()))
        sid = rows[0][-1] if who.returncode == 0 and len(rows) == 1 else ""
        if not re.fullmatch(r"S-[0-9]+(?:-[0-9]+)+", sid):
            raise IdentityError("Cannot establish private Windows ownership")
        acl = subprocess.run(["icacls", str(output), "/inheritance:r", "/grant:r",
                              "*" + sid + ":(OI)(CI)F", "*S-1-5-18:(OI)(CI)F"],
                             capture_output=True, text=True, timeout=10)
        if acl.returncode:
            raise IdentityError("Cannot establish private Windows permissions")
    return output


def _write(root, relative, content):
    path = root / relative
    directory = root
    for component in Path(relative).parts[:-1]:
        directory = directory / component
        directory.mkdir(mode=0o700, exist_ok=True)
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0), 0o600), "wb") as stream:
        stream.write(content)


def _ring_files(rings, prefix=""):
    files = {prefix + name + "/service-identities.json": (json.dumps(ring, indent=2, sort_keys=True) + "\n").encode()
             for name, ring in rings.items()}
    files.update({prefix + "observability/prometheus/" + target + "-token": (value + "\n").encode()
                  for target, value in rings["observability"]["outbound"].items()})
    return files


INSTRUCTIONS = """PRIVATE CREDENTIAL ARTIFACTS. Do not commit, attach, log, or paste token contents.
No command here applies Kubernetes resources or changes a running workload.

Initial generation: create each <workload>-service-identity Secret with its own
<workload>/service-identities.json only. Prometheus gets only the five files under
observability/prometheus/, mapped to /etc/prometheus/tianji/<target>-token.
The media-worker keyring is empty because it has no current internal RPC/scrape edge.

Rotation MUST be receiver first:
1. Publish receiver-first/<workload>/service-identities.json to every receiving
   workload: inbound [new,previous], outbound still previous. Keep senders and
   Prometheus on previous tokens until every receiver replica observes this stage.
2. Verify all receiver replicas accept the new and previous credential without
   logging either. Then publish the root workload keyrings, whose outbound is new
   and inbound remains [new,previous]. Roll every caller and update Prometheus's
   token files; verify new-token calls and all replicas, including idle workloads.
3. Retain previous-token acceptance until the rollout and observation window is
   complete. Only then explicitly run the retire command into ANOTHER new private
   directory, review its artifacts, and publish those receiver keyrings. Never
   remove previous inbound keys as part of an automatic rotation or cron job.

Before retirement, rollback re-publishes this rotation's receiver-first keyrings
(previous outbound, dual inbound); never publish an old single-inbound keyring
after any caller has advanced. After retirement, do not roll back
senders to previous tokens; first re-publish a reviewed dual-inbound receiver stage.
Preparing a retirement artifact does not prove a rollout or verification happened.
Keep all generations recoverable and protected according to the credential policy.
"""


def _package(output, rings, phase, previous=None, receiver_first=None):
    validate_keyrings(rings, phase)
    if receiver_first is not None:
        validate_keyrings(receiver_first, phase, sender_index=1)
    files = _ring_files(rings)
    if receiver_first is not None:
        files.update(_ring_files(receiver_first, "receiver-first/"))
    files["INSTRUCTIONS.txt"] = INSTRUCTIONS.encode()
    manifest = {"owner": OWNER, "generation": str(uuid.uuid4()), "phase": phase,
                "previous_generation": previous, "policy_sha256": policy_fingerprint(),
                "workloads": sorted(WORKLOADS), "edges": [list(edge) for edge in sorted(required_edges())],
                "files": {name: hashlib.sha256(content).hexdigest() for name, content in sorted(files.items())}}
    destination = _private_directory(Path(output))
    for name, content in sorted(files.items()):
        _write(destination, name, content)
    _write(destination, "manifest.json", (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode())
    load(destination)
    return {"status": phase.upper().replace("-", "_"), "generation": manifest["generation"],
            "workloads": len(WORKLOADS), "edges": len(required_edges())}


def load(directory):
    root = Path(directory)
    if not root.is_absolute() or _link(root) or not root.is_dir():
        raise IdentityError("Select an absolute owned private generation directory")
    files = []
    for path in root.rglob("*"):
        if _link(path):
            raise IdentityError("Linked credential artifacts are forbidden")
        if os.name != "nt" and stat.S_IMODE(path.stat().st_mode) & 0o077:
            raise IdentityError("Credential artifacts must be private to their owner")
        if path.is_file():
            files.append(path)
    if os.name != "nt" and stat.S_IMODE(root.stat().st_mode) & 0o077:
        raise IdentityError("Credential directory must be private to its owner")
    manifest = _json((root / "manifest.json").read_text(encoding="utf-8"))
    if not isinstance(manifest, dict) or set(manifest) != {"owner", "generation", "phase", "previous_generation", "policy_sha256", "workloads", "edges", "files"} or manifest.get("owner") != OWNER or manifest.get("phase") not in {"generated", "rotation-prepared", "retirement-prepared"}:
        raise IdentityError("Unknown or incomplete private credential generation")
    if (manifest["phase"] == "generated") != (manifest["previous_generation"] is None) or manifest["generation"] == manifest["previous_generation"]:
        raise IdentityError("Invalid generation lineage")
    identifiers = [manifest["generation"]]
    if manifest["previous_generation"] is not None:
        identifiers.append(manifest["previous_generation"])
    for identifier in identifiers:
        try:
            if str(uuid.UUID(identifier)) != identifier:
                raise ValueError()
        except (ValueError, AttributeError, TypeError):
            raise IdentityError("Generation metadata must contain canonical UUIDs") from None
    if manifest.get("workloads") != sorted(WORKLOADS) or manifest.get("edges") != [list(edge) for edge in sorted(required_edges())] or manifest.get("policy_sha256") != policy_fingerprint():
        raise IdentityError("Reviewed topology changed; do not rotate a different credential graph implicitly")
    expected = _ring_files({name: {"outbound": {target: "" for caller, target in required_edges() if caller == name}, "inbound": {}} for name in WORKLOADS})
    paths = set(expected) | {"INSTRUCTIONS.txt"}
    rotating = manifest["phase"] == "rotation-prepared"
    if rotating:
        paths |= {"receiver-first/" + name for name in expected}
    if not isinstance(manifest.get("files"), dict) or set(manifest["files"]) != paths or {p.relative_to(root).as_posix() for p in files} != paths | {"manifest.json"}:
        raise IdentityError("Unexpected, missing, or caller-mismatched credential files")
    for name, digest in manifest["files"].items():
        if not isinstance(digest, str) or hashlib.sha256((root / name).read_bytes()).hexdigest() != digest:
            raise IdentityError("Private credential artifact integrity failed")
    rings = {name: _json((root / name / "service-identities.json").read_text(encoding="utf-8")) for name in WORKLOADS}
    validate_keyrings(rings, manifest["phase"])
    for target, value in rings["observability"]["outbound"].items():
        if (root / "observability/prometheus" / (target + "-token")).read_text(encoding="utf-8") != value + "\n":
            raise IdentityError("Monitoring token file does not match its directed edge")
    if rotating:
        stage = {name: _json((root / "receiver-first" / name / "service-identities.json").read_text(encoding="utf-8")) for name in WORKLOADS}
        validate_keyrings(stage, manifest["phase"], sender_index=1)
        for name in WORKLOADS:
            if stage[name]["inbound"] != rings[name]["inbound"]:
                raise IdentityError("Receiver-first stage does not preserve both inbound generations")
        for target, value in stage["observability"]["outbound"].items():
            if (root / "receiver-first/observability/prometheus" / (target + "-token")).read_text(encoding="utf-8") != value + "\n":
                raise IdentityError("Receiver-first monitoring token file mismatch")
    return manifest, rings


def generate(output):
    return _package(output, _rings(_new_tokens()), "generated")


def rotate(previous, output):
    manifest, rings = load(previous)
    if manifest["phase"] == "rotation-prepared":
        raise IdentityError("Previous keys are still retained; explicitly retire them before another rotation")
    old = {(caller, receiver): rings[caller]["outbound"][receiver] for caller, receiver in required_edges()}
    current = _new_tokens()
    while set(current.values()) & set(old.values()):
        current = _new_tokens()
    return _package(output, _rings(current, old), "rotation-prepared", manifest["generation"], _rings(current, old, old_senders=True))


def retire(previous, output):
    manifest, rings = load(previous)
    if manifest["phase"] != "rotation-prepared":
        raise IdentityError("Explicit retirement requires a dual-inbound rotation generation")
    current = {(caller, receiver): rings[caller]["outbound"][receiver] for caller, receiver in required_edges()}
    return _package(output, _rings(current), "retirement-prepared", manifest["generation"])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    for action in ("generate", "rotate", "retire"):
        command = sub.add_parser(action)
        command.add_argument("--output", type=Path, required=True)
        if action != "generate":
            command.add_argument("--previous", type=Path, required=True)
    command = sub.add_parser("validate")
    command.add_argument("--input", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.action == "generate":
            result = generate(args.output)
        elif args.action == "rotate":
            result = rotate(args.previous, args.output)
        elif args.action == "retire":
            result = retire(args.previous, args.output)
        else:
            manifest, _ = load(args.input)
            result = {"status": "VALIDATED", "generation": manifest["generation"], "workloads": len(WORKLOADS), "edges": len(required_edges())}
        print(json.dumps(result))  # Metadata only; never emit keyrings, tokens, or provider credentials.
        return 0
    except IdentityError as error:
        print("Refused: " + str(error), file=sys.stderr)
    except (OSError, KeyError, TypeError, subprocess.SubprocessError):
        print("Refused: incomplete, unavailable, existing, or unsafe private output/input", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
