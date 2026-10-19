import importlib.util
import os
import unittest

_spec = importlib.util.spec_from_file_location(
    "deliver_apk", os.path.join(os.path.dirname(__file__), "deliver-apk.py")
)
deliver = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(deliver)

SAMPLE = (
    "List of devices attached\r\n"
    "R58M123ABC             device usb:1-1 product:x model:SM_G991B device:y transport_id:1\r\n"
    "emulator-5554          device product:sdk model:sdk_gphone transport_id:2\r\n"
    "ZZZ999                 unauthorized usb:1-2 transport_id:3\r\n"
    "OFF111                 offline\r\n"
    "\r\n"
)


class DeliverApkTest(unittest.TestCase):
    def test_parse_devices(self):
        devices = deliver.parse_devices(SAMPLE)
        self.assertEqual(
            devices,
            [("R58M123ABC", "device"), ("emulator-5554", "device"),
             ("ZZZ999", "unauthorized"), ("OFF111", "offline")],
        )

    def test_excludes_emulators_by_default(self):
        serials, warnings = deliver.select_serials(deliver.parse_devices(SAMPLE), False)
        self.assertEqual(serials, ["R58M123ABC"])
        self.assertEqual(len(warnings), 2)

    def test_includes_emulators_on_request(self):
        serials, _ = deliver.select_serials(deliver.parse_devices(SAMPLE), True)
        self.assertEqual(serials, ["R58M123ABC", "emulator-5554"])


class ProdConfigTest(unittest.TestCase):
    FULL = {
        "supabase.prod.url": "u",
        "supabase.prod.publishableKey": "k",
        "google.webClientId": "g",
    }

    def test_complete_local_properties(self):
        self.assertEqual(deliver.missing_prod_config(self.FULL, {}), [])

    def test_env_fallback(self):
        env = {"SUPABASE_URL": "u", "SUPABASE_PUBLISHABLE_KEY": "k", "GOOGLE_WEB_CLIENT_ID": "g"}
        self.assertEqual(deliver.missing_prod_config({}, env), [])

    def test_blank_values_are_missing(self):
        props = dict(self.FULL, **{"google.webClientId": "  "})
        self.assertEqual(
            deliver.missing_prod_config(props, {"GOOGLE_WEB_CLIENT_ID": ""}),
            [("google.webClientId", "GOOGLE_WEB_CLIENT_ID")],
        )

    def test_reports_all_missing(self):
        self.assertEqual(len(deliver.missing_prod_config({}, {})), 3)

    def test_read_local_properties(self):
        import tempfile

        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "local.properties")
            with open(path, "w", encoding="utf-8", newline="") as f:
                f.write("# c\nsupabase.prod.url=https://x\ngoogle.webClientId = abc\n")
            props = deliver.read_local_properties(path)
        self.assertEqual(props["supabase.prod.url"], "https://x")
        self.assertEqual(props["google.webClientId"], "abc")
        self.assertEqual(deliver.read_local_properties(os.path.join(d, "nope")), {})


class RepdbBundleTest(unittest.TestCase):
    def _exercises_dir(self, root):
        d = os.path.join(root, deliver.EXERCISES_DIR)
        os.makedirs(os.path.join(d, "images"))
        return d

    def test_refuses_without_bundle_and_names_path_and_command(self):
        import tempfile

        with tempfile.TemporaryDirectory() as root:
            problem = deliver.repdb_bundle_problem(root)
        self.assertIn(os.path.join("files", "exercises"), problem)
        self.assertIn("python scripts/import-repdb.py", problem)

    def test_refuses_with_library_but_no_images(self):
        import tempfile

        with tempfile.TemporaryDirectory() as root:
            d = self._exercises_dir(root)
            open(os.path.join(d, "library.json"), "w", encoding="utf-8").close()
            self.assertIsNotNone(deliver.repdb_bundle_problem(root))

    def test_accepts_library_and_an_image(self):
        import tempfile

        with tempfile.TemporaryDirectory() as root:
            d = self._exercises_dir(root)
            open(os.path.join(d, "library.json"), "w", encoding="utf-8").close()
            open(os.path.join(d, "images", "x-start.webp"), "wb").close()
            self.assertIsNone(deliver.repdb_bundle_problem(root))


class RepdbSourceTest(unittest.TestCase):
    def test_cli_wins_over_properties_and_env(self):
        props = {"repdb.source": "from-props"}
        env = {"REPDB_SOURCE": "from-env"}
        self.assertEqual(deliver.resolve_repdb_source("from-cli", props, env), "from-cli")
        self.assertEqual(deliver.resolve_repdb_source(None, props, env), "from-props")
        self.assertEqual(deliver.resolve_repdb_source(None, {}, env), "from-env")

    def test_blank_values_are_ignored_and_none_when_unset(self):
        self.assertEqual(deliver.resolve_repdb_source("  ", {"repdb.source": ""}, {"REPDB_SOURCE": "e"}), "e")
        self.assertIsNone(deliver.resolve_repdb_source(None, {}, {}))

    def test_step_fails_clearly_when_source_is_missing(self):
        import contextlib
        import io

        original = deliver.repdb_bundle_problem
        deliver.repdb_bundle_problem = lambda root=deliver.ROOT: "missing"
        try:
            err = io.StringIO()
            with contextlib.redirect_stderr(err), contextlib.redirect_stdout(io.StringIO()):
                ok = deliver.step_repdb("/definitely/not/here", False)
        finally:
            deliver.repdb_bundle_problem = original
        self.assertFalse(ok)
        self.assertIn("--repdb-source", err.getvalue())

    def test_step_skips_when_already_imported(self):
        import contextlib
        import io

        original = deliver.repdb_bundle_problem
        deliver.repdb_bundle_problem = lambda root=deliver.ROOT: None
        try:
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                ok = deliver.step_repdb(None, False)
        finally:
            deliver.repdb_bundle_problem = original
        self.assertTrue(ok)
        self.assertIn("skipped", out.getvalue())


if __name__ == "__main__":
    unittest.main()
