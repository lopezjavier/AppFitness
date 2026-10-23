import importlib.util
import json
import os
import tempfile
import unittest


def _load(name, filename):
    spec = importlib.util.spec_from_file_location(name, os.path.join(os.path.dirname(__file__), filename))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


gen = _load("generate_exercise_catalog", "generate-exercise-catalog.py")
imp = _load("import_repdb", "import-repdb.py")


def exercise(key, primary=("pectoralis_major",), body_part="chest", equipment="barbell", images=("start", "peak")):
    ex = {
        "id": key,
        "name": f"Nombre sintetico {key}",
        "description": "Descripcion sintetica.",
        "instructions": ["Paso uno.", "Paso dos."],
        "tips": ["Consejo."],
        "body_part": body_part,
        "primary_muscles": list(primary),
        "secondary_muscles": [],
        "images": {"flat": list(images)},
    }
    if equipment:
        ex["equipment"] = equipment
    return ex


def write_bundle(root, exercises, schema_version=3):
    os.makedirs(os.path.join(root, "images", "flat"))
    with open(os.path.join(root, "free.es.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"schema_version": schema_version, "exercises": exercises}, f, ensure_ascii=False)
    for ex in exercises:
        for pose in ex["images"]["flat"]:
            with open(os.path.join(root, "images", "flat", f"{ex['id']}-{pose}.webp"), "wb") as f:
                f.write(b"fake-" + pose.encode())


class MappingTest(unittest.TestCase):
    def test_muscle_ids_map_to_groups(self):
        cases = {
            "pectoralis_major": "chest", "latissimus_dorsi": "back", "rhomboids": "back", "trapezius": "back",
            "erector_spinae": "back", "anterior_deltoid": "shoulders", "lateral_deltoid": "shoulders",
            "posterior_deltoid": "shoulders", "biceps_brachii": "biceps", "brachialis": "biceps",
            "triceps_brachii": "triceps", "forearm_flexors": "forearms", "brachioradialis": "forearms",
            "quadriceps": "quads", "hip_flexors": "quads", "adductors": "quads", "abductors": "quads",
            "hamstrings": "hamstrings", "gluteus_maximus": "glutes", "gluteus_medius": "glutes",
            "gastrocnemius": "calves", "soleus": "calves", "tibialis_anterior": "calves",
            "rectus_abdominis": "core", "obliques": "core", "transverse_abdominis": "core",
        }
        for muscle, group in cases.items():
            with self.subTest(muscle=muscle):
                self.assertEqual(gen.muscle_group_of(exercise("x", primary=(muscle,))), group)

    def test_full_body_wins_over_muscle(self):
        self.assertEqual(gen.muscle_group_of(exercise("x", primary=("gluteus_maximus",), body_part="full_body")),
                         "full_body")

    def test_body_part_is_the_fallback_without_muscles(self):
        self.assertEqual(gen.muscle_group_of(exercise("x", primary=(), body_part="upper_legs")), "quads")

    def test_equipment_ids_map_to_enum(self):
        cases = {
            "barbell": "barbell", "ez_bar": "barbell", "trap_bar": "barbell", "landmine": "barbell",
            "dumbbell": "dumbbell", "cable": "cable", "kettlebell": "kettlebell", "loop_band": "band",
            "resistance_band": "band", "leg_press": "machine", "smith_machine": "machine",
            "pec_deck": "machine", "hack_squat": "machine", "chest_press_machine": "machine",
            "pull_up_bar": "bodyweight", "plates": "other",
        }
        for eq, enum in cases.items():
            with self.subTest(equipment=eq):
                self.assertEqual(gen.equipment_of(exercise("x", equipment=eq)), enum)

    def test_missing_equipment_is_bodyweight(self):
        self.assertEqual(gen.equipment_of(exercise("x", equipment=None)), "bodyweight")

    def test_unmapped_ids_fail_listing_them(self):
        exercises = [exercise("a", primary=("mystery_muscle",)), exercise("b", equipment="teleporter")]
        with self.assertRaises(gen.CatalogError) as ctx:
            gen.build_rows(exercises)
        message = str(ctx.exception)
        self.assertIn("mystery_muscle", message)
        self.assertIn("teleporter", message)

    def test_every_mapped_value_is_a_valid_enum_label(self):
        groups = {"chest", "back", "shoulders", "biceps", "triceps", "forearms", "quads", "hamstrings",
                  "glutes", "calves", "core", "full_body"}
        equipment = {"barbell", "dumbbell", "machine", "cable", "bodyweight", "kettlebell", "band", "other"}
        self.assertLessEqual(set(gen.MUSCLE_TO_GROUP.values()) | set(gen.BODY_PART_TO_GROUP.values()), groups)
        self.assertLessEqual(set(gen.EQUIPMENT_TO_ENUM.values()), equipment)


class GeneratorTest(unittest.TestCase):
    def test_sql_is_idempotent_sorted_and_has_no_names(self):
        rows = gen.build_rows([exercise("zeta"), exercise("alfa", equipment="dumbbell")])
        sql = gen.render_sql(rows)
        self.assertIn("on conflict (key) where key is not null do nothing", sql)
        self.assertLess(sql.index("'alfa'"), sql.index("'zeta'"))
        self.assertNotIn("Nombre sintetico", sql)
        self.assertNotIn("Descripcion", sql)

    def test_cli_writes_the_migration_and_rejects_a_wrong_schema(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = os.path.join(tmp, "good")
            write_bundle(bundle, [exercise("press-banca")])
            out = os.path.join(tmp, "m.sql")
            self.assertEqual(gen.main([bundle, "--out", out]), 0)
            with open(out, encoding="utf-8") as f:
                self.assertIn("('press-banca', 'chest', 'barbell', 'weight_reps', false, null)", f.read())
            bad = os.path.join(tmp, "bad")
            write_bundle(bad, [exercise("a")], schema_version=2)
            self.assertEqual(gen.main([bad, "--out", out]), 1)
            self.assertEqual(gen.main([os.path.join(tmp, "missing"), "--out", out]), 1)


class MetricTypeTest(unittest.TestCase):
    def test_documented_examples_hold(self):
        for (key, eq), expected in gen.SELF_CHECK_EXAMPLES.items():
            with self.subTest(key=key):
                self.assertEqual(gen.metric_type_of(key, eq), expected)

    def test_unclassifiable_bodyweight_key_fails(self):
        with self.assertRaises(gen.CatalogError):
            gen.build_metric_rows([("zzz-unknown-move", "core", "bodyweight")])

    def test_metrics_sql_skips_default_and_groups_per_metric(self):
        sql = gen.render_metrics_sql(
            [("bench-press", "weight_reps"), ("plank", "duration"), ("running", "distance_duration")])
        self.assertIn("set metric_type = 'duration'", sql)
        self.assertIn("'running'", sql)
        self.assertNotIn("bench-press", sql)


class ImportTest(unittest.TestCase):
    def test_copies_images_and_trims_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = os.path.join(tmp, "bundle")
            write_bundle(bundle, [exercise("press-banca"), exercise("plancha", images=("main",))])
            out = os.path.join(tmp, "out")
            count, images = imp.import_bundle(bundle, out)
            self.assertEqual((count, images), (2, 3))
            with open(os.path.join(out, "library.json"), encoding="utf-8") as f:
                lib = json.load(f)
            self.assertEqual(lib["schema_version"], 1)
            self.assertEqual(set(lib["exercises"]["press-banca"]),
                             {"name", "description", "instructions", "tips", "poses"})
            self.assertEqual(lib["exercises"]["press-banca"]["poses"], ["start", "peak"])
            self.assertEqual(lib["exercises"]["plancha"]["poses"], ["start"])
            names = set(os.listdir(os.path.join(out, "images")))
            self.assertEqual(names, {"press-banca-start.webp", "press-banca-peak.webp", "plancha-start.webp"})

    def test_is_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = os.path.join(tmp, "bundle")
            write_bundle(bundle, [exercise("a")])
            out = os.path.join(tmp, "out")
            imp.import_bundle(bundle, out)
            with open(os.path.join(out, "library.json"), "rb") as f:
                first = f.read()
            imp.import_bundle(bundle, out)
            with open(os.path.join(out, "library.json"), "rb") as f:
                self.assertEqual(f.read(), first)

    def test_refuses_missing_bundle_or_wrong_schema(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(imp.ImportError_):
                imp.import_bundle(os.path.join(tmp, "nope"), os.path.join(tmp, "out"))
            bad = os.path.join(tmp, "bad")
            write_bundle(bad, [exercise("a")], schema_version=4)
            with self.assertRaises(imp.ImportError_):
                imp.import_bundle(bad, os.path.join(tmp, "out"))


if __name__ == "__main__":
    unittest.main()
