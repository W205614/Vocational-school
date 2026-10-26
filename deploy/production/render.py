"""Render a separate, fail-closed production deployment; never contacts a cluster.

Inputs contain resource identities, never secret values. Existing compact files,
containers and data are neither imported nor modified.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import ipaddress
import json
from pathlib import Path
import re
import sys
from functools import lru_cache
from urllib.parse import urlsplit

import yaml

ROOT = Path(__file__).resolve().parents[2]
GROUPS = {"identity": ["auth", "user"], "commerce": ["trade", "pay", "promotion"],
          "education": ["course", "learning", "exam", "remark"],
          "support": ["media", "search", "message", "data"]}
PORTS = {"identity": 24001, "commerce": 24002, "education": 24003, "support": 24004, "gateway": 24310}
OWNERS = {alias: group for group, aliases in GROUPS.items() for alias in aliases}
MODULES = {alias: (f"tj-{alias}" if alias not in {"auth", "pay", "message"} else
                  {"auth": "tj-auth/tj-auth-service", "pay": "tj-pay/tj-pay-service",
                   "message": "tj-message/tj-message-service"}[alias]) for alias in OWNERS}
MODULES["gateway"] = "tj-gateway"
WORKLOADS = [*GROUPS, "gateway"]
IMAGE = re.compile(r"[a-z0-9][a-z0-9./:_-]*@sha256:[0-9a-f]{64}")
NAME = re.compile(r"[a-z][a-z0-9-]{1,50}")


class ConfigurationError(ValueError):
    pass


def dns(value):
    if not isinstance(value, str) or not re.fullmatch(r"(?=.{1,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?", value):
        raise ConfigurationError("Expected an explicit DNS hostname")
    if value in {"localhost", "example.com"} or value.endswith((".example.com", ".invalid", ".localhost")):
        raise ConfigurationError("Example/loopback hosts cannot be deployed")
    try:
        ipaddress.ip_address(value)
    except ValueError:
        return value
    raise ConfigurationError("Use provider DNS endpoints, not node IP addresses")


def validate(settings):
    s = copy.deepcopy(settings)
    if not NAME.fullmatch(s.get("namespace", "")):
        raise ConfigurationError("namespace must be a lowercase Kubernetes name")
    if not re.fullmatch(r"[0-9a-f]{40}", s.get("commit", "")):
        raise ConfigurationError("An exact 40-character source commit is required")
    images = s.get("images", {})
    if set(images) != set(WORKLOADS + ["student", "admin", "media-worker", "java-agent"]):
        raise ConfigurationError("Provide immutable images for all applications, portals, media worker and Java instrumentation")
    if any(not IMAGE.fullmatch(image) for image in images.values()):
        raise ConfigurationError("Images must use immutable @sha256 digests")
    replicas = s.setdefault("replicas", 2)
    if not isinstance(replicas, int) or isinstance(replicas, bool) or replicas < 2 or replicas > 20:
        raise ConfigurationError("Production replicas must be 2..20")
    mesh = s["mesh"]
    if not NAME.fullmatch(mesh["ingress_namespace"]) or not NAME.fullmatch(mesh["ingress_service_account"]):
        raise ConfigurationError("An explicit mesh ingress workload identity is required")
    if not re.fullmatch(r"[a-z0-9.-]+", mesh["trust_domain"]):
        raise ConfigurationError("An explicit mesh trust domain is required")
    for host in (s["public"]["student_host"], s["public"]["admin_host"]):
        dns(host)
    if s["public"]["student_host"] == s["public"]["admin_host"]:
        raise ConfigurationError("Student and administrative portals must use separate origins")
    if not NAME.fullmatch(s["public"]["tls_secret"]):
        raise ConfigurationError("Ingress TLS credential Secret name is required")
    if not s["public"].get("trusted_proxy_addresses"):
        raise ConfigurationError("Supply the ingress proxy's explicit DNS/IP addresses")
    managed = s["managed"]
    for kind, default_port in {"mysql": 3306, "redis": 6380, "rabbitmq": 5671, "elasticsearch": 9243}.items():
        endpoint = managed[kind]
        dns(endpoint["host"])
        port = endpoint.setdefault("port", default_port)
        if not isinstance(port, int) or isinstance(port, bool) or not 1 <= port <= 65535:
            raise ConfigurationError("Managed endpoint ports must be 1..65535")
        if not endpoint.get("cidrs"):
            raise ConfigurationError("Managed endpoint network CIDRs are required")
        for cidr in endpoint["cidrs"]:
            network = ipaddress.ip_network(cidr, strict=True)
            if network.prefixlen == 0:
                raise ConfigurationError("Managed service egress must not allow the entire Internet")
    if not s.get("provider_hosts"):
        raise ConfigurationError("Explicit Alipay/Tencent provider API hosts are required")
    for host in s["provider_hosts"]:
        dns(host)
    if not re.fullmatch(r"[a-z0-9-]+-[0-9]+", s["providers"]["cos_bucket"]):
        raise ConfigurationError("Tencent COS bucket must include its numeric application id")
    cos_host = s["providers"]["cos_bucket"] + ".cos." + s["providers"]["region"] + ".myqcloud.com"
    if cos_host not in s["provider_hosts"]:
        raise ConfigurationError("The exact private COS bucket endpoint must be in provider_hosts")
    if not NAME.fullmatch(s["media_worker"]["signatures_claim"]):
        raise ConfigurationError("A maintained, cross-zone ClamAV signatures PVC is required")
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,64}", s["privacy"]["policy_version"]):
        raise ConfigurationError("A published privacy policy version is required")
    policy = urlsplit(s["privacy"]["policy_url"])
    if policy.scheme != "https" or policy.hostname != s["public"]["student_host"]:
        raise ConfigurationError("Privacy policy must be published on the student HTTPS origin")
    if not NAME.fullmatch(s["observability"]["namespace"]) or not NAME.fullmatch(s["observability"]["service_account"]):
        raise ConfigurationError("External observability workload identity is required")
    if not NAME.fullmatch(s["observability"]["collector_configmap"]):
        raise ConfigurationError("A private Collector ConfigMap is required for redaction preflight")
    for key in ("otlp_endpoint", "alert_webhook"):
        parsed = urlsplit(s["observability"][key])
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query:
            raise ConfigurationError("Observability endpoints must be HTTPS without embedded credentials/query tokens")
        dns(parsed.hostname)
    return s


def env(name):
    return "${" + name + "}"


def source_config(alias):
    return yaml.safe_load((ROOT / MODULES[alias] / "src/main/resources/application.yml").read_text(encoding="utf8"))


@lru_cache(maxsize=1)
def feign_declarations():
    declarations = []
    for path in ROOT.glob("tj-*/**/src/main/java/**/*Client.java"):
        declarations.extend(re.findall(r"@FeignClient\(([^)]*)\)", path.read_text(encoding="utf8")))
    return declarations


def routes(internal=False, owner=None):
    return {alias: "http://" + ("127.0.0.1" if owner == group and internal else "app-" + group)
            + ":" + str(PORTS[group] + (1000 if internal else 0)) + "/_modules/" + alias
            for alias, group in OWNERS.items()}


def module_config(alias, s):
    c = source_config(alias)
    spring = c["spring"]
    spring.update({"profiles": {"active": "production"}, "config": {"import": []},
                   "main": {"allow-bean-definition-overriding": False},
                   "lifecycle": {"timeout-per-shutdown-phase": "45s"}})
    cloud = spring.setdefault("cloud", {})
    cloud.update({"sentinel": {"enabled": False}, "nacos": {"config": {"enabled": False,
                  "import-check": {"enabled": False}}, "discovery": {"enabled": False}}})
    target = routes(True, OWNERS.get(alias))
    cloud["discovery"] = {"client": {"simple": {"instances": {
        name + "-service": [{"uri": url}] for name, url in target.items()}}}}
    clients = {name: {"url": url, "connectTimeout": 1500, "readTimeout": 5000, "loggerLevel": "none"}
               for name, url in target.items()}
    for name, value in list(clients.items()):
        clients[name + "-service"] = value
    # contextId is independent of service name; every explicit client must point
    # at the same bounded transport used by the compact module runtime.
    for declaration in feign_declarations():
        named = re.search(r'(?:value|name)\s*=\s*"([^"]+)"', declaration)
        positional = re.match(r'\s*"([^"]+)"', declaration)
        service = named or positional
        context = re.search(r'contextId\s*=\s*"([^"]+)"', declaration)
        if service and context and service.group(1).removesuffix("-service") in target:
            clients[context.group(1)] = clients[service.group(1).removesuffix("-service")]
    cloud["openfeign"] = {"client": {"config": clients}, "httpclient": {
        "connection-timeout": 1500, "max-connections": 128, "max-connections-per-route": 64,
        "hc5": {"connection-request-timeout": 1500, "connection-request-timeout-unit": "milliseconds",
                "socket-timeout": 5, "socket-timeout-unit": "seconds"}}}
    r = s["managed"]["redis"]
    spring["data"] = {"redis": {"host": r["host"], "port": r["port"], "username": env("REDIS_USERNAME"),
        "password": env("REDIS_PASSWORD"), "ssl": {"enabled": True}, "timeout": "2s"}}
    mq = s["managed"]["rabbitmq"]
    spring["rabbitmq"] = {"host": mq["host"], "port": mq["port"], "virtual-host": s["namespace"],
        "username": env("RABBIT_USERNAME"), "password": env("RABBIT_PASSWORD"),
        "ssl": {"enabled": True, "validate-server-certificate": True, "verify-hostname": True},
        "publisher-confirm-type": "correlated", "publisher-returns": True,
        "listener": {"simple": {"concurrency": 1, "max-concurrency": 2, "prefetch": 10,
            "default-requeue-rejected": False, "retry": {"enabled": True, "max-attempts": 3, "initial-interval": "500ms"}}}}
    spring["task"] = {"scheduling": {"pool": {"size": 2}}, "execution": {"pool": {
        "core-size": 2, "max-size": 4, "queue-capacity": 100}}}
    database = "tj_" + alias if alias not in {"data", "gateway"} else None
    if database:
        db = s["managed"]["mysql"]
        spring["datasource"] = {"url": f"jdbc:mysql://{db['host']}:{db['port']}/{database}?sslMode=VERIFY_IDENTITY&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true",
            "username": env(alias.upper() + "_DB_USERNAME"), "password": env(alias.upper() + "_DB_PASSWORD"),
            "driver-class-name": "com.mysql.cj.jdbc.Driver", "hikari": {"maximum-pool-size": 6,
                "minimum-idle": 0, "connection-timeout": 3000, "pool-name": alias + "-db"}}
    else:
        spring.pop("datasource", None)
        spring["autoconfigure"] = {"exclude": ["org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"]}
    c["server"] = {"port": PORTS.get(alias, 0), "shutdown": "graceful", "forward-headers-strategy": "none",
                   "error": {"include-message": "never", "include-stacktrace": "never"}}
    tj = c.setdefault("tj", {})
    tj["production"] = {"enabled": True, "transport": "mesh", "module": alias}
    tj["compact"] = {"readiness": {"required": "db,redis" if database else "redis"}}
    tj["xxljob"] = {"enabled": False}
    tj.setdefault("swagger", {})["enable"] = False
    tj["feign"] = {"max-concurrent": 32, "internal-ports": ",".join(str(PORTS[g] + 1000) for g in GROUPS)}
    tj["reliability"] = {"enabled": database is not None}
    resource = tj.setdefault("auth", {}).setdefault("resource", {})
    resource["enable"] = True
    resource.setdefault("excludeLoginPaths", []).extend(["/actuator/health/**", "/readyz"])
    if alias in {"course", "search"}:
        resource["excludeLoginPaths"].append("/api/v2/public/**")
    if alias == "auth":
        c["encrypt"] = {"key-store": {"alias": "tianji", "location": "file:/run/signing/signing.jks",
            "password": env("SIGNING_STORE_PASSWORD"), "secret": env("SIGNING_KEY_PASSWORD")}}
        tj["auth"]["cookie"] = {"require-secure": True}
        tj["auth"]["mfa"] = {"required": True}
        resource["includeLoginPaths"] = ["/menus/me", "/accounts/logout", "/accounts/sessions/**"]
    if alias == "user":
        tj["accounts"] = {"public": {"enabled": True, "sms-delivery-mode": "provider"},
            "privacy": {"policy-version": s["privacy"]["policy_version"],
                "policy-url": s["privacy"]["policy_url"], "retention-review-days": 30}}
    if alias == "pay":
        tj["pay"]["simulated"] = False
        tj["pay"]["wx"] = {"enabled": False}
        tj["pay"]["ali"]["enabled"] = True
        tj["pay"]["ali"]["seller-id"] = env("ALIPAY_SELLER_ID")
        tj["pay"]["notifyHost"] = "https://" + s["public"]["student_host"] + "/api/v2/services/pay"
    if alias == "message":
        tj.setdefault("sms", {})["simulated"] = False
    if alias == "media":
        resource["excludeLoginPaths"].extend(["/cloud-content/**", "/course-covers/*"])
        tj["platform"] = {"file": "TENCENT", "media": "CLOUD_COS"}
        tj["local-storage"] = {"enabled": False}
        tj["tencent"]["vod"] = {"enable": False}
        tj["tencent"]["cos"].update({"enable": True, "region": s["providers"]["region"],
                                    "bucket": s["providers"]["cos_bucket"].rsplit("-", 1)[0]})
        tj["tencent"]["appId"] = s["providers"]["cos_bucket"].rsplit("-", 1)[1]
        tj["cloud-media"] = {"enabled": True, "role": "api", "bucket": s["providers"]["cos_bucket"],
            "region": s["providers"]["region"], "secret-id": env("CLOUD_MEDIA_SECRET_ID"),
            "secret-key": env("CLOUD_MEDIA_SECRET_KEY"), "signing-key": env("CLOUD_MEDIA_SIGNING_KEY"),
            "max-upload-bytes": 209715200, "upload-ttl-seconds": 900, "playback-ttl-seconds": 600}
    if alias == "search":
        es = s["managed"]["elasticsearch"]
        spring["elasticsearch"] = {"uris": f"https://{es['host']}:{es['port']}",
            "username": env("ELASTICSEARCH_USERNAME"), "password": env("ELASTICSEARCH_PASSWORD")}
        tj["interests"] = {"top-number": 10}
    if alias == "gateway":
        spring.pop("rabbitmq", None)
        spring["main"]["web-application-type"] = "reactive"
        cloud["gateway"] = {"server": {"webflux": {"routes": [], "globalcors": {"corsConfigurations": {
            "[/**]": {"allowedOrigins": ["https://" + s["public"]["student_host"], "https://" + s["public"]["admin_host"]],
                       "allowedMethods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"], "allowedHeaders": ["*"], "allowCredentials": True}}}}}}
        tj["routes"] = routes()
        tj["simulators"] = {"enabled": False, "storage": False, "video": False, "sms": False}
        tj["gateway"] = {"proxy": {"trusted-addresses": s["public"]["trusted_proxy_addresses"]}}
        tj["cloud-media"] = {"enabled": True, "delivery": "ENTITLEMENT_PROXY"}
        tj["platform"] = {"media": "CLOUD_COS"}
        resource["enable"] = False
    c["mybatis-plus"] = {"mapper-locations": "classpath*:mapper/**/*.xml", "configuration": {"map-underscore-to-camel-case": True}}
    c["springdoc"] = {"api-docs": {"enabled": False}, "swagger-ui": {"enabled": False}}
    c["logging"] = {"level": {"root": "INFO", "feign": "WARN"}}
    c["management"] = {"endpoints": {"web": {"exposure": {"include": "health,info,prometheus"}}},
        "endpoint": {"health": {"show-details": "never", "probes": {"enabled": True}, "group": {
            "liveness": {"include": "livenessState"}, "readiness": {"include": "readinessState" + (",db" if database else "") +
                (",redis,authentication" if alias == "gateway" else ""), "additional-path": "server:/readyz"}}}}}
    return c


@lru_cache(maxsize=4)
def host_config(group):
    excludes = [line.strip() for source in ROOT.glob("tj-*/**/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                for line in source.read_text(encoding="utf8").splitlines() if line.strip()]
    excludes += ["com.tianji.authsdk.resource.config.ResourceInterceptorConfiguration",
        "com.tianji.authsdk.resource.config.FeignRelayUserAutoConfiguration", "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration", "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"]
    return {"server": {"port": PORTS[group], "shutdown": "graceful", "tomcat": {"threads": {"max": 80, "min-spare": 4},
        "accept-count": 50, "max-connections": 2000}}, "spring": {"profiles": {"active": "production"},
        "application": {"name": group + "-app"}, "autoconfigure": {"exclude": sorted(set(excludes))},
        "main": {"allow-bean-definition-overriding": False}, "lifecycle": {"timeout-per-shutdown-phase": "45s"},
        "cloud": {"sentinel": {"enabled": False}, "nacos": {"config": {"enabled": False, "import-check": {"enabled": False}},
        "discovery": {"enabled": False}}}}, "tj": {"compact": {"config-directory": "/run/compact/configs",
        "internal-port": PORTS[group] + 1000}, "production": {"enabled": True, "transport": "mesh"}, "reliability": {"enabled": False}},
        "management": {"endpoints": {"web": {"exposure": {"include": "health,info,prometheus"}}}, "endpoint": {"health": {
            "probes": {"enabled": True}, "show-details": "never", "group": {"liveness": {"include": "livenessState"},
            "readiness": {"include": "readinessState,modules", "additional-path": "server:/readyz"}}}}}}


def secret_keys(group):
    keys = ["REDIS_USERNAME", "REDIS_PASSWORD", "OTEL_EXPORTER_OTLP_HEADERS"]
    if group != "gateway":
        keys += ["RABBIT_USERNAME", "RABBIT_PASSWORD"]
    for alias in GROUPS.get(group, []):
        if alias != "data":
            keys += [alias.upper() + "_DB_USERNAME", alias.upper() + "_DB_PASSWORD"]
    if group == "identity":
        keys += ["SIGNING_STORE_PASSWORD", "SIGNING_KEY_PASSWORD", "TJ_MFA_ENCRYPTION_KEY"]
    if group == "commerce":
        keys += ["TJ_PAY_TJ_PAY_ALI_APPID", "TJ_PAY_TJ_PAY_ALI_MERCHANTPRIVATEKEY", "TJ_PAY_TJ_PAY_ALI_PUBLICKEY", "ALIPAY_SELLER_ID"]
    if group == "support":
        keys += ["ELASTICSEARCH_USERNAME", "ELASTICSEARCH_PASSWORD", "TJ_MEDIA_TJ_TENCENT_APPID", "TJ_MEDIA_TJ_TENCENT_SECRETID",
                 "TJ_MEDIA_TJ_TENCENT_SECRETKEY", "CLOUD_MEDIA_SECRET_ID", "CLOUD_MEDIA_SECRET_KEY", "CLOUD_MEDIA_SIGNING_KEY",
                 "TJ_MESSAGE_TJ_SMS_ALI_ACCESSID", "TJ_MESSAGE_TJ_SMS_ALI_ACCESSSECRET"]
    return keys


def object_(kind, name, namespace, spec=None, **extra):
    versions = {"Deployment": "apps/v1", "PodDisruptionBudget": "policy/v1", "NetworkPolicy": "networking.k8s.io/v1",
        "PeerAuthentication": "security.istio.io/v1", "AuthorizationPolicy": "security.istio.io/v1",
        "DestinationRule": "networking.istio.io/v1", "Gateway": "networking.istio.io/v1", "VirtualService": "networking.istio.io/v1",
        "ServiceEntry": "networking.istio.io/v1", "Sidecar": "networking.istio.io/v1"}
    result = {"apiVersion": versions.get(kind, "v1"), "kind": kind, "metadata": {"name": name, "namespace": namespace}}
    if spec is not None:
        result["spec"] = spec
    result.update(extra)
    return result


def render(settings):
    s = validate(settings)
    ns, commit = s["namespace"], s["commit"]
    docs = [{"apiVersion": "v1", "kind": "Namespace", "metadata": {"name": ns, "labels": {
        "istio-injection": "enabled", "pod-security.kubernetes.io/enforce": "restricted"}}}]
    docs.append({"apiVersion": "opentelemetry.io/v1alpha1", "kind": "Instrumentation", "metadata": {"name": "tianji-java", "namespace": ns},
        "spec": {"exporter": {"endpoint": s["observability"]["otlp_endpoint"]}, "propagators": ["tracecontext", "baggage"],
            "sampler": {"type": "parentbased_traceidratio", "argument": "0.1"}, "java": {"image": s["images"]["java-agent"],
                "env": [{"name": "OTEL_EXPORTER_OTLP_PROTOCOL", "value": "http/protobuf"},
                        {"name": "OTEL_LOGS_EXPORTER", "value": "none"}, {"name": "OTEL_METRICS_EXPORTER", "value": "none"}]}}})
    transform = observability_config(s)["otel-sanitize.yml"]["processors"]["transform/tianji_redaction"]
    docs.append(object_("ConfigMap", "production-observability-contract", ns, data={
        "collector-namespace": s["observability"]["namespace"], "collector-configmap": s["observability"]["collector_configmap"],
        "processor-sha256": hashlib.sha256(json.dumps(transform, sort_keys=True).encode()).hexdigest()}))
    principals = lambda group: s["mesh"]["trust_domain"] + "/ns/" + ns + "/sa/" + group
    ingress_principal = s["mesh"]["trust_domain"] + "/ns/" + s["mesh"]["ingress_namespace"] + "/sa/" + s["mesh"]["ingress_service_account"]
    metrics_principal = s["mesh"]["trust_domain"] + "/ns/" + s["observability"]["namespace"] + "/sa/" + s["observability"]["service_account"]
    docs += [object_("PeerAuthentication", "require-mtls", ns, {"mtls": {"mode": "STRICT"}}),
        object_("Sidecar", "registered-egress-only", ns, {"outboundTrafficPolicy": {"mode": "REGISTRY_ONLY"},
            "egress": [{"hosts": ["./*", "istio-system/*"]}]}),
        object_("DestinationRule", "internal-mtls", ns, {"host": "*." + ns + ".svc.cluster.local", "trafficPolicy": {
            "tls": {"mode": "ISTIO_MUTUAL"}, "connectionPool": {"tcp": {"maxConnections": 128},
            "http": {"http1MaxPendingRequests": 64, "maxRequestsPerConnection": 1000}},
            "outlierDetection": {"consecutive5xxErrors": 5, "interval": "10s", "baseEjectionTime": "30s", "maxEjectionPercent": 50}}}),
        object_("NetworkPolicy", "default-deny", ns, {"podSelector": {}, "policyTypes": ["Ingress", "Egress"]})]
    endpoints = list(s["managed"].values())
    for name, endpoint in s["managed"].items():
        docs.append(object_("ServiceEntry", "managed-" + name, ns, {"hosts": [endpoint["host"]], "location": "MESH_EXTERNAL",
            "resolution": "DNS", "ports": [{"number": endpoint["port"], "name": "tls-" + name, "protocol": "TLS"}]}))
    external_hosts = sorted(set(s["provider_hosts"] + [urlsplit(s["observability"][k]).hostname for k in ("otlp_endpoint", "alert_webhook")]))
    docs.append(object_("ServiceEntry", "approved-provider-apis", ns, {"hosts": external_hosts, "location": "MESH_EXTERNAL",
        "resolution": "DNS", "ports": [{"number": 443, "name": "https", "protocol": "HTTPS"}]}))
    secret_contract = {}
    for group in WORKLOADS + ["student", "admin"]:
        web = group in {"student", "admin"}
        name = "web-" + group if web else "app-" + group
        port = 8080 if web else PORTS[group]
        labels = {"app": name, "app.kubernetes.io/part-of": "tianji", "app.kubernetes.io/version": commit[:12]}
        docs.append(object_("ServiceAccount", group, ns, automountServiceAccountToken=False))
        volume_mounts = [{"name": "tmp", "mountPath": "/tmp"}]
        volumes = [{"name": "tmp", "emptyDir": {"sizeLimit": "256Mi"}}]
        envs = []
        if web:
            # Existing nginx image needs writable entrypoint, cache and pid paths.
            volumes += [{"name": "nginx-cache", "emptyDir": {"sizeLimit": "32Mi"}}, {"name": "nginx-run", "emptyDir": {"sizeLimit": "8Mi"}},
                        {"name": "nginx-config", "emptyDir": {"sizeLimit": "8Mi"}}]
            volume_mounts += [{"name": "nginx-cache", "mountPath": "/var/cache/nginx"}, {"name": "nginx-run", "mountPath": "/var/run"},
                             {"name": "nginx-config", "mountPath": "/etc/nginx/conf.d"}]
            envs = [{"name": "GW_UPSTREAM", "value": "app-gateway:24310"}]
        else:
            config = {alias + ".yml": yaml.safe_dump(module_config(alias, s), allow_unicode=True, sort_keys=False) for alias in GROUPS.get(group, ["gateway"])}
            if group in GROUPS:
                config[group + ".yml"] = yaml.safe_dump(host_config(group), sort_keys=False)
            config_name = group + "-config-" + hashlib.sha256(json.dumps(config, sort_keys=True).encode()).hexdigest()[:12]
            docs.append(object_("ConfigMap", config_name, ns, data=config, immutable=True))
            volumes += [{"name": "config", "configMap": {"name": config_name}},
                {"name": "service-identity", "secret": {"secretName": group + "-service-identity", "defaultMode": 288}}]
            volume_mounts += [{"name": "config", "mountPath": "/run/compact/configs", "readOnly": True},
                             {"name": "service-identity", "mountPath": "/run/secrets", "readOnly": True}]
            envs = [{"name": "APP_PORT", "value": str(port)}, {"name": "TJ_SERVICE_ID", "value": group},
                    {"name": "TJ_INTERNAL_AUTH_MODE", "value": "service-tokens"},
                    {"name": "TJ_SERVICE_TOKENS_FILE", "value": "/run/secrets/service-identities.json"},
                    {"name": "TJ_PRODUCTION_TLS_MODE", "value": "mesh"}, {"name": "SPRING_PROFILES_ACTIVE", "value": "production"}]
            envs += [{"name": key, "valueFrom": {"secretKeyRef": {"name": group + "-credentials", "key": key}}} for key in secret_keys(group)]
            secret_contract[group + "-credentials"] = secret_keys(group)
            secret_contract[group + "-service-identity"] = ["service-identities.json"]
            if group == "identity":
                volumes.append({"name": "signing", "secret": {"secretName": "identity-signing", "defaultMode": 288}})
                volume_mounts.append({"name": "signing", "mountPath": "/run/signing", "readOnly": True})
                secret_contract["identity-signing"] = ["signing.jks"]
        container = {"name": name, "image": s["images"][group], "imagePullPolicy": "IfNotPresent", "ports": [{"name": "http-api", "containerPort": port}],
            "env": envs, "volumeMounts": volume_mounts, "securityContext": {"allowPrivilegeEscalation": False,
                "readOnlyRootFilesystem": True, "capabilities": {"drop": ["ALL"]}},
            "resources": {"requests": {"cpu": "100m" if web else "250m", "memory": "128Mi" if web else "768Mi"},
                "limits": {"cpu": "1" if web else "2", "memory": "256Mi" if web else "1536Mi"}},
            "readinessProbe": {"httpGet": {"path": "/" if web else "/readyz", "port": "http-api"}, "periodSeconds": 5, "failureThreshold": 3},
            "livenessProbe": {"httpGet": {"path": "/" if web else "/actuator/health/liveness", "port": "http-api"}, "periodSeconds": 15, "failureThreshold": 3},
            "startupProbe": {"httpGet": {"path": "/" if web else "/readyz", "port": "http-api"}, "periodSeconds": 5, "failureThreshold": 120}}
        if not web:
            container["args"] = ["--spring.config.location=file:/run/compact/configs/" + group + ".yml"]
            volumes.append({"name": "logs", "emptyDir": {"sizeLimit": "256Mi"}})
            container["volumeMounts"].append({"name": "logs", "mountPath": "/app/logs"})
        pod_spec = {"serviceAccountName": group, "automountServiceAccountToken": False, "terminationGracePeriodSeconds": 75,
            "securityContext": {"runAsNonRoot": True, "runAsUser": 10001, "runAsGroup": 10001, "fsGroup": 10001,
                "seccompProfile": {"type": "RuntimeDefault"}}, "containers": [container], "volumes": volumes,
            "topologySpreadConstraints": [{"maxSkew": 1, "minDomains": 2, "topologyKey": "topology.kubernetes.io/zone",
                "whenUnsatisfiable": "DoNotSchedule", "labelSelector": {"matchLabels": {"app": name}}},
                {"maxSkew": 1, "topologyKey": "kubernetes.io/hostname", "whenUnsatisfiable": "DoNotSchedule",
                 "labelSelector": {"matchLabels": {"app": name}}}]}
        docs.append(object_("Deployment", name, ns, {"replicas": s["replicas"], "revisionHistoryLimit": 3,
            "strategy": {"type": "RollingUpdate", "rollingUpdate": {"maxUnavailable": 0, "maxSurge": 1}},
            "selector": {"matchLabels": {"app": name}}, "template": {"metadata": {"labels": labels, "annotations": {
                "sidecar.istio.io/inject": "true", "proxy.istio.io/config": '{"holdApplicationUntilProxyStarts":true,"terminationDrainDuration":"50s"}',
                "sidecar.istio.io/rewriteAppHTTPProbers": "true", **({"instrumentation.opentelemetry.io/inject-java": "tianji-java"} if not web else {})}}, "spec": pod_spec}}))
        ports = [{"name": "http-api", "port": port, "targetPort": port}]
        if group in GROUPS:
            ports += [{"name": "tcp-depth-" + str(i + 1), "port": port + 1000 + i, "targetPort": port + 1000 + i} for i in range(4)]
        docs += [object_("Service", name, ns, {"type": "ClusterIP", "selector": {"app": name}, "ports": ports}),
                 object_("PodDisruptionBudget", name, ns, {"maxUnavailable": 1, "selector": {"matchLabels": {"app": name}}})]
        if web:
            callers = [ingress_principal]
        elif group == "gateway":
            callers = [principals("student"), principals("admin"), ingress_principal, metrics_principal]
        else:
            callers = [principals(g) for g in WORKLOADS] + [metrics_principal]
        docs.append(object_("AuthorizationPolicy", name, ns, {"selector": {"matchLabels": {"app": name}}, "action": "ALLOW",
            "rules": [{"from": [{"source": {"principals": callers}}]}]}))
        # A namespace selector must accompany cross-namespace pod selection.
        ingress = [{"from": [{"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": ns}}}],
                    "ports": [{"port": p["port"], "protocol": "TCP"} for p in ports]}]
        if web or group == "gateway":
            ingress.append({"from": [{"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": s["mesh"]["ingress_namespace"]}}}],
                            "ports": [{"port": port, "protocol": "TCP"}]})
        ingress.append({"from": [{"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": s["observability"]["namespace"]}}}],
                        "ports": [{"port": port, "protocol": "TCP"}, {"port": 15090, "protocol": "TCP"}]})
        egress = [{"to": [{"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "kube-system"}},
                            "podSelector": {"matchLabels": {"k8s-app": "kube-dns"}}}],
                   "ports": [{"port": 53, "protocol": p} for p in ["UDP", "TCP"]]},
                  {"to": [{"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": "istio-system"}}}],
                   "ports": [{"port": 15012, "protocol": "TCP"}]},
                  {"to": [{"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": ns}}}],
                   "ports": [{"port": p, "protocol": "TCP"} for p in sorted({24310, *PORTS.values(),
                       *(PORTS[g] + 1000 + i for g in GROUPS for i in range(4))})]}]
        if not web:
            upstreams = [s["managed"]["redis"]] if group == "gateway" else [
                endpoint for kind, endpoint in s["managed"].items() if kind != "elasticsearch" or group == "support"]
            egress += [{"to": [{"ipBlock": {"cidr": cidr}} for cidr in ep["cidrs"]],
                        "ports": [{"port": ep["port"], "protocol": "TCP"}]} for ep in upstreams]
            # Public HTTPS is additionally constrained to explicit DNS ServiceEntry
            # hosts by the mesh; private/link-local addresses remain denied here.
            egress.append({"to": [{"ipBlock": {"cidr": "0.0.0.0/0", "except": ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "127.0.0.0/8"]}}],
                           "ports": [{"port": 443, "protocol": "TCP"}]})
        docs.append(object_("NetworkPolicy", name, ns, {"podSelector": {"matchLabels": {"app": name}},
            "policyTypes": ["Ingress", "Egress"], "ingress": ingress, "egress": egress}))
    worker_config = module_config("media", s)
    worker_config["server"]["port"] = 8086
    worker_config["spring"]["application"] = {"name": "media-worker"}
    worker_config["tj"]["production"]["module"] = "media-worker"
    worker_config["tj"]["auth"]["resource"]["enable"] = False
    worker_config["tj"]["reliability"] = {"enabled": False}
    worker_config["tj"]["cloud-media"].update({"role": "worker", "scanner-command": "/usr/bin/clamscan",
        "ffmpeg-command": "/usr/bin/ffmpeg", "worker-directory": "/work/media", "transcode-timeout-seconds": 1800})
    worker_config["tj"]["cloud-media"].pop("signing-key")
    worker_config["tj"].pop("tencent", None)
    worker_config["spring"].pop("rabbitmq", None)
    worker_config["spring"].pop("data", None)
    worker_config["spring"]["cloud"] = {"sentinel": {"enabled": False}, "nacos": {
        "config": {"enabled": False, "import-check": {"enabled": False}}, "discovery": {"enabled": False}}}
    worker_config["spring"]["autoconfigure"] = {"exclude": ["org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration", "com.tianji.common.autoconfigure.mq.MqConfig",
        "com.tianji.common.autoconfigure.redisson.RedissonConfig"]}
    worker_config["management"]["endpoint"]["health"]["group"]["readiness"]["include"] = "readinessState,db,cloudWorker"
    worker_yaml = yaml.safe_dump(worker_config, allow_unicode=True, sort_keys=False)
    worker_config_name = "media-worker-config-" + hashlib.sha256(worker_yaml.encode()).hexdigest()[:12]
    docs += [object_("ConfigMap", worker_config_name, ns, data={"media-worker.yml": worker_yaml}, immutable=True),
             object_("ServiceAccount", "media-worker", ns, automountServiceAccountToken=False)]
    worker_keys = ["MEDIA_DB_USERNAME", "MEDIA_DB_PASSWORD", "CLOUD_MEDIA_SECRET_ID", "CLOUD_MEDIA_SECRET_KEY", "OTEL_EXPORTER_OTLP_HEADERS"]
    secret_contract["media-worker-credentials"] = worker_keys
    secret_contract["media-worker-service-identity"] = ["service-identities.json"]
    worker_env = [{"name": k, "valueFrom": {"secretKeyRef": {"name": "media-worker-credentials", "key": k}}} for k in worker_keys]
    worker_env += [{"name": "APP_PORT", "value": "8086"}, {"name": "TJ_SERVICE_ID", "value": "media-worker"},
        {"name": "TJ_INTERNAL_AUTH_MODE", "value": "service-tokens"}, {"name": "TJ_SERVICE_TOKENS_FILE", "value": "/run/secrets/service-identities.json"},
        {"name": "TJ_PRODUCTION_TLS_MODE", "value": "mesh"}, {"name": "SPRING_PROFILES_ACTIVE", "value": "production"}]
    worker_labels = {"app": "media-worker", "app.kubernetes.io/part-of": "tianji", "app.kubernetes.io/version": commit[:12]}
    docs.append(object_("Deployment", "media-worker", ns, {"replicas": s["replicas"], "revisionHistoryLimit": 3,
        "strategy": {"type": "RollingUpdate", "rollingUpdate": {"maxUnavailable": 0, "maxSurge": 1}},
        "selector": {"matchLabels": {"app": "media-worker"}}, "template": {"metadata": {"labels": worker_labels, "annotations": {
            "sidecar.istio.io/inject": "true", "sidecar.istio.io/rewriteAppHTTPProbers": "true",
            "instrumentation.opentelemetry.io/inject-java": "tianji-java", "proxy.istio.io/config": '{"holdApplicationUntilProxyStarts":true,"terminationDrainDuration":"50s"}'}},
            "spec": {"serviceAccountName": "media-worker", "automountServiceAccountToken": False, "terminationGracePeriodSeconds": 75,
                "securityContext": {"runAsNonRoot": True, "runAsUser": 10001, "runAsGroup": 10001, "fsGroup": 10001, "seccompProfile": {"type": "RuntimeDefault"}},
                "topologySpreadConstraints": [{"maxSkew": 1, "minDomains": 2, "topologyKey": "topology.kubernetes.io/zone",
                    "whenUnsatisfiable": "DoNotSchedule", "labelSelector": {"matchLabels": {"app": "media-worker"}}}],
                "volumes": [{"name": "config", "configMap": {"name": worker_config_name}}, {"name": "tmp", "emptyDir": {"sizeLimit": "256Mi"}},
                    {"name": "work", "emptyDir": {"sizeLimit": "4Gi"}}, {"name": "logs", "emptyDir": {"sizeLimit": "64Mi"}},
                    {"name": "service-identity", "secret": {"secretName": "media-worker-service-identity", "defaultMode": 288}},
                    {"name": "signatures", "persistentVolumeClaim": {"claimName": s["media_worker"]["signatures_claim"], "readOnly": True}}],
                "containers": [{"name": "media-worker", "image": s["images"]["media-worker"], "imagePullPolicy": "IfNotPresent", "env": worker_env,
                    "args": ["--spring.config.location=file:/run/compact/configs/media-worker.yml"], "ports": [{"name": "http-api", "containerPort": 8086}],
                    "securityContext": {"allowPrivilegeEscalation": False, "readOnlyRootFilesystem": True, "capabilities": {"drop": ["ALL"]}},
                    "resources": {"requests": {"cpu": "500m", "memory": "768Mi", "ephemeral-storage": "1Gi"},
                        "limits": {"cpu": "2", "memory": "1536Mi", "ephemeral-storage": "5Gi"}},
                    "volumeMounts": [{"name": "config", "mountPath": "/run/compact/configs", "readOnly": True}, {"name": "tmp", "mountPath": "/tmp"},
                        {"name": "work", "mountPath": "/work/media"}, {"name": "logs", "mountPath": "/app/logs"},
                        {"name": "signatures", "mountPath": "/var/lib/clamav", "readOnly": True}, {"name": "service-identity", "mountPath": "/run/secrets", "readOnly": True}],
                    "startupProbe": {"httpGet": {"path": "/readyz", "port": "http-api"}, "periodSeconds": 5, "failureThreshold": 120},
                    "readinessProbe": {"httpGet": {"path": "/readyz", "port": "http-api"}, "periodSeconds": 5},
                    "livenessProbe": {"httpGet": {"path": "/actuator/health/liveness", "port": "http-api"}, "periodSeconds": 15}}]}}}))
    docs += [object_("PodDisruptionBudget", "media-worker", ns, {"maxUnavailable": 1, "selector": {"matchLabels": {"app": "media-worker"}}}),
        object_("AuthorizationPolicy", "media-worker", ns, {"selector": {"matchLabels": {"app": "media-worker"}}, "action": "ALLOW",
            "rules": [{"from": [{"source": {"principals": [metrics_principal]}}]}]}),
        object_("NetworkPolicy", "media-worker", ns, {"podSelector": {"matchLabels": {"app": "media-worker"}}, "policyTypes": ["Ingress", "Egress"],
            "ingress": [{"from": [{"namespaceSelector": {"matchLabels": {"kubernetes.io/metadata.name": s["observability"]["namespace"]}}}],
                "ports": [{"port": 8086, "protocol": "TCP"}, {"port": 15090, "protocol": "TCP"}]}],
            "egress": egress[:2] + [{"to": [{"ipBlock": {"cidr": cidr}} for cidr in s["managed"]["mysql"]["cidrs"]],
                "ports": [{"port": s["managed"]["mysql"]["port"], "protocol": "TCP"}]}] + [{"to": [{"ipBlock": {
                    "cidr": "0.0.0.0/0", "except": ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "127.0.0.0/8"]}}],
                    "ports": [{"port": 443, "protocol": "TCP"}]}]})]
    gateway = "tianji-public"
    hosts = [s["public"]["student_host"], s["public"]["admin_host"]]
    docs.append(object_("Gateway", gateway, ns, {"selector": s["mesh"].get("ingress_selector", {"istio": "ingressgateway"}),
        "servers": [{"port": {"number": 443, "name": "https", "protocol": "HTTPS"}, "tls": {"mode": "SIMPLE",
            "credentialName": s["public"]["tls_secret"], "minProtocolVersion": "TLSV1_2"}, "hosts": hosts}]}))
    for portal in ["student", "admin"]:
        docs.append(object_("VirtualService", portal, ns, {"hosts": [s["public"][portal + "_host"]], "gateways": [gateway],
            "http": [{"match": [{"uri": {"prefix": "/api/"}}], "route": [{"destination": {"host": "app-gateway", "port": {"number": 24310}}}], "timeout": "30s"},
                {"route": [{"destination": {"host": "web-" + portal, "port": {"number": 8080}}}]}]}))
    return docs, secret_contract


def observability_config(settings):
    """Native configuration fragments for the existing monitoring installation."""
    s = validate(settings)
    ns = s["namespace"]
    jobs = []
    for group in WORKLOADS:
        for alias in GROUPS.get(group, ["gateway"]):
            jobs.append({"job_name": alias, "metrics_path": "/actuator/prometheus" if group == "gateway" else "/_modules/" + alias + "/actuator/prometheus",
                "kubernetes_sd_configs": [{"role": "pod", "namespaces": {"names": [ns]}}],
                "http_headers": {"X-TJ-Service": {"values": ["observability"]}, "X-Internal-Token": {
                    "files": ["/etc/prometheus/tianji/" + group + "-token"]}},
                "relabel_configs": [{"source_labels": ["__meta_kubernetes_pod_container_name"], "regex": "app-" + group, "action": "keep"},
                    {"source_labels": ["__meta_kubernetes_pod_container_port_name"], "regex": "http-api", "action": "keep"},
                    {"source_labels": ["__meta_kubernetes_pod_ready"], "regex": "true", "action": "keep"},
                    {"source_labels": ["__meta_kubernetes_pod_name"], "target_label": "pod"},
                    {"source_labels": ["__meta_kubernetes_namespace"], "target_label": "namespace"}]})
    alertmanager = {"route": {"receiver": "external-oncall", "group_by": ["alertname", "namespace", "job"],
        "group_wait": "15s", "group_interval": "1m", "repeat_interval": "1h"}, "receivers": [{"name": "external-oncall",
        "webhook_configs": [{"url": s["observability"]["alert_webhook"], "send_resolved": True,
            "http_config": {"authorization": {"type": "Bearer", "credentials_file": "/etc/alertmanager/tianji/receiver-token"},
                            "tls_config": {"insecure_skip_verify": False}}}]}]}
    rules = {"groups": [{"name": "tianji-production", "rules": [
        {"alert": "TianjiMetricsTargetDown", "expr": 'up{namespace="' + ns + '"} == 0', "for": "2m", "labels": {"severity": "critical"},
            "annotations": {"summary": "An application metrics target is unavailable"}},
        {"alert": "TianjiDeliveryBacklog", "expr": "tj_events_oldest_seconds > 120", "for": "5m", "labels": {"severity": "warning"},
            "annotations": {"summary": "Committed business events are not converging"}},
        {"alert": "TianjiDeadBusinessEvents", "expr": "tj_events_dead > 0", "for": "1m", "labels": {"severity": "critical"},
            "annotations": {"summary": "Business events require operator intervention"}},
        {"alert": "TianjiBackupProofMissing", "expr": "absent(tianji_backup_last_verified_timestamp_seconds)", "for": "30m", "labels": {"severity": "critical"},
            "annotations": {"summary": "Managed backup verification evidence is missing"}},
        {"alert": "TianjiBackupProofExpired", "expr": "time() - tianji_backup_last_verified_timestamp_seconds > 86400", "for": "5m", "labels": {"severity": "critical"},
            "annotations": {"summary": "Last verified online backup is older than one day"}}]}]}
    return {"prometheus-jobs.yml": {"scrape_configs": jobs}, "alertmanager.yml": alertmanager, "alerts.yml": rules,
        "istio-observability-values.yml": {"meshConfig": {"accessLogFile": "/dev/stdout", "accessLogEncoding": "JSON",
            "accessLogFormat": json.dumps({"start_time": "%START_TIME%", "method": "%REQ(:METHOD)%", "status": "%RESPONSE_CODE%",
                "bytes_sent": "%BYTES_SENT%", "duration_ms": "%DURATION%", "transport_failure": "%RESPONSE_FLAGS%"})}},
        "otel-sanitize.yml": {"processors": {"transform/tianji_redaction": {"error_mode": "propagate", "trace_statements": [
            {"context": "resource", "statements": ['keep_keys(attributes, ["service.name", "service.version", "service.namespace", "deployment.environment.name", "k8s.namespace.name", "k8s.pod.name", "cloud.availability_zone"])']},
            {"context": "span", "statements": [
                'keep_keys(attributes, ["http.request.method", "http.response.status_code", "http.route", "server.address", "server.port", "db.system.name", "db.namespace", "db.operation.name", "messaging.system", "messaging.operation.name", "error.type"])',
                'replace_pattern(name, "\\\\?.*$", "")',
                'set(status.message, "")']},
            {"context": "spanevent", "statements": ['keep_keys(attributes, ["exception.type"])']}]}}},
        "backup-policy.yml": {"policy": "tianji-managed-backup-v1", "pitr_days": 7, "daily_retention_days": 30,
            "offsite_retention_days": 30, "encrypted": True, "immutable_offsite": True,
            "rpo_seconds": 300, "rto_seconds": 1800, "scope": "single-node-or-availability-zone",
            "status": "REQUIRED_NOT_VERIFIED"}}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--settings", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        settings = yaml.safe_load(args.settings.read_text(encoding="utf8"))
        docs, secrets = render(settings)
        if args.output.exists() and any(args.output.iterdir()):
            raise ConfigurationError("Output must be an empty, separate directory")
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / "resources.yaml").write_text(yaml.safe_dump_all(docs, sort_keys=False), encoding="utf8")
        (args.output / "required-secrets.json").write_text(json.dumps(secrets, indent=2) + "\n", encoding="utf8")
        for name, value in observability_config(settings).items():
            (args.output / name).write_text(yaml.safe_dump(value, sort_keys=False), encoding="utf8")
        (args.output / "observability-required-secrets.json").write_text(json.dumps({"namespace": settings["observability"]["namespace"],
            "prometheus": [group + "-token" for group in WORKLOADS], "alertmanager": ["receiver-token"]}, indent=2) + "\n", encoding="utf8")
        (args.output / "release.json").write_text(json.dumps({"commit": settings["commit"], "manifestSha256": hashlib.sha256(
            (args.output / "resources.yaml").read_bytes()).hexdigest(), "status": "RENDERED_NOT_DEPLOYED"}, indent=2) + "\n", encoding="utf8")
        print("Rendered production package; no cluster or provider calls were made")
    except (ConfigurationError, KeyError, OSError, TypeError, yaml.YAMLError) as error:
        print("Production render failed: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
