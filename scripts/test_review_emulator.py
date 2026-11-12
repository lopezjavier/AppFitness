import contextlib
import importlib.util
import io
import os
import unittest
from unittest import mock

HERE = os.path.dirname(__file__)
ROOT = os.path.abspath(os.path.join(HERE, ".."))
_spec = importlib.util.spec_from_file_location("review_emulator", os.path.join(HERE, "review-emulator.py"))
review = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(review)


def args(**kw):
    base = dict(no_build=False, no_reset=False, serial=None, dry_run=False, check=False, verbose=False)
    base.update(kw)
    return type("Args", (), base)()


class ProjectIdTest(unittest.TestCase):
    def test_found(self):
        self.assertEqual(review.parse_project_id('# c\nproject_id = "appfitness"\n[api]\nport = 1\n'), "appfitness")

    def test_missing_is_an_error(self):
        with self.assertRaises(ValueError):
            review.parse_project_id("[api]\nport = 1\n")

    def test_real_config(self):
        with open(os.path.join(ROOT, "supabase", "config.toml"), encoding="utf-8") as f:
            self.assertEqual(review.parse_project_id(f.read()), "appfitness")


class PickEmulatorTest(unittest.TestCase):
    def test_one_emulator(self):
        self.assertEqual(review.pick_emulator([("R58M", "device"), ("emulator-5554", "device")]), "emulator-5554")

    def test_none_is_an_error(self):
        with self.assertRaises(ValueError):
            review.pick_emulator([])

    def test_a_phone_only_is_an_error(self):
        with self.assertRaises(ValueError):
            review.pick_emulator([("R58M", "device")])

    def test_two_emulators_ask_for_serial(self):
        with self.assertRaisesRegex(ValueError, "--serial"):
            review.pick_emulator([("emulator-5554", "device"), ("emulator-5556", "device")])

    def test_serial_wins(self):
        devices = [("emulator-5554", "device"), ("emulator-5556", "device")]
        self.assertEqual(review.pick_emulator(devices, "emulator-5556"), "emulator-5556")

    def test_serial_not_connected_or_offline(self):
        with self.assertRaises(ValueError):
            review.pick_emulator([("emulator-5554", "device")], "emulator-5999")
        with self.assertRaises(ValueError):
            review.pick_emulator([("emulator-5554", "offline")], "emulator-5554")


class LocalConfigTest(unittest.TestCase):
    def test_blank_or_missing_key(self):
        self.assertEqual(review.missing_local_config({}), ["supabase.local.publishableKey"])
        self.assertEqual(review.missing_local_config({"supabase.local.publishableKey": "  "}),
                         ["supabase.local.publishableKey"])

    def test_present(self):
        self.assertEqual(review.missing_local_config({"supabase.local.publishableKey": "sb_publishable_x"}), [])


class StepsTest(unittest.TestCase):
    def names(self, a):
        return [s["name"] for s in review.build_steps(a, "emulator-5554", "appfitness")]

    def test_psql_command_stops_on_error_in_the_right_container(self):
        cmd = review.psql_command("supabase_db_appfitness")
        self.assertIn("ON_ERROR_STOP=1", cmd)
        self.assertEqual(cmd[:4], ["docker", "exec", "-i", "supabase_db_appfitness"])

    def test_order(self):
        steps = review.build_steps(args(), "emulator-5554", "appfitness")
        self.assertEqual(steps[0]["cmd"], ["npx", "supabase", "start"])
        self.assertEqual([s["step"] for s in steps], [2, 3, 4, 5, 5, 5])
        self.assertEqual(steps[1]["cmd"][-2:], ["db", "reset"])
        self.assertEqual(steps[2]["stdin"], review.SEED_FILE)
        self.assertIn("installLocalDebug", steps[3]["cmd"][-1])
        self.assertEqual(steps[3]["env"], {"ANDROID_SERIAL": "emulator-5554"})
        self.assertIn("pm", steps[4]["cmd"])
        self.assertIn("monkey", steps[5]["cmd"])

    def test_the_container_comes_from_the_project_id(self):
        steps = review.build_steps(args(), "emulator-5554", "other")
        self.assertIn("supabase_db_other", steps[2]["cmd"])

    def test_no_reset_omits_the_reset(self):
        self.assertNotIn("reset the database", self.names(args(no_reset=True)))
        self.assertIn("apply the rich dataset", self.names(args(no_reset=True)))

    def test_no_build_omits_the_install_only(self):
        names = self.names(args(no_build=True))
        self.assertNotIn("install the local flavor", names)
        self.assertIn("launch the app", names)

    def test_check_adds_the_check_after_the_dataset(self):
        steps = review.build_steps(args(check=True), "emulator-5554", "appfitness")
        names = [s["name"] for s in steps]
        self.assertEqual(names.index("check the dataset"), names.index("apply the rich dataset") + 1)
        self.assertEqual(steps[names.index("check the dataset")]["stdin"], review.CHECK_FILE)

    def test_the_dataset_files_exist_and_the_seed_is_not_a_default_seed(self):
        for f in (review.SEED_FILE, review.CHECK_FILE):
            self.assertTrue(os.path.isfile(os.path.join(ROOT, f)), f)
        with open(os.path.join(ROOT, "supabase", "config.toml"), encoding="utf-8") as f:
            toml = f.read()
        self.assertNotIn("seed-review", toml)


class DryRunTest(unittest.TestCase):
    def test_dry_run_runs_nothing_and_exits_zero(self):
        out = io.StringIO()
        with mock.patch.object(review.subprocess, "run") as run, mock.patch.object(review.subprocess, "call") as call, \
                mock.patch.object(review.subprocess, "Popen") as popen, contextlib.redirect_stdout(out):
            code = review.main(["--dry-run"])
        self.assertEqual(code, 0)
        run.assert_not_called()
        call.assert_not_called()
        popen.assert_not_called()
        self.assertIn("supabase start", out.getvalue())
        self.assertIn("seed-review.sql", out.getvalue())


class JavaHomeTest(unittest.TestCase):
    def test_run_step_passes_the_discovered_java_home(self):
        done = mock.Mock(returncode=0, stdout="")
        with mock.patch.object(review.gitbash, "java_env", return_value={"JAVA_HOME": "C:\\jdk"}), \
                mock.patch.object(review.subprocess, "run", return_value=done) as run:
            review.run_step({"cmd": ["x"], "env": {"ANDROID_SERIAL": "e"}}, "adb", False)
        env = run.call_args.kwargs["env"]
        self.assertEqual(env["JAVA_HOME"], "C:\\jdk")
        self.assertEqual(env["ANDROID_SERIAL"], "e")

    def preflight(self, **kw):
        ok = mock.Mock(returncode=0, stdout="List of devices attached\nemulator-5554\tdevice\n")
        with mock.patch.object(review.shutil, "which", return_value="x"), \
                mock.patch.object(review.subprocess, "run", return_value=ok), \
                mock.patch.object(review.deliver, "read_local_properties",
                                  return_value={"supabase.local.publishableKey": "k"}), \
                mock.patch.object(review.deliver, "repdb_bundle_problem", return_value=None), \
                mock.patch.object(review.gitbash, "java_env", return_value={}), \
                mock.patch.dict(review.os.environ, {}, clear=True):
            return review.preflight(args(**kw), "adb")

    def test_preflight_reports_a_missing_jdk_when_building(self):
        serial, problem = self.preflight()
        self.assertIsNone(serial)
        self.assertIn("JAVA_HOME", problem)

    def test_preflight_ignores_the_jdk_without_a_build(self):
        serial, problem = self.preflight(no_build=True)
        self.assertEqual((serial, problem), ("emulator-5554", None))


class CredentialsTest(unittest.TestCase):
    def test_match_seed_sql_and_the_environment_doc(self):
        with open(os.path.join(ROOT, "supabase", "seed.sql"), encoding="utf-8") as f:
            seed = f.read()
        with open(os.path.join(ROOT, "planning", "08-environment-setup.md"), encoding="utf-8") as f:
            doc = f.read()
        for text in (seed, doc):
            self.assertIn(review.TEST_EMAIL, text)
            self.assertIn(review.TEST_PASSWORD, text)
        self.assertIn(review.TEST_EMAIL, review.credentials_line())
        self.assertIn(review.TEST_PASSWORD, review.credentials_line())


if __name__ == "__main__":
    unittest.main()
