"""Read-only source binding for reviewed production service method/path inventories.

Failures require reviewing source changes and the static grants before manually
updating their manifest header. Tests never regenerate grants or update hashes.
"""
import hashlib
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
POLICY_HOME = ROOT / "tj-common/src/main/resources/META-INF/tianji"
FEIGN_ROOTS = (
    "tj-api/src/main/java",
    "tj-pay/tj-pay-api/src/main/java",
    "tj-message/tj-message-api/src/main/java",
)
DIRECT_AUTH_SOURCES = (
    "tj-auth/tj-auth-gateway-sdk/src/main/java/com/tianji/authsdk/gateway/util/JwtSignerHolder.java",
    "tj-auth/tj-auth-gateway-sdk/src/main/java/com/tianji/authsdk/gateway/util/SessionVerifier.java",
)
GATEWAY_SOURCES = (
    "tj-gateway/src/main/resources/access-policy.txt",
    "tj-gateway/src/main/java/com/tianji/gateway/config/V2Routes.java",
    "tj-gateway/src/main/java/com/tianji/gateway/config/SimulatorRoutes.java",
    "tj-gateway/src/main/java/com/tianji/gateway/filter/AccountAuthFilter.java",
)
HEADER = re.compile(r"^# Source-Manifest-SHA256: ([0-9a-f]{64})$", re.MULTILINE)


def entry(relative, content):
    """Hash exact source bytes except platform line endings; preserve paths and all other whitespace."""
    normalized = content.replace(b"\r\n", b"\n").replace(b"\r", b"\n")
    return hashlib.sha256(normalized).hexdigest() + "  " + relative


def manifest(paths):
    relatives = sorted({path.relative_to(ROOT).as_posix() for path in paths})
    if not relatives:
        raise AssertionError("Reviewed source manifest must not be empty")
    text = "\n".join(entry(relative, (ROOT / relative).read_bytes()) for relative in relatives) + "\n"
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def rpc_sources():
    sources = [path for directory in FEIGN_ROOTS for path in (ROOT / directory).rglob("*.java")
               if re.search(rb"@FeignClient\s*\(", path.read_bytes())]
    if not sources:
        raise AssertionError("No Feign declarations found; review the source discovery roots")
    return sources + [ROOT / relative for relative in DIRECT_AUTH_SOURCES]


class ReviewedRpcInventorySourceTest(unittest.TestCase):
    def assert_bound(self, policy_name, sources):
        policy = POLICY_HOME / policy_name
        headers = HEADER.findall(policy.read_text(encoding="utf-8"))
        self.assertEqual(1, len(headers), "Expected exactly one source-manifest SHA256 header in " + policy_name)
        current = manifest(sources)
        self.assertEqual(headers[0], current,
                         policy_name + " source contract changed. Review all changed RPC/routes, caller permissions "
                         "and negative cases before manually updating grants and Source-Manifest-SHA256. "
                         "This test never regenerates permissions. Current reviewed-source candidate: " + current)

    def test_peer_rpc_inventory_is_bound_to_feign_and_direct_auth_contracts(self):
        self.assert_bound("service-rpc-policy.txt", rpc_sources())

    def test_gateway_inventory_is_bound_to_public_policy_routes_and_exclusions(self):
        self.assert_bound("gateway-module-policy.txt", [ROOT / relative for relative in GATEWAY_SOURCES])

    def test_manifest_is_stable_across_windows_and_linux_line_endings(self):
        self.assertEqual(entry("sample.java", b"first\r\nsecond\r\n"),
                         entry("sample.java", b"first\nsecond\n"))


if __name__ == "__main__":
    unittest.main()
