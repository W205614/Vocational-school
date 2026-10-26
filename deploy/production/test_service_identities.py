import contextlib
import copy
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest

import service_identities as identities


class DirectedCredentialPreparationTest(unittest.TestCase):
    def test_generated_edges_are_unique_private_scoped_and_never_printed_or_overwritten(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "initial"
            stdout, stderr = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                self.assertEqual(0, identities.main(["generate", "--output", str(output)]))
            metadata = json.loads(stdout.getvalue())
            manifest, rings = identities.load(output)
            self.assertEqual("GENERATED", metadata["status"])
            self.assertEqual(set(identities.WORKLOADS), set(rings))
            tokens = []
            for caller, receiver in identities.required_edges():
                token = rings[caller]["outbound"][receiver]
                self.assertTrue(token == rings[receiver]["inbound"][caller][0], "Directed sender/receiver mismatch")
                self.assertTrue(identities.TOKEN.fullmatch(token) is not None, "Edge token has insufficient generated entropy encoding")
                self.assertFalse(token in stdout.getvalue() or token in stderr.getvalue(), "CLI exposed a token")
                tokens.append(token)
            self.assertEqual(len(tokens), len(set(tokens)))
            self.assertEqual({"outbound": {}, "inbound": {}}, rings["media-worker"])
            self.assertEqual({}, rings["observability"]["inbound"])
            self.assertEqual(set(identities.APPLICATIONS), set(rings["observability"]["outbound"]))
            before = (output / "manifest.json").read_bytes()
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1, identities.main(["generate", "--output", str(output)]))
            self.assertTrue(before == (output / "manifest.json").read_bytes(), "Existing generation was overwritten")

    def test_receiver_first_rotation_and_explicit_retirement_preserve_previous_artifacts(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            initial, rotated, retired = root / "initial", root / "rotated", root / "retired"
            identities.generate(initial)
            old_manifest, old = identities.load(initial)
            before = hashlib.sha256((initial / "manifest.json").read_bytes()).hexdigest()
            identities.rotate(initial, rotated)
            rotation, new = identities.load(rotated)
            self.assertEqual("rotation-prepared", rotation["phase"])
            self.assertEqual(old_manifest["generation"], rotation["previous_generation"])
            for caller, receiver in identities.required_edges():
                previous = old[caller]["outbound"][receiver]
                current = new[caller]["outbound"][receiver]
                self.assertTrue(current != previous, "Rotation reused an old edge token")
                self.assertTrue(new[receiver]["inbound"][caller] == [current, previous], "Receiver rotation window changed")
                stage = json.loads((rotated / "receiver-first" / caller / "service-identities.json").read_text())
                self.assertTrue(stage["outbound"][receiver] == previous, "Receiver-first stage advanced the sender prematurely")
            with self.assertRaises(identities.IdentityError):
                identities.rotate(rotated, root / "unsafe-second-rotation")
            self.assertFalse((root / "unsafe-second-rotation").exists())
            identities.retire(rotated, retired)
            retired_manifest, final = identities.load(retired)
            self.assertEqual("retirement-prepared", retired_manifest["phase"])
            for caller, receiver in identities.required_edges():
                self.assertTrue(final[caller]["outbound"][receiver] == new[caller]["outbound"][receiver])
                self.assertTrue(final[receiver]["inbound"][caller] == [new[caller]["outbound"][receiver]])
            self.assertTrue(before == hashlib.sha256((initial / "manifest.json").read_bytes()).hexdigest())
            self.assertEqual("rotation-prepared", identities.load(rotated)[0]["phase"])

    def test_duplicate_keys_cross_edge_reuse_wrong_caller_and_unsafe_outbound_are_rejected(self):
        with self.assertRaises(identities.IdentityError):
            identities._json('{"outbound":{},"outbound":{},"inbound":{}}')
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "initial"
            identities.generate(root)
            _, rings = identities.load(root)
            edges = sorted(identities.required_edges())
            (first_caller, first_receiver), (second_caller, second_receiver) = edges[:2]
            duplicate = copy.deepcopy(rings)
            shared = duplicate[first_caller]["outbound"][first_receiver]
            duplicate[second_caller]["outbound"][second_receiver] = shared
            duplicate[second_receiver]["inbound"][second_caller] = [shared]
            with self.assertRaises(identities.IdentityError):
                identities.validate_keyrings(duplicate, "generated")
            mismatched = copy.deepcopy(rings)
            mismatched["unknown-caller"] = mismatched.pop("identity")
            with self.assertRaises(identities.IdentityError):
                identities.validate_keyrings(mismatched, "generated")
            unsafe = copy.deepcopy(rings)
            unsafe["gateway"]["outbound"]["unreviewed-peer"] = shared
            with self.assertRaises(identities.IdentityError):
                identities.validate_keyrings(unsafe, "generated")
            swapped = copy.deepcopy(rings)
            swapped[first_caller]["outbound"][first_receiver] = "a" * 64
            with self.assertRaises(identities.IdentityError):
                identities.validate_keyrings(swapped, "generated")
            # Even tampered metadata must not turn CLI diagnostics into a token printer.
            manifest = json.loads((root / "manifest.json").read_text())
            manifest["generation"] = shared
            (root / "manifest.json").write_text(json.dumps(manifest))
            stdout, stderr = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                self.assertEqual(1, identities.main(["validate", "--input", str(root)]))
            self.assertFalse(shared in stdout.getvalue() or shared in stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
