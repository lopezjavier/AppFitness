#!/usr/bin/env python3
"""Generate the exercise catalog migration from the RepDB free bundle.

Usage: python scripts/generate-exercise-catalog.py <bundle_dir> --out supabase/migrations/<ts>_exercise_catalog.sql

The SQL holds only the key plus our own enum values (ADR-38): no RepDB text and no asset. It is idempotent
(on conflict do nothing). Bundle ids that are not in the mapping tables below make the script fail, so a new
bundle never silently falls into "other".
"""
import argparse
import json
import os
import re
import sys

SUPPORTED_SCHEMA_VERSION = 3
KEY_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")

MUSCLE_TO_GROUP = {
    "pectoralis_major": "chest",
    "latissimus_dorsi": "back",
    "rhomboids": "back",
    "trapezius": "back",
    "erector_spinae": "back",
    "quadratus_lumborum": "back",
    "anterior_deltoid": "shoulders",
    "lateral_deltoid": "shoulders",
    "posterior_deltoid": "shoulders",
    "supraspinatus": "shoulders",
    "biceps_brachii": "biceps",
    "brachialis": "biceps",
    "triceps_brachii": "triceps",
    "forearms": "forearms",
    "forearm_flexors": "forearms",
    "forearm_extensors": "forearms",
    "brachioradialis": "forearms",
    "quadriceps": "quads",
    "hip_flexors": "quads",
    "adductors": "quads",
    "abductors": "quads",
    "hamstrings": "hamstrings",
    "gluteus_maximus": "glutes",
    "gluteus_medius": "glutes",
    "gastrocnemius": "calves",
    "soleus": "calves",
    "tibialis_anterior": "calves",
    "rectus_abdominis": "core",
    "obliques": "core",
    "transverse_abdominis": "core",
    "serratus_anterior": "core",
}

BODY_PART_TO_GROUP = {
    "chest": "chest",
    "back": "back",
    "shoulders": "shoulders",
    "upper_arms": "biceps",
    "lower_arms": "forearms",
    "upper_legs": "quads",
    "lower_legs": "calves",
    "core": "core",
    "full_body": "full_body",
}

EQUIPMENT_TO_ENUM = {
    # Barbell family.
    "barbell": "barbell", "ez_bar": "barbell", "trap_bar": "barbell", "landmine": "barbell",
    "dumbbell": "dumbbell",
    "cable": "cable",
    "kettlebell": "kettlebell",
    "resistance_band": "band", "loop_band": "band",
    # Machines (strength and cardio).
    "ab_crunch_machine": "machine", "assisted_pullup_machine": "machine", "back_extension_machine": "machine",
    "bicep_curl_machine": "machine", "chest_fly_machine": "machine", "chest_press_machine": "machine",
    "dip_machine": "machine", "donkey_calf_raise_machine": "machine", "glute_ham_developer": "machine",
    "hack_squat": "machine", "hip_abduction_machine": "machine", "hip_adduction_machine": "machine",
    "hip_thrust_machine": "machine", "lat_pulldown_machine": "machine", "lateral_raise_machine": "machine",
    "leg_curl": "machine", "leg_extension": "machine", "leg_press": "machine", "pec_deck": "machine",
    "plate_loaded_dip_machine": "machine", "plate_loaded_lateral_raise_machine": "machine",
    "preacher_curl_machine": "machine", "reverse_fly_machine": "machine",
    "seated_calf_raise_machine": "machine", "seated_row_machine": "machine",
    "shoulder_press_machine": "machine", "shrug_machine": "machine", "smith_machine": "machine",
    "standing_calf_raise_machine": "machine", "tricep_extension_machine": "machine",
    "air_bike": "machine", "elliptical": "machine", "rower": "machine", "ski_erg": "machine",
    "stair_climber": "machine", "stationary_bike": "machine", "treadmill": "machine",
    # Own body weight on a fixed structure.
    "pull_up_bar": "bodyweight", "dip_station": "bodyweight", "rings": "bodyweight",
    "suspension_trainer": "bodyweight",
    # Everything else the bundle names explicitly.
    "ab_wheel": "other", "battle_rope": "other", "climbing_rope": "other", "decline_bench": "other",
    "flat_bench": "other", "incline_bench": "other", "jump_rope": "other", "medicine_ball": "other",
    "plates": "other", "plyo_box": "other", "sandbag": "other", "sled": "other", "slam_ball": "other",
    "stability_ball": "other", "wrist_roller": "other",
}
MISSING_EQUIPMENT = "bodyweight"


class CatalogError(Exception):
    pass


def load_exercises(bundle_dir):
    path = os.path.join(bundle_dir, "free.es.json")
    if not os.path.isfile(path):
        raise CatalogError(f"{path} not found: pass the Biblioteca_Ejercicios_Free folder")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if data.get("schema_version") != SUPPORTED_SCHEMA_VERSION:
        raise CatalogError(
            f"unsupported schema_version {data.get('schema_version')!r} (expected {SUPPORTED_SCHEMA_VERSION})"
        )
    return data["exercises"]


def muscle_group_of(exercise):
    if exercise.get("body_part") == "full_body":
        return "full_body"
    primary = exercise.get("primary_muscles") or []
    if primary:
        return MUSCLE_TO_GROUP.get(primary[0])
    return BODY_PART_TO_GROUP.get(exercise.get("body_part"))


def equipment_of(exercise):
    eq = exercise.get("equipment")
    if not eq:
        return MISSING_EQUIPMENT
    return EQUIPMENT_TO_ENUM.get(eq)


def build_rows(exercises):
    rows, unmapped = [], []
    seen = set()
    for ex in exercises:
        key = ex["id"]
        if not KEY_RE.match(key):
            raise CatalogError(f"invalid exercise id {key!r}")
        if key in seen:
            raise CatalogError(f"duplicate exercise id {key!r}")
        seen.add(key)
        group, equipment = muscle_group_of(ex), equipment_of(ex)
        if group is None:
            unmapped.append(f"{key}: muscle/body_part {(ex.get('primary_muscles') or [None])[0]!r}/{ex.get('body_part')!r}")
        if equipment is None:
            unmapped.append(f"{key}: equipment {ex.get('equipment')!r}")
        rows.append((key, group, equipment))
    if unmapped:
        raise CatalogError("unmapped bundle ids (extend the tables in this script):\n  " + "\n  ".join(unmapped))
    return sorted(rows)


def render_sql(rows):
    lines = [
        "-- T-042 (US-09): RepDB free exercise catalog, generated by scripts/generate-exercise-catalog.py.",
        "-- Keys and our own enum values only: names, instructions and images live in the client bundle (ADR-38, ADR-39).",
        "-- Idempotent. Later catalog changes ship as a new migration.",
        "",
        "insert into public.exercises (key, muscle_group, equipment, metric_type, is_custom, name) values",
    ]
    values = [f"  ('{k}', '{g}', '{e}', 'weight_reps', false, null)" for k, g, e in rows]
    lines.append(",\n".join(values))
    lines.append("on conflict (key) where key is not null do nothing;")
    return "\n".join(lines) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser(prog="generate-exercise-catalog.py", description=__doc__.splitlines()[0])
    ap.add_argument("bundle_dir", help="Path to the Biblioteca_Ejercicios_Free folder.")
    ap.add_argument("--out", required=True, help="Migration file to write.")
    args = ap.parse_args(argv)
    try:
        rows = build_rows(load_exercises(os.path.abspath(args.bundle_dir)))
    except CatalogError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        f.write(render_sql(rows))
    print(f"Wrote {len(rows)} catalog rows to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
