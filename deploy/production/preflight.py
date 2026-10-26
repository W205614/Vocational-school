"""Read-only checks against an explicitly selected production candidate cluster.

This observes resource configuration and rollout state. It neither injects faults
nor certifies availability, provider settlement, backup recoverability or capacity.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess
import sys

import yaml


def kubectl(context, *args):
    result = subprocess.run(["kubectl", "--context", context, *args], capture_output=True, text=True, timeout=60)
    if result.returncode:
        # kubectl diagnostics can include connection strings or embedded auth
        # plugin output. Keep all raw diagnostics outside public result files.
        raise RuntimeError("kubectl read failed; inspect the selected context privately")
    return json.loads(result.stdout)


def inspect(context, namespace, resources, secrets, reader=kubectl):
    failures = []
    expected = {v["metadata"]["name"]: v for v in resources if v["kind"] == "Deployment"}
    deployments = reader(context, "get", "deployments", "-n", namespace, "-o", "json")["items"]
    observed = {v["metadata"]["name"]: v for v in deployments}
    nodes = {v["metadata"]["name"]: v["metadata"].get("labels", {}).get("topology.kubernetes.io/zone")
             for v in reader(context, "get", "nodes", "-o", "json")["items"]}
    pods = reader(context, "get", "pods", "-n", namespace, "-l", "app.kubernetes.io/part-of=tianji", "-o", "json")["items"]
    grouped = defaultdict(list)
    for pod in pods:
        if not pod["metadata"].get("deletionTimestamp"):
            grouped[pod["metadata"].get("labels", {}).get("app")].append(pod)
    for name, desired in expected.items():
        live = observed.get(name)
        if not live:
            failures.append(name + ": deployment missing")
            continue
        replicas = desired["spec"]["replicas"]
        status = live.get("status", {})
        if status.get("observedGeneration", 0) < live["metadata"]["generation"] or status.get("availableReplicas", 0) < replicas or status.get("updatedReplicas", 0) < replicas:
            failures.append(name + ": rollout incomplete")
        template = live["spec"]["template"]["spec"]
        if template["containers"][0]["image"] != desired["spec"]["template"]["spec"]["containers"][0]["image"]:
            failures.append(name + ": image differs from candidate")
        zones = set()
        ready_pods = 0
        for pod in grouped[name]:
            pod_spec = pod["spec"]
            zone = nodes.get(pod_spec.get("nodeName"))
            ready = any(v.get("type") == "Ready" and v.get("status") == "True" for v in pod.get("status", {}).get("conditions", []))
            if ready:
                ready_pods += 1
                if zone:
                    zones.add(zone)
            if not any(v["name"] == "istio-proxy" for v in pod_spec["containers"]):
                failures.append(name + ": actual pod has no mesh sidecar")
            if not name.startswith("web-") and not any("opentelemetry" in v["name"] for v in pod_spec.get("initContainers", [])):
                failures.append(name + ": Java instrumentation was not admitted")
        if ready_pods < replicas or len(zones) < 2:
            failures.append(name + ": ready replicas are not distributed across two zones")
    mesh = reader(context, "get", "peerauthentication", "require-mtls", "-n", namespace, "-o", "json")
    if mesh.get("spec", {}).get("mtls", {}).get("mode") != "STRICT":
        failures.append("STRICT mesh authentication is absent")
    for name, required in secrets.items():
        # Values are never retained or emitted; permission to read each named
        # Secret is needed only for checking its required key names.
        obj = reader(context, "get", "secret", name, "-n", namespace, "-o", "json")
        if not set(required).issubset(obj.get("data", {})):
            failures.append(name + ": required secret keys missing")
    contract = next((v.get("data") for v in resources if v["kind"] == "ConfigMap" and
                     v["metadata"]["name"] == "production-observability-contract"), None)
    if contract:
        try:
            obj = reader(context, "get", "configmap", contract["collector-configmap"], "-n", contract["collector-namespace"], "-o", "json")
            config = yaml.safe_load(obj["data"]["collector.yaml"])
            transform = config["processors"]["transform/tianji_redaction"]
            observed_hash = hashlib.sha256(json.dumps(transform, sort_keys=True).encode()).hexdigest()
            pipelines = [value for name, value in config["service"]["pipelines"].items() if name.split("/")[0] == "traces"]
            if observed_hash != contract["processor-sha256"] or not pipelines or any("transform/tianji_redaction" not in v.get("processors", []) for v in pipelines):
                failures.append("Private Collector trace pipelines do not enforce the exact redaction processor")
            if any(name.split("/")[0] in {"debug", "logging"} for name in config.get("exporters", {})) or config.get("service", {}).get("telemetry", {}).get("logs", {}).get("level") == "debug":
                failures.append("Private Collector exposes raw debug telemetry")
        except (ValueError, KeyError, TypeError, yaml.YAMLError):
            failures.append("Private Collector configuration cannot be verified")
    return {"status": "PASSED" if not failures else "FAILED", "checks": "cluster_configuration_only",
            "failures": sorted(set(failures)), "workloads": len(expected), "capacity_proven": False,
            "availability_proven": False, "recovery_proven": False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--context", required=True)
    parser.add_argument("--package", required=True, type=Path)
    parser.add_argument("--result", required=True, type=Path)
    args = parser.parse_args(argv)
    manifest = args.package / "resources.yaml"
    try:
        resources = list(yaml.safe_load_all(manifest.read_text(encoding="utf8")))
        secrets = json.loads((args.package / "required-secrets.json").read_text(encoding="utf8"))
        namespace = next(v["metadata"]["name"] for v in resources if v["kind"] == "Namespace")
        result = inspect(args.context, namespace, resources, secrets)
        result.update({"observed_at": dt.datetime.now(dt.timezone.utc).isoformat(), "namespace": namespace,
                       "manifest_sha256": hashlib.sha256(manifest.read_bytes()).hexdigest()})
        args.result.parent.mkdir(parents=True, exist_ok=True)
        args.result.write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
        print("Production configuration preflight: " + result["status"])
        return 0 if result["status"] == "PASSED" else 1
    except (OSError, ValueError, KeyError, RuntimeError, subprocess.TimeoutExpired):
        print("Production preflight could not complete; no passing result was written", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
