import importlib.util
import os
import unittest

HERE = os.path.dirname(__file__)
spec = importlib.util.spec_from_file_location("gitbash", os.path.join(HERE, "..", "gitbash.py"))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

WSL = "C:\\Windows\\System32\\bash.exe"
GIT = "C:\\Program Files\\Git\\cmd\\git.exe"
GIT_BASH = "C:\\Program Files\\Git\\bin\\bash.exe"


def finder(found=None, existing=()):
    table = dict(found or {})
    return dict(which=lambda name: table.get(name), exists=lambda path: path in existing)


class FindBashTest(unittest.TestCase):
    def test_wsl_launcher_first_on_path_is_skipped_for_git_bash(self):
        result = mod.find_bash(env={}, is_windows=True, **finder({"bash": WSL, "git": GIT}, {GIT_BASH}))
        self.assertEqual(result, GIT_BASH)

    def test_git_bash_env_override_wins(self):
        custom = "D:\\tools\\bash.exe"
        result = mod.find_bash(env={"GIT_BASH": custom}, is_windows=True, **finder({"bash": WSL, "git": GIT}, {custom, GIT_BASH}))
        self.assertEqual(result, custom)

    def test_git_with_bash_in_usr_bin_only(self):
        usr = "C:\\Program Files\\Git\\usr\\bin\\bash.exe"
        result = mod.find_bash(env={}, is_windows=True, **finder({"git": GIT}, {usr}))
        self.assertEqual(result, usr)

    def test_falls_back_to_program_files(self):
        result = mod.find_bash(env={"ProgramFiles": "C:\\Program Files"}, is_windows=True, **finder({}, {GIT_BASH}))
        self.assertEqual(result, GIT_BASH)

    def test_windowsapps_launcher_is_rejected(self):
        apps = "C:\\Users\\x\\AppData\\Local\\Microsoft\\WindowsApps\\bash.exe"
        with self.assertRaises(mod.BashNotFound):
            mod.find_bash(env={}, is_windows=True, **finder({"bash": apps}))

    def test_only_wsl_bash_raises_with_the_install_hint(self):
        with self.assertRaisesRegex(mod.BashNotFound, "GIT_BASH"):
            mod.find_bash(env={}, is_windows=True, **finder({"bash": WSL}))

    def test_another_bash_on_path_is_accepted(self):
        msys = "C:\\msys64\\usr\\bin\\bash.exe"
        self.assertEqual(mod.find_bash(env={}, is_windows=True, **finder({"bash": msys})), msys)

    def test_non_windows_uses_path(self):
        self.assertEqual(mod.find_bash(env={}, is_windows=False, **finder({"bash": "/bin/bash"})), "/bin/bash")


class JavaEnvTest(unittest.TestCase):
    JBR = "C:\\Program Files\\Android\\Android Studio\\jbr"

    def test_set_java_home_is_not_overridden(self):
        self.assertEqual(mod.java_env({"JAVA_HOME": "C:\\jdk"}, which=lambda n: None, exists=lambda p: True), {})

    def test_android_studio_jbr_is_used_when_unset(self):
        result = mod.java_env({"ProgramFiles": "C:\\Program Files"}, which=lambda n: None, exists=lambda p: p == self.JBR)
        self.assertEqual(result, {"JAVA_HOME": self.JBR})

    def test_java_on_path_gives_its_parent_of_bin(self):
        result = mod.java_env({}, which=lambda n: "/opt/jdk/bin/java", exists=lambda p: False)
        self.assertEqual(result, {"JAVA_HOME": os.path.dirname(os.path.dirname("/opt/jdk/bin/java"))})

    def test_nothing_found_is_empty(self):
        self.assertEqual(mod.java_env({}, which=lambda n: None, exists=lambda p: False), {})


if __name__ == "__main__":
    unittest.main()
