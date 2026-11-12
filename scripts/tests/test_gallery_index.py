import contextlib
import importlib.util
import io
import json
import os
import tempfile
import unittest
from unittest import mock

HERE = os.path.dirname(__file__)
spec = importlib.util.spec_from_file_location("gallery", os.path.join(HERE, "..", "gallery.py"))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def sidecar(root, rel, data, png=True):
    base = os.path.join(root, *rel.split("/"))
    os.makedirs(os.path.dirname(base), exist_ok=True)
    with open(base + ".json", "w", encoding="utf-8", newline="") as handle:
        json.dump(data, handle)
    if png:
        with open(base + ".png", "wb") as handle:
            handle.write(b"png")


def entry(area, screen, state, kind="screen", also=None):
    return {"area": area, "screen": screen, "state": state, "kind": kind, "alsoOpenedFrom": also or []}


class GalleryIndexTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = self._tmp.name
        self.addCleanup(self._tmp.cleanup)

    def index(self, manifest=None):
        entries = mod.load_entries(self.root)
        return mod.build_index(entries, manifest or {"exempt": {}})

    def test_groups_by_area_in_index_order(self):
        sidecar(self.root, "perfil/peso/data", entry("Perfil", "Peso", "data"))
        sidecar(self.root, "inicio/welcome/default", entry("Inicio", "Welcome", "default"))
        text = self.index()
        self.assertLess(text.index("<h2>Inicio</h2>"), text.index("<h2>Perfil</h2>"))

    def test_links_each_image_with_alt_text_and_caption(self):
        sidecar(self.root, "diario/diario/empty", entry("Diario", "Diario", "empty"))
        text = self.index()
        self.assertIn('href="diario/diario/empty.png"', text)
        self.assertIn('alt="Diario / Diario / empty"', text)
        self.assertIn("<figcaption>empty</figcaption>", text)

    def test_lists_overlays_under_their_screen_and_the_other_callers(self):
        sidecar(self.root, "diario/centro/list", entry("Diario", "Centro de avisos", "list", also=["Perfil"]))
        sidecar(self.root, "diario/centro/menu", entry("Diario", "Centro de avisos", "menu", kind="sheet"))
        text = self.index()
        self.assertIn("Sheets and dialogs", text)
        self.assertIn("Also opened from: Perfil", text)

    def test_lists_exemptions_with_their_reasons(self):
        sidecar(self.root, "inicio/a/b", entry("Inicio", "A", "b"))
        text = self.index({"exempt": {"CatalogRoute": "debug only"}})
        self.assertIn("CatalogRoute", text)
        self.assertIn("debug only", text)
        self.assertNotIn("Not yet in the gallery", text)

    def test_index_uses_the_dark_app_background_and_readable_text(self):
        sidecar(self.root, "inicio/a/b", entry("Inicio", "A", "b"))
        text = self.index()
        self.assertIn("background:#0B0F14", text)
        self.assertIn("color:#E0E2EA", text)
        self.assertNotIn("background:#fff", text)

    def test_design_system_is_an_index_area_after_compartido(self):
        sidecar(self.root, "design_system/button/default", entry("Design system", "Button", "default"))
        sidecar(self.root, "compartido/a/b", entry("Compartido", "A", "b"))
        text = self.index()
        self.assertLess(text.index("<h2>Compartido</h2>"), text.index("<h2>Design system</h2>"))

    def test_sidecar_without_image_fails(self):
        sidecar(self.root, "inicio/a/b", entry("Inicio", "A", "b"), png=False)
        with self.assertRaises(mod.GalleryError):
            mod.load_entries(self.root)

    def test_image_without_sidecar_fails(self):
        os.makedirs(os.path.join(self.root, "inicio", "a"))
        with open(os.path.join(self.root, "inicio", "a", "b.png"), "wb") as handle:
            handle.write(b"png")
        with self.assertRaises(mod.GalleryError):
            mod.load_entries(self.root)


class AreaTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = self._tmp.name
        self.addCleanup(self._tmp.cleanup)

    def test_resolve_area_is_lenient_about_case_and_separators(self):
        self.assertEqual(mod.resolve_area("entreno"), "Entreno")
        self.assertEqual(mod.resolve_area("Design system"), "Design system")
        self.assertEqual(mod.resolve_area("design-system"), "Design system")
        self.assertEqual(mod.resolve_area("design_system"), "Design system")

    def test_resolve_area_rejects_unknown_with_the_valid_list(self):
        with self.assertRaises(ValueError) as caught:
            mod.resolve_area("Foo")
        self.assertIn("Inicio", str(caught.exception))
        self.assertIn("Design system", str(caught.exception))

    def write_two_areas(self):
        sidecar(self.root, "inicio/a/b", entry("Inicio", "A", "b"))
        sidecar(self.root, "entreno/c/d", entry("Entreno", "C", "d"))

    def test_write_index_with_an_area_lists_only_that_area(self):
        self.write_two_areas()
        with open(mod.write_index(self.root, area="Entreno"), encoding="utf-8") as handle:
            text = handle.read()
        self.assertIn("<h2>Entreno</h2>", text)
        self.assertNotIn("<h2>Inicio</h2>", text)
        self.assertIn("Area: Entreno", text)

    def test_write_index_without_area_lists_everything(self):
        self.write_two_areas()
        with open(mod.write_index(self.root), encoding="utf-8") as handle:
            text = handle.read()
        self.assertIn("<h2>Entreno</h2>", text)
        self.assertIn("<h2>Inicio</h2>", text)
        self.assertNotIn("Area:", text)

    def test_gradle_command_without_area_has_no_area_property(self):
        cmd = mod.gradle_command("bash", None)
        self.assertNotIn("gallery.area", " ".join(cmd))
        self.assertIn("-Pgallery", cmd)

    def test_gradle_command_with_area(self):
        cmd = mod.gradle_command("bash", "Design system")
        self.assertIn("-Pgallery.area=Design system", cmd)
        self.assertIn("-Pgallery", cmd)
        self.assertIn("*.gallery.*", cmd)


class MainTest(unittest.TestCase):
    def test_unknown_area_returns_2_without_running_gradle(self):
        with mock.patch.object(mod.subprocess, "run") as run, contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(mod.main(["Foo"]), 2)
        run.assert_not_called()

    def test_bash_not_found_returns_1(self):
        with mock.patch.object(mod.gitbash, "find_bash", side_effect=mod.gitbash.BashNotFound("nope")), \
                mock.patch.object(mod.shutil, "rmtree"), mock.patch.object(mod.subprocess, "run") as run, \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(mod.main([]), 1)
        run.assert_not_called()

    def test_gradle_gets_the_discovered_java_home(self):
        done = mock.Mock(returncode=1)
        with mock.patch.object(mod.gitbash, "find_bash", return_value="bash"), \
                mock.patch.object(mod.gitbash, "java_env", return_value={"JAVA_HOME": "/jdk"}), \
                mock.patch.object(mod.shutil, "rmtree"), mock.patch.object(mod.subprocess, "run", return_value=done) as run:
            self.assertEqual(mod.main(["entreno"]), 1)
        self.assertEqual(run.call_args.kwargs["env"]["JAVA_HOME"], "/jdk")
        self.assertIn("-Pgallery.area=Entreno", run.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
