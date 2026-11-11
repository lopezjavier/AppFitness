import importlib.util
import os
import tempfile
import unittest

HERE = os.path.dirname(__file__)
spec = importlib.util.spec_from_file_location("check_unused_resources", os.path.join(HERE, "..", "check-unused-resources.py"))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def write(root, rel, text):
    path = os.path.join(root, *rel.split("/"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(text)


VALUES = "composeApp/src/commonMain/composeResources/values/strings.xml"
KT = "composeApp/src/commonMain/kotlin/A.kt"


class CheckUnusedResourcesTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = self._tmp.name
        self.addCleanup(self._tmp.cleanup)

    def strings(self, body):
        write(self.root, VALUES, "<resources>\n" + body + "\n</resources>\n")

    def test_used_string_passes(self):
        self.strings('<string name="hello">Hola</string>')
        write(self.root, KT, "val a = Res.string.hello")
        self.assertEqual(mod.check(self.root), ([], []))

    def test_unused_string_is_reported(self):
        self.strings('<string name="hello">Hola</string><string name="bye">Adios</string>')
        write(self.root, KT, "val a = Res.string.hello")
        self.assertEqual(mod.check(self.root), (["bye"], []))

    def test_prefix_of_another_id_is_not_a_use(self):
        self.strings('<string name="hello">Hola</string>')
        write(self.root, KT, "val a = Res.string.hello_world")
        self.assertEqual(mod.check(self.root)[0], ["hello"])

    def test_plurals_and_arrays_and_drawables_are_handled(self):
        self.strings('<plurals name="days"><item quantity="one">d</item></plurals>\n<string-array name="opts"><item>a</item></string-array>')
        write(self.root, "composeApp/src/commonMain/composeResources/drawable/ic_x.xml", "<vector/>")
        write(self.root, "composeApp/src/commonMain/composeResources/files/exercises/images/a.png", "x")
        write(self.root, KT, "Res.plurals.days; Res.array.opts")
        self.assertEqual(mod.check(self.root)[0], ["ic_x"])

    def test_dynamic_lookup_is_detected(self):
        self.strings('<string name="hello">Hola</string>')
        write(self.root, KT, "val a = Res.string.hello; val b = Res.allStringResources")
        unused, dynamic = mod.check(self.root)
        self.assertEqual(unused, [])
        self.assertEqual(len(dynamic), 1)

    def test_allowlist_wildcard_counts_as_use(self):
        self.strings('<string name="menu_helper">x</string><string name="other">y</string>')
        write(self.root, KT, "val a = 1")
        write(self.root, "scripts/ux-inline-text-allowlist.txt", "# c\nmenu_*  # reason\n")
        self.assertEqual(mod.check(self.root)[0], ["other"])


if __name__ == "__main__":
    unittest.main()
