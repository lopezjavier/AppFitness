import importlib.util
import json
import os
import tempfile
import unittest

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


if __name__ == "__main__":
    unittest.main()
