import importlib.util
import os
import unittest

HERE = os.path.dirname(__file__)
spec = importlib.util.spec_from_file_location("generate_band_long", os.path.join(HERE, "..", "generate-band-long.py"))
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)


def rows(entries):
    """entries: {key: "other=0.9;another=0.8"} -> parsed alternative rows."""
    return [(k, gen.parse_alternatives_cell(v)) for k, v in entries.items()]


class PairsTest(unittest.TestCase):
    def test_directed_entries_are_deduped_into_unordered_pairs(self):
        pairs = gen.build_pairs(rows({"a": "b=0.9;c=0.8", "b": "a=0.9", "c": "a=0.8"}), expected=2)
        self.assertEqual(pairs, {("a", "b"): "0.90", ("a", "c"): "0.80"})

    def test_mismatched_score_fails(self):
        with self.assertRaises(gen.GeneratorError):
            gen.build_pairs(rows({"a": "b=0.9", "b": "a=0.8"}), expected=1)

    def test_missing_reverse_entry_fails(self):
        with self.assertRaises(gen.GeneratorError):
            gen.build_pairs(rows({"a": "b=0.9", "b": ""}), expected=1)

    def test_wrong_pair_count_fails(self):
        with self.assertRaises(gen.GeneratorError):
            gen.build_pairs(rows({"a": "b=0.9", "b": "a=0.9"}), expected=402)

    def test_unknown_key_fails(self):
        with self.assertRaises(gen.GeneratorError):
            gen.build_pairs(rows({"a": "zzz=0.9"}), expected=1, known_keys={"a"})

    def test_out_of_range_score_fails(self):
        with self.assertRaises(gen.GeneratorError):
            gen.build_pairs(rows({"a": "b=1.5", "b": "a=1.5"}), expected=1)


class CatalogTest(unittest.TestCase):
    def test_missing_source_key_fails(self):
        band = [{"key": "band-long-x", "media_source_key": "x"}]
        with self.assertRaises(gen.GeneratorError):
            gen.check_sources(band, catalog_keys={"y"})


if __name__ == "__main__":
    unittest.main()
