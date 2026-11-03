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
    "hip_flexors": "core",
    "adductors": "adductors",
    "abductors": "glutes",
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
    # T-201 (ADR-92): the EZ bar has its own item; landmine and hex bar are "other".
    "barbell": "barbell", "ez_bar": "ez_bar", "trap_bar": "other", "landmine": "other",
    "dumbbell": "dumbbell",
    "cable": "cable",
    "kettlebell": "kettlebell",
    "resistance_band": "band", "loop_band": "band",
    # Strength machines.
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
    # One "Máquina de cardio" (T-184, ADR-85; collapsed by T-201, ADR-92); the strength machines above stay `machine`.
    "air_bike": "cardio_machine", "stationary_bike": "cardio_machine", "treadmill": "cardio_machine",
    "elliptical": "cardio_machine", "rower": "cardio_machine", "ski_erg": "cardio_machine",
    "stair_climber": "cardio_machine",
    # Fixed structures and small implements are "other" (T-201); `bodyweight` means floor or wall only.
    "pull_up_bar": "other", "dip_station": "other", "rings": "other", "suspension_trainer": "other",
    # Everything else the bundle names explicitly.
    "ab_wheel": "other", "battle_rope": "other", "climbing_rope": "other", "decline_bench": "other",
    "flat_bench": "other", "incline_bench": "other", "jump_rope": "other", "medicine_ball": "other",
    "plates": "other", "plyo_box": "other", "sandbag": "other", "sled": "other", "slam_ball": "other",
    "stability_ball": "other", "wrist_roller": "other",
}
MISSING_EQUIPMENT = "bodyweight"

# Per-key overrides of the bundle's equipment (T-201, planning/07-coach-design.md section 3). The bundle files these
# under bodyweight/band/machine; the rule moves them.
KEY_EQUIPMENT_OVERRIDES = {
    "human-flag": "other", "back-extension": "other", "band-assisted-pull-ups": "other",
    "straight-bar-dips": "other", "captains-chair-knee-raise": "other", "captains-chair-leg-raise": "other",
    "toes-to-bar": "other", "muscle-ups": "other", "dips": "other", "weighted-dips": "other",
    "t-bar-row": "other", "hex-bar-deadlift": "other",
    "behind-the-neck-lat-pulldown": "cable", "v-bar-lat-pulldown": "cable",
}
# T-211 (07 section 3 "Taxonomy" > "Lower-body filing"): the bundle files squats and lunges under glutes, and hip
# abduction, hip flexion and hip adduction under quads. The rule re-files them; migration
# 20261229100100_catalogue_leg_muscle_groups.sql applies the same lists to the live database.
KNEE_DOMINANT_KEYS = {
    "banded-squat", "barbell-bulgarian-split-squat", "barbell-lunge", "barbell-reverse-lunge",
    "bodyweight-reverse-lunge", "bodyweight-squat", "box-jump", "box-squat", "bulgarian-split-squat",
    "close-stance-leg-press", "cossack-squat", "db-lunge", "db-somersault-squat", "db-squat", "db-sumo-squat",
    "deep-squat-hold", "dumbbell-front-squat", "dumbbell-pistol-squat", "dumbbell-split-squat", "front-squat",
    "goblet-squat", "hack-squat", "heel-elevated-squat", "high-foot-leg-press", "horizontal-leg-press",
    "jump-squat", "kettlebell-bulgarian-split-squat", "kettlebell-goblet-lunge", "kettlebell-pistol-squat",
    "kettlebell-reverse-lunge", "kettlebell-rotational-lunge", "kettlebell-squat", "leg-press", "lunge",
    "one-arm-kettlebell-front-squat", "overhead-squat", "pause-squat", "pistol-squat", "plyo-lunge",
    "reverse-lunge", "side-lunge", "single-leg-press", "smith-machine-bulgarian-split",
    "smith-machine-front-squat", "smith-machine-reverse-lunge", "smith-machine-split-squat",
    "smith-machine-squat", "split-squat", "squat", "squat-hold", "squat-pulse", "stability-ball-wall-squat",
    "step-ups", "sumo-squat", "trx-lunge", "trx-pistol-squat", "trx-squat", "walking-lunge", "wall-sit",
    "wide-stance-leg-press", "zercher-squat",
}
HIP_ABDUCTION_KEYS = {
    "hip-abduction", "banded-clamshell", "banded-fire-hydrant", "banded-seated-hip-abduction",
    "banded-standing-hip-abduction", "clamshells", "clamshells-hold", "fire-hydrant",
    "side-lying-hip-abduction", "side-lying-hip-abduction-hold",
}
HIP_FLEXION_KEYS = {
    "captains-chair-knee-raise", "captains-chair-leg-raise", "hanging-knee-raise", "hanging-l-sit",
    "hanging-leg-raise", "hanging-tuck-hold", "lying-leg-raise", "ring-l-sit", "v-sit", "l-sit", "sit-ups",
    "v-ups", "toes-to-bar", "flutter-kicks", "scissor-kicks", "bench-hollow-hold", "hollow-body-hold",
    "boat-pose", "six-inch-hold", "plank-knee-tuck", "stability-ball-knee-tuck", "bench-leg-pull-in",
    "mountain-climbers", "pilates-side-bend", "downward-dog-to-knee-drive", "downward-dog-knee-tuck",
}
HIP_ADDUCTION_KEYS = {
    "hip-adduction", "banded-standing-hip-adduction", "side-lying-hip-adduction",
    "side-lying-hip-adduction-hold", "banded-adductor-stretch", "bench-adductor-stretch", "butterfly-stretch",
    "seated-straddle-stretch", "lateral-leg-swing", "happy-baby", "garland-pose",
}
# Single keys filed by the joint action (07 section 3 "Keys, not names"), with the secondary they gain.
KEY_ONLY_GROUPS = {"kettlebell-sumo-high-pull": "glutes", "wide-legged-forward-fold": "hamstrings"}
KEY_GROUP_OVERRIDES = {
    "ez-bar-reverse-grip-row": "back", "smith-machine-reverse-grip-bent-over-row": "back",
    **{k: "quads" for k in KNEE_DOMINANT_KEYS},
    **{k: "glutes" for k in HIP_ABDUCTION_KEYS},
    **{k: "core" for k in HIP_FLEXION_KEYS},
    **{k: "adductors" for k in HIP_ADDUCTION_KEYS},
    **KEY_ONLY_GROUPS,
}
# Secondary muscles the catalogue migrations add on top of the bundle (rendered by render_patterns_sql).
KEY_SECONDARY_OVERRIDES = {**{k: ["glutes"] for k in KNEE_DOMINANT_KEYS}, "kettlebell-sumo-high-pull": ["hamstrings"]}


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
        group = KEY_GROUP_OVERRIDES.get(key, group)
        equipment = KEY_EQUIPMENT_OVERRIDES.get(key, equipment)
        if group is None:
            unmapped.append(f"{key}: muscle/body_part {(ex.get('primary_muscles') or [None])[0]!r}/{ex.get('body_part')!r}")
        if equipment is None:
            unmapped.append(f"{key}: equipment {ex.get('equipment')!r}")
        rows.append((key, group, equipment))
    if unmapped:
        raise CatalogError("unmapped bundle ids (extend the tables in this script):\n  " + "\n  ".join(unmapped))
    return sorted(rows)


def render_sql(rows, patterns=None):
    """patterns: optional {key: movement_pattern}; when given the insert also writes movement_pattern (T-150)."""
    lines = [
        "-- T-042 (US-09): RepDB free exercise catalog, generated by scripts/generate-exercise-catalog.py.",
        "-- Keys and our own enum values only: names, instructions and images live in the client bundle (ADR-38, ADR-39).",
        "-- Idempotent. Later catalog changes ship as a new migration.",
        "",
        "insert into public.exercises (key, muscle_group, equipment, metric_type, is_custom, name) values",
    ]
    values = [f"  ('{k}', '{g}', '{e}', 'weight_reps', false, null)" for k, g, e in rows]
    if patterns is not None:
        lines[-1] = lines[-1].replace("metric_type, is_custom, name)", "metric_type, is_custom, name, movement_pattern)")
        values = [f"  ('{k}', '{g}', '{e}', 'weight_reps', false, null, '{patterns[k]}')" for k, g, e in rows]
    lines.append(",\n".join(values))
    lines.append("on conflict (key) where key is not null do nothing;")
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------------------
# Metric types (T-092, US-31). Classified from the key (+ our equipment enum): the bundle carries no usable
# category field. Precedence, first match wins; a bodyweight-equipment key matched by no rule fails the script.
# ---------------------------------------------------------------------------
ASSISTED_SUBSTR = ("assisted",)
WEIGHTED_PREFIX = ("weighted-",)
WEIGHT_DURATION_KEYS = {"plate-pinch"}
WEIGHT_DURATION_SUFFIX = ("-farmers-walk", "-carry")
FLOORS_DURATION_KEYS = {"stair-climber"}
STEPS_DURATION_KEYS = set()  # custom exercises only today; add a key here if the bundle gains a step counter
DISTANCE_DURATION_KEYS = {
    "air-bike", "stationary-bike", "rowing-machine", "ski-erg", "elliptical-trainer", "treadmill-running",
    "incline-treadmill-walk", "running", "walking",
}
# Timed: isometric holds, stretches and yoga poses, timed cardio without distance.
DURATION_NOT = {"plank-knee-tuck", "reverse-plank-dips", "downward-dog-to-plank"}
DURATION_KEYS = {
    "dead-hang", "ring-dead-hang", "flexed-arm-hang", "hanging-l-sit", "hanging-tuck-hold", "l-sit", "ring-l-sit",
    "wall-sit", "jump-rope", "jumping-jacks", "mountain-climbers", "battle-ropes", "planche", "front-lever",
    "back-lever", "human-flag", "handstand-hold", "isometric-neck-side", "six-inch-hold", "boat-pose",
    "savasana", "happy-baby", "legs-up-the-wall", "downward-dog", "three-legged-dog", "thread-the-needle",
    "supported-shoulderstand", "standing-split", "low-lunge", "crescent-lunge", "revolved-crescent-lunge",
    "extended-side-angle", "warrior-one", "warrior-two", "warrior-three", "standing-forward-fold",
    "standing-forward-fold-to-half-lift", "seated-forward-fold", "wide-legged-forward-fold",
    "seated-spinal-twist", "supine-spinal-twist", "low-lunge-to-half-split", "childs-pose", "pigeon-stretch",
    "bird-dog-hold", "squat-hold",
}
DURATION_SUBSTR = ("plank", "-hold", "stretch", "-pose")
# Bodyweight moves with no meaningful load (impact, trunk flexion, mobility, flows).
REPS_ONLY_KEYS = {
    "crunches", "bicycle-crunch", "reverse-crunches", "cross-body-crunch", "decline-crunch", "sit-ups",
    "jackknife-sit-up", "box-jump", "jump-squat", "burpees", "rope-climb", "plank-knee-tuck", "reverse-plank-dips",
    "downward-dog-to-plank", "ab-wheel-rollout",
    "battle-rope-double-slam", "bear-crawl", "heel-to-toe-walk", "heel-flicks", "heel-touches", "high-knees",
    "flutter-kicks", "scissor-kicks", "v-ups", "v-sit", "russian-twist", "torso-twists", "cocoons", "dead-bug",
    "bird-dog", "superman", "cat-cow", "inchworm", "supine-windshield-wipers", "lateral-leg-swing",
    "leg-swing-front-to-back", "squat-pulse", "fire-hydrant", "clamshells", "glute-kickback", "plyo-lunge",
    "dragon-flag", "lying-leg-raise", "standing-side-bend", "standing-side-bend-flow", "thread-the-needle-flow",
    "half-kneeling-hip-flexor-rock", "reverse-tabletop-hip-pulses", "downward-dog-knee-tuck",
    "downward-dog-pedal", "downward-dog-to-knee-drive", "downward-dog-to-low-lunge",
    "downward-dog-to-upward-dog", "upward-dog", "thoracic-bridge", "bench-leg-pull-in", "ball-leg-curl",
    "ball-pike", "stability-ball-knee-tuck", "stability-ball-hip-bridge", "side-lying-hip-abduction",
    "side-lying-hip-adduction", "glute-bridge", "single-leg-glute-bridge",

}
REPS_ONLY_PREFIX = ("pilates-",)
# Own body weight is the (main) load: reps + optional added weight.
BODYWEIGHT_SUBSTR = (
    "pull-up", "chin-up", "dips", "push-up", "muscle-up", "inverted-row", "ring-row", "trx-", "squat", "lunge",
    "step-ups", "hanging-", "toes-to-bar", "captains-chair", "calf-raise", "back-extension", "reverse-nordic-curl",
    "bodyweight-", "ring-face-pull", "pike-push",
)
LOADED_EQUIPMENT = {
    "barbell", "ez_bar", "dumbbell", "cable", "machine", "kettlebell", "band", "other", "cardio_machine",
}
CARDIO_EQUIPMENT = {"cardio_machine"}
METRIC_TYPES = (
    "weight_reps", "reps_only", "duration", "weight_duration", "distance_duration", "bodyweight", "assisted",
    "weighted", "steps_duration", "floors_duration",
)


def metric_type_of(key, equipment):
    """Metric type of a catalog key; raises CatalogError for a bodyweight key no rule covers."""
    if any(s in key for s in ASSISTED_SUBSTR):
        return "assisted"
    if key.startswith(WEIGHTED_PREFIX):
        return "weighted"
    if key in WEIGHT_DURATION_KEYS or key.endswith(WEIGHT_DURATION_SUFFIX):
        return "weight_duration"
    if key in FLOORS_DURATION_KEYS:
        return "floors_duration"
    if key in STEPS_DURATION_KEYS:
        return "steps_duration"
    if key in DISTANCE_DURATION_KEYS:
        return "distance_duration"
    if key in REPS_ONLY_KEYS or key.startswith(REPS_ONLY_PREFIX):
        return "reps_only"
    if key in DURATION_KEYS:
        return "duration"
    if key not in DURATION_NOT and any(s in key for s in DURATION_SUBSTR) and equipment != "machine" and equipment not in CARDIO_EQUIPMENT:
        return "duration"
    if any(s in key for s in BODYWEIGHT_SUBSTR) and equipment in ("bodyweight", "other"):
        return "bodyweight"
    if equipment in LOADED_EQUIPMENT:
        return "weight_reps"
    raise CatalogError(f"{key}: no metric type rule (equipment {equipment!r}); extend the metric tables")


def build_metric_rows(rows):
    """rows: (key, group, equipment) tuples. Returns sorted (key, metric_type); fails on unclassified keys."""
    out, bad = [], []
    for key, _group, equipment in rows:
        try:
            out.append((key, metric_type_of(key, equipment)))
        except CatalogError as e:
            bad.append(str(e))
    if bad:
        raise CatalogError("unclassified catalog keys:\n  " + "\n  ".join(bad))
    return sorted(out)


def render_metrics_sql(metric_rows):
    lines = [
        "-- T-092 (US-31): real metric_type for the catalog rows, generated by scripts/generate-exercise-catalog.py",
        "-- (--metrics-out). weight_reps is the column default, so only the other metric types are written.",
        "-- Idempotent.",
    ]
    for metric in METRIC_TYPES:
        keys = [k for k, m in metric_rows if m == metric]
        if metric == "weight_reps" or not keys:
            continue
        lines += ["", f"update public.exercises set metric_type = '{metric}'", "where not is_custom and key in ("]
        lines.append(",\n".join(f"  '{k}'" for k in keys))
        lines.append(");")
    return "\n".join(lines) + "\n"


def render_metrics_table(metric_rows):
    """Markdown table grouped by metric, for the PR body."""
    out = ["# T-092 catalog metric mapping", ""]
    for metric in METRIC_TYPES:
        keys = [k for k, m in metric_rows if m == metric]
        out.append(f"## {metric} ({len(keys)})")
        out.append(", ".join(f"`{k}`" for k in keys) if keys else "_none_")
        out.append("")
    return "\n".join(out)


SELF_CHECK_EXAMPLES = {
    ("pull-up", "bodyweight"): "bodyweight", ("plank", "bodyweight"): "duration",
    ("treadmill-running", "cardio_machine"): "distance_duration", ("assisted-pull-ups", "machine"): "assisted",
    ("weighted-dips", "bodyweight"): "weighted", ("dumbbell-farmers-walk", "dumbbell"): "weight_duration",
    ("stair-climber", "cardio_machine"): "floors_duration", ("crunches", "bodyweight"): "reps_only",
    ("bench-press", "barbell"): "weight_reps",
}


def self_check():
    for (key, eq), expected in SELF_CHECK_EXAMPLES.items():
        got = metric_type_of(key, eq)
        if got != expected:
            raise CatalogError(f"self-check: {key} -> {got}, expected {expected}")
    try:
        metric_type_of("zzz-unknown-move", "bodyweight")
    except CatalogError:
        return
    raise CatalogError("self-check: an unclassifiable bodyweight key did not fail")


# ---------------------------------------------------------------------------
# Movement patterns (T-150, ADR-78). Reviewed mapping key -> pattern kept in scripts/exercise-movement-patterns.csv;
# the script only validates it against the catalogue and renders SQL.
# ---------------------------------------------------------------------------
MOVEMENT_PATTERNS = (
    "squat", "hinge", "lunge", "horizontal_push", "vertical_push", "horizontal_pull", "vertical_pull", "carry",
    "core", "isolation", "cardio", "other", "shoulder_abduction",
)


def load_patterns_csv(path):
    """Returns {key: pattern}; fails on a bad header, duplicate key or unknown pattern."""
    mapping, errors = {}, []
    with open(path, encoding="utf-8", newline="") as f:
        lines = [ln.rstrip("\r\n") for ln in f if ln.strip()]
    if not lines or lines[0] != "key,movement_pattern":
        raise CatalogError(f"{path}: header must be 'key,movement_pattern'")
    for n, line in enumerate(lines[1:], start=2):
        parts = line.split(",")
        if len(parts) != 2:
            errors.append(f"line {n}: expected 'key,movement_pattern'")
            continue
        key, pattern = parts[0].strip(), parts[1].strip()
        if key in mapping:
            errors.append(f"line {n}: duplicate key {key!r}")
        if pattern not in MOVEMENT_PATTERNS:
            errors.append(f"line {n}: {key}: unknown movement pattern {pattern!r}")
        mapping[key] = pattern
    if errors:
        raise CatalogError("invalid patterns CSV:\n  " + "\n  ".join(errors))
    return mapping


def build_pattern_rows(rows, mapping):
    """rows: catalogue (key, group, equipment). Fails on catalogue keys missing from the CSV and on extra CSV keys."""
    catalog_keys = {r[0] for r in rows}
    missing = sorted(catalog_keys - set(mapping))
    extra = sorted(set(mapping) - catalog_keys)
    problems = []
    if missing:
        problems.append("catalogue keys without a pattern:\n  " + "\n  ".join(missing))
    if extra:
        problems.append("CSV keys not in the catalogue:\n  " + "\n  ".join(extra))
    if problems:
        raise CatalogError("\n".join(problems))
    return sorted((k, mapping[k]) for k in catalog_keys)


def render_patterns_sql(pattern_rows):
    lines = [
        "-- T-150 (ADR-78): movement_pattern for the catalogue rows, generated by scripts/generate-exercise-catalog.py",
        "-- (--patterns-out) from scripts/exercise-movement-patterns.csv. Idempotent.",
    ]
    for pattern in MOVEMENT_PATTERNS:
        keys = [k for k, p in pattern_rows if p == pattern]
        if not keys:
            continue
        lines += ["", f"update public.exercises set movement_pattern = '{pattern}'", "where not is_custom and key in ("]
        lines.append(",\n".join(f"  '{k}'" for k in keys))
        lines.append(");")
    # T-205: every hinge lists hamstrings as primary or secondary (07 section 3 "Taxonomy"), so the fractional
    # volume count does not undercount them.
    lines += ["", "update public.exercises set secondary_muscles = secondary_muscles || array['hamstrings']::public.muscle_group[]",
              "where not is_custom and movement_pattern = 'hinge' and muscle_group <> 'hamstrings'",
              "  and not ('hamstrings' = any (secondary_muscles));"]
    # T-211: knee-dominant compounds list glutes as secondary (quads primary, see KEY_SECONDARY_OVERRIDES).
    for secondary in sorted({g for gs in KEY_SECONDARY_OVERRIDES.values() for g in gs}):
        keys = sorted(k for k, gs in KEY_SECONDARY_OVERRIDES.items() if secondary in gs)
        lines += ["", f"update public.exercises set secondary_muscles = secondary_muscles || array['{secondary}']::public.muscle_group[]",
                  "where not is_custom and key in ("]
        lines.append(",\n".join(f"  '{k}'" for k in keys))
        lines.append(f") and not ('{secondary}' = any (secondary_muscles));")
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------------------
# Strength flag (T-211, 07 section 3 "What H1 counts"): exercises.counts_as_strength. False for timed or distance
# metrics (holds, planks, stretches, carries, cardio), cardio-pattern or cardio-machine keys, and the reps-only
# mobility, flow and impact keys below. Custom rows keep the default (the coach decides them by metric type).
# ---------------------------------------------------------------------------
NON_STRENGTH_METRICS = ("duration", "weight_duration", "distance_duration", "floors_duration", "steps_duration")
NON_STRENGTH_KEYS = {
    "battle-rope-double-slam", "bear-crawl", "box-jump", "burpees", "cat-cow", "downward-dog-knee-tuck",
    "downward-dog-pedal", "downward-dog-to-knee-drive", "downward-dog-to-low-lunge", "downward-dog-to-plank",
    "downward-dog-to-upward-dog", "half-kneeling-hip-flexor-rock", "heel-flicks", "heel-to-toe-walk", "high-knees",
    "inchworm", "jump-squat", "lateral-leg-swing", "leg-swing-front-to-back", "plyo-lunge", "standing-side-bend",
    "standing-side-bend-flow", "thoracic-bridge", "thread-the-needle-flow", "upward-dog",
}


def counts_as_strength(key, metric_type, movement_pattern, equipment):
    """Whether a catalogue key's sets add to the coach's H1 weekly count."""
    if metric_type in NON_STRENGTH_METRICS or movement_pattern == "cardio" or equipment in CARDIO_EQUIPMENT:
        return False
    return key not in NON_STRENGTH_KEYS


def render_strength_sql():
    """The update statement of the strength flag (same rule as counts_as_strength, evaluated in SQL)."""
    metrics = ", ".join(f"'{m}'" for m in NON_STRENGTH_METRICS)
    keys = ",\n".join(f"  '{k}'" for k in sorted(NON_STRENGTH_KEYS))
    return (
        "update public.exercises\nset counts_as_strength = false\nwhere not is_custom\n"
        f"  and (metric_type in ({metrics})\n"
        "       or movement_pattern = 'cardio'\n       or equipment = 'cardio_machine'\n"
        f"       or key in (\n{keys}\n));\n"
    )


ROW_RE = re.compile(r"^\s*\('([^']+)', '([^']+)', '([^']+)', '[^']+', false, null\)")


def rows_from_catalog_sql(path):
    with open(path, encoding="utf-8") as f:
        rows = [m.groups() for m in (ROW_RE.match(line) for line in f) if m]
    if not rows:
        raise CatalogError(f"no catalog rows found in {path}")
    return sorted(rows)


def main(argv=None):
    ap = argparse.ArgumentParser(prog="generate-exercise-catalog.py", description=__doc__.splitlines()[0])
    ap.add_argument("bundle_dir", nargs="?", help="Path to the Biblioteca_Ejercicios_Free folder.")
    ap.add_argument("--out", help="Catalog migration file to write.")
    ap.add_argument("--metrics-out", help="Metric-type migration file to write (T-092).")
    ap.add_argument("--catalog-sql", help="Read (key, group, equipment) rows from the generated catalog migration "
                                          "instead of the bundle (metrics mode only).")
    ap.add_argument("--patterns-csv", help="Reviewed key -> movement_pattern CSV (T-150).")
    ap.add_argument("--patterns-out", help="Movement-pattern update statements to write (needs --patterns-csv).")
    ap.add_argument("--strength-out", help="Strength-flag update statement to write (T-211, counts_as_strength).")
    ap.add_argument("--metrics-table", help="Also write the key -> metric table (markdown) here.")
    ap.add_argument("--self-check", action="store_true", help="Assert the documented metric examples and exit.")
    args = ap.parse_args(argv)
    try:
        self_check()
        if args.self_check:
            print("self-check ok")
            return 0
        if args.bundle_dir:
            rows = build_rows(load_exercises(os.path.abspath(args.bundle_dir)))
        elif args.catalog_sql and (args.metrics_out or args.patterns_out) and not args.out:
            rows = rows_from_catalog_sql(args.catalog_sql)
        else:
            ap.error("pass the bundle folder (or --catalog-sql with --metrics-out/--patterns-out only)")
        if args.patterns_out and not args.patterns_csv:
            ap.error("--patterns-out needs --patterns-csv")
        mapping = None
        if args.patterns_csv:
            mapping = load_patterns_csv(args.patterns_csv)
            pattern_rows = build_pattern_rows(rows, mapping)
        if args.out:
            with open(args.out, "w", encoding="utf-8", newline="\n") as f:
                f.write(render_sql(rows, mapping))
            print(f"Wrote {len(rows)} catalog rows to {args.out}")
        if args.metrics_out:
            metric_rows = build_metric_rows(rows)
            with open(args.metrics_out, "w", encoding="utf-8", newline="\n") as f:
                f.write(render_metrics_sql(metric_rows))
            print(f"Wrote {len(metric_rows)} metric types to {args.metrics_out}")
            if args.metrics_table:
                with open(args.metrics_table, "w", encoding="utf-8", newline="\n") as f:
                    f.write(render_metrics_table(metric_rows))
        if args.patterns_out:
            with open(args.patterns_out, "w", encoding="utf-8", newline="\n") as f:
                f.write(render_patterns_sql(pattern_rows))
            print(f"Wrote {len(pattern_rows)} movement patterns to {args.patterns_out}")
        if args.strength_out:
            with open(args.strength_out, "w", encoding="utf-8", newline="\n") as f:
                f.write(render_strength_sql())
            print(f"Wrote the strength flag statement to {args.strength_out}")
    except CatalogError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
