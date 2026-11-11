import importlib.util
import json
import os
import re
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
            "quadriceps": "quads", "hip_flexors": "core", "adductors": "adductors", "abductors": "glutes",
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
            "barbell": "barbell", "ez_bar": "ez_bar", "trap_bar": "other", "landmine": "other",
            "dumbbell": "dumbbell", "cable": "cable", "kettlebell": "kettlebell", "loop_band": "band",
            "resistance_band": "band", "leg_press": "machine", "smith_machine": "machine",
            "pec_deck": "machine", "hack_squat": "machine", "chest_press_machine": "machine",
            "pull_up_bar": "other", "plates": "other",
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
                  "glutes", "calves", "core", "full_body", "adductors"}
        equipment = {"barbell", "dumbbell", "machine", "cable", "bodyweight", "kettlebell", "band", "other", "ez_bar",
                     "cardio_machine"}
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


REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG_SQL = os.path.join(REPO, "supabase", "migrations", "20261019130100_exercise_catalog.sql")
PATTERNS_CSV = os.path.join(REPO, "scripts", "exercise-movement-patterns.csv")
CATALOG_ROWS = [("alfa", "chest", "barbell"), ("beta", "back", "cable")]


class MovementPatternTest(unittest.TestCase):
    def _csv(self, tmp, body, header="key,movement_pattern\n"):
        path = os.path.join(tmp, "p.csv")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(header + body)
        return path

    def test_missing_catalogue_key_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            mapping = gen.load_patterns_csv(self._csv(tmp, "alfa,horizontal_push\n"))
            with self.assertRaises(gen.CatalogError) as ctx:
                gen.build_pattern_rows(CATALOG_ROWS, mapping)
        self.assertIn("beta", str(ctx.exception))

    def test_unknown_pattern_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(gen.CatalogError) as ctx:
                gen.load_patterns_csv(self._csv(tmp, "alfa,horizontal_push\nbeta,sideways\n"))
        self.assertIn("sideways", str(ctx.exception))

    def test_extra_csv_key_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            mapping = gen.load_patterns_csv(self._csv(tmp, "alfa,horizontal_push\nbeta,vertical_pull\ngamma,core\n"))
            with self.assertRaises(gen.CatalogError) as ctx:
                gen.build_pattern_rows(CATALOG_ROWS, mapping)
        self.assertIn("gamma", str(ctx.exception))

    def test_duplicate_key_and_bad_header_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(gen.CatalogError):
                gen.load_patterns_csv(self._csv(tmp, "alfa,core\nalfa,core\n"))
            with self.assertRaises(gen.CatalogError):
                gen.load_patterns_csv(self._csv(tmp, "alfa,core\n", header="k,p\n"))

    def test_render_groups_keys_per_pattern(self):
        sql = gen.render_patterns_sql(
            [("alfa", "horizontal_push"), ("beta", "vertical_pull"), ("gamma", "vertical_pull")])
        self.assertIn("set movement_pattern = 'vertical_pull'\nwhere not is_custom and key in (\n  'beta',\n  'gamma'\n);", sql)
        self.assertIn("movement_pattern = 'horizontal_push'", sql)
        self.assertNotIn("'core'", sql)

    def test_render_adds_hamstrings_as_secondary_of_every_hinge(self):
        sql = gen.render_patterns_sql([("alfa", "hinge")])
        self.assertIn("movement_pattern = 'hinge' and muscle_group <> 'hamstrings'", sql)

    def test_shoulder_abduction_is_an_allowed_pattern(self):
        self.assertIn("shoulder_abduction", gen.MOVEMENT_PATTERNS)

    def test_render_sql_writes_the_column_only_with_patterns(self):
        self.assertNotIn("movement_pattern", gen.render_sql(CATALOG_ROWS))
        sql = gen.render_sql(CATALOG_ROWS, {"alfa": "core", "beta": "squat"})
        self.assertIn("name, movement_pattern)", sql)
        self.assertIn("('alfa', 'chest', 'barbell', 'weight_reps', false, null, 'core')", sql)

    def test_committed_csv_covers_the_real_catalogue_exactly(self):
        rows = gen.rows_from_catalog_sql(CATALOG_SQL)
        mapping = gen.load_patterns_csv(PATTERNS_CSV)
        pattern_rows = gen.build_pattern_rows(rows, mapping)
        self.assertEqual(len(pattern_rows), len(rows))
        self.assertEqual(dict(pattern_rows)["bench-press"], "horizontal_push")

    def test_cli_writes_patterns_from_the_catalogue_sql(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = os.path.join(tmp, "p.sql")
            code = gen.main(["--catalog-sql", CATALOG_SQL, "--patterns-csv", PATTERNS_CSV, "--patterns-out", out])
            self.assertEqual(code, 0)
            with open(out, encoding="utf-8") as f:
                self.assertIn("update public.exercises set movement_pattern", f.read())


class StrengthFlagTest(unittest.TestCase):
    def test_holds_cardio_stretches_and_impact_do_not_count(self):
        for key, metric, pattern, equipment in (
                ("wall-sit", "duration", "squat", "bodyweight"), ("plank", "duration", "core", "bodyweight"),
                ("dumbbell-farmers-walk", "weight_duration", "carry", "dumbbell"),
                ("stationary-bike", "distance_duration", "cardio", "cardio_machine"),
                ("jump-squat", "reps_only", "squat", "bodyweight"), ("cat-cow", "reps_only", "other", "bodyweight")):
            self.assertFalse(gen.counts_as_strength(key, metric, pattern, equipment), key)

    def test_loaded_and_bodyweight_dynamic_work_counts(self):
        for key, metric, pattern, equipment in (
                ("squat", "weight_reps", "squat", "barbell"), ("push-ups", "bodyweight", "horizontal_push", "bodyweight"),
                ("glute-bridge", "reps_only", "hinge", "bodyweight"), ("clamshells", "reps_only", "isolation", "band")):
            self.assertTrue(gen.counts_as_strength(key, metric, pattern, equipment), key)

    def test_cli_writes_the_statement(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = os.path.join(tmp, "strength.sql")
            self.assertEqual(gen.main(["--catalog-sql", CATALOG_SQL, "--metrics-out", os.path.join(tmp, "m.sql"),
                                       "--strength-out", out]), 0)
            with open(out, encoding="utf-8") as f:
                self.assertEqual(f.read(), gen.render_strength_sql())


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


class EquipmentMappingTest(unittest.TestCase):
    """T-201 (ADR-92): equipment items of the generator."""

    def _rows(self, *specs):
        return gen.build_rows([exercise(key, equipment=eq) for key, eq in specs])

    def test_ez_bar_key_is_ez_bar_and_plain_barbell_stays_barbell(self):
        rows = dict((k, e) for k, _g, e in self._rows(("ez-bar-curl", "ez_bar"), ("barbell-curl", "barbell")))
        self.assertEqual(rows, {"ez-bar-curl": "ez_bar", "barbell-curl": "barbell"})

    def test_small_implements_and_structures_are_other(self):
        for bundle in ("landmine", "trap_bar", "pull_up_bar", "dip_station", "rings", "suspension_trainer"):
            self.assertEqual(self._rows(("x", bundle))[0][2], "other", bundle)

    def test_every_cardio_machine_is_one_value(self):
        for bundle in ("air_bike", "stationary_bike", "treadmill", "elliptical", "rower", "ski_erg", "stair_climber"):
            self.assertEqual(self._rows(("x", bundle))[0][2], "cardio_machine", bundle)

    def test_key_overrides_and_group_fix(self):
        rows = {k: (g, e) for k, g, e in self._rows(
            ("v-bar-lat-pulldown", "lat_pulldown_machine"), ("human-flag", None),
            ("smith-machine-reverse-grip-bent-over-row", "smith_machine"))}
        self.assertEqual(rows["v-bar-lat-pulldown"][1], "cable")
        self.assertEqual(rows["human-flag"][1], "other")
        self.assertEqual(rows["smith-machine-reverse-grip-bent-over-row"][0], "back")

    def test_leg_filing_overrides_map_each_list_to_its_group(self):
        expected = {"squat": "quads", "walking-lunge": "quads", "leg-press": "quads", "step-ups": "quads",
                    "clamshells": "glutes", "fire-hydrant": "glutes", "side-lying-hip-abduction": "glutes",
                    "hip-adduction": "adductors", "banded-adductor-stretch": "adductors",
                    "sit-ups": "core", "hanging-leg-raise": "core", "mountain-climbers": "core",
                    "pilates-side-bend": "core", "happy-baby": "adductors",
                    "garland-pose": "adductors", "seated-straddle-stretch": "adductors",
                    "lateral-leg-swing": "adductors", "downward-dog-knee-tuck": "core",
                    "downward-dog-to-knee-drive": "core", "kettlebell-sumo-high-pull": "glutes",
                    "wide-legged-forward-fold": "hamstrings"}
        # The bundle files these under glutes, quads (abductors, adductors, hip flexors) or core.
        rows = {k: g for k, g, _ in self._rows(*((k, None) for k in expected))}
        self.assertEqual(rows, expected)
        for keys, group in ((gen.KNEE_DOMINANT_KEYS, "quads"), (gen.HIP_ABDUCTION_KEYS, "glutes"),
                            (gen.HIP_FLEXION_KEYS, "core"), (gen.HIP_ADDUCTION_KEYS, "adductors")):
            self.assertEqual(len(gen.HIP_ADDUCTION_KEYS), 11)
            self.assertEqual(len(gen.HIP_FLEXION_KEYS), 26)
            for key in keys:
                self.assertEqual(gen.KEY_GROUP_OVERRIDES[key], group, key)

    def test_leg_key_lists_are_disjoint_and_match_the_live_migration(self):
        lists = [gen.KNEE_DOMINANT_KEYS, gen.HIP_ABDUCTION_KEYS, gen.HIP_FLEXION_KEYS, gen.HIP_ADDUCTION_KEYS]
        self.assertEqual(sum(len(x) for x in lists), len(set().union(*lists)))
        path = os.path.join(os.path.dirname(__file__), "..", "supabase", "migrations",
                            "20261229100100_catalogue_leg_muscle_groups.sql")
        with open(path, encoding="utf-8") as f:
            blocks = re.findall(r"key in \((.*?)\)\s*;", f.read(), re.S)
        self.assertEqual([set(re.findall(r"'([^']+)'", b)) for b in blocks[:4]], lists)

    def test_knee_dominant_keys_get_glutes_as_secondary_in_the_rendered_sql(self):
        sql = gen.render_patterns_sql([("alfa", "core")])
        self.assertIn("set secondary_muscles = secondary_muscles || array['glutes']::public.muscle_group[]", sql)
        self.assertIn("  'walking-lunge'", sql)
        self.assertIn("  'squat'", sql)
        self.assertNotIn("  'sit-ups'", sql)
        self.assertEqual(set(gen.KEY_SECONDARY_OVERRIDES),
                         gen.KNEE_DOMINANT_KEYS | gen.STRAIGHT_KNEE_HINGE_KEYS | {"kettlebell-sumo-high-pull"})
        self.assertEqual(gen.KEY_SECONDARY_OVERRIDES["kettlebell-sumo-high-pull"], ["hamstrings"])
        self.assertIn("array['hamstrings']::public.muscle_group[]\nwhere not is_custom and key in (\n  'kettlebell-sumo-high-pull'", sql)

    def test_straight_knee_hinges_are_hamstrings_with_glutes_secondary_and_match_the_migration(self):
        keys = gen.STRAIGHT_KNEE_HINGE_KEYS
        self.assertEqual(len(keys), 18)
        for key in keys:
            self.assertEqual(gen.KEY_GROUP_OVERRIDES[key], "hamstrings", key)
            self.assertEqual(gen.KEY_SECONDARY_OVERRIDES[key], ["glutes"], key)
        for bent in ("deadlift", "sumo-deadlift", "hex-bar-deadlift", "deficit-deadlift", "pause-deadlift"):
            self.assertNotIn(bent, gen.KEY_GROUP_OVERRIDES)
        self.assertTrue(keys.isdisjoint(gen.KNEE_DOMINANT_KEYS | gen.HIP_FLEXION_KEYS | gen.HIP_ABDUCTION_KEYS))
        path = os.path.join(os.path.dirname(__file__), "..", "supabase", "migrations",
                            "20261229100500_catalogue_straight_knee_hinge.sql")
        with open(path, encoding="utf-8") as f:
            block = re.search(r"key in \((.*?)\)\s*;", f.read(), re.S).group(1)
        self.assertEqual(set(re.findall(r"'([^']+)'", block)), keys)

    def test_no_old_cardio_value_is_produced(self):
        old = {"cardio_bike", "treadmill", "elliptical", "rower", "cardio_other"}
        self.assertTrue(old.isdisjoint(gen.EQUIPMENT_TO_ENUM.values()))


class EzBarNameTest(unittest.TestCase):
    def test_barra_ez_becomes_barra_z_in_every_text(self):
        ex = exercise("ez-bar-pullover")
        ex["name"] = "Pullover con Barra EZ"
        ex["instructions"] = ["Sujeta la Barra EZ."]
        out = imp.trim(ex)
        self.assertEqual(out["name"], "Pullover con Barra Z")
        self.assertEqual(out["instructions"], ["Sujeta la Barra Z."])
