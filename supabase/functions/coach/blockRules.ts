// Deterministic rules of the training block (planning/04 section 9): frequency, session split, slots, volume,
// rests, rep ranges and order. The model never decides any of this; it only picks an exercise per slot.
// Pure: no I/O.
import { CARDIO_MIN_WEEK, CARDIO_MIN_WEEK_MINIMAL } from "./cardioRules.ts";
import { firstPhase } from "./nutritionRules.ts";
import type {
    BlockPrefs,
    BlockSkeleton,
    BlockSlot,
    Intention,
    MuscleGroup,
    Phase,
    Role,
    SkeletonSession,
} from "./schemas.ts";

export const BLOCK_WEEKS = 4;
export const DEFAULT_SESSION_MINUTES = 60;
export const MIN_EXERCISES = 3;
export const MAX_EXERCISES = 8;
/** Minutes budgeted per exercise (sets x rest + transitions), see 04 section 9.3. */
export const MINUTES_PER_EXERCISE = 12;
export const MAX_DAYS = 7;

/** Cardio in the block (04 section 9.11): whole 5-minute units, at most 60 min per routine, always "suave". */
export const CARDIO_UNIT_MIN = 5;
export const CARDIO_ROUTINE_CAP_MIN = 60;
/** Per-routine cap of the minimal phase (07 section 3 "Cardio by phase"). */
export const CARDIO_ROUTINE_CAP_MINIMAL_MIN = 30;
/** After this many minimal blocks in a row the cardio target returns to the recommendation (Spiering 2021). */
export const MINIMAL_CARDIO_BLOCKS = 4;
/** The minimal phase: sessions and sets per slot, doubled from this age (07 section 12.3). */
export const MINIMAL_SENIOR_AGE = 65;
export const CARDIO_PACE = "suave" as const;
/** Technical bounds of the weekly target (same as the plan edit). */
export const CARDIO_TARGET_MAX = 600;

/**
 * Splits the weekly cardio target over the routines: units of 5 min, remainder to the first routines, at most `cap`
 * (60, or 30 in the minimal phase) min per routine; the minutes that do not fit are "por tu cuenta". A routine with 0
 * has no cardio exercise.
 */
export function splitCardio(
    targetMin: number,
    routines: number,
    cap: number = CARDIO_ROUTINE_CAP_MIN,
): {
    per_routine: number[];
    planned: number;
    on_your_own: number;
} {
    const units = Math.floor(Math.max(0, targetMin) / CARDIO_UNIT_MIN);
    const planned = Math.min(
        units,
        (cap / CARDIO_UNIT_MIN) * routines,
    );
    const base = Math.floor(planned / routines);
    const rest = planned % routines;
    return {
        per_routine: Array.from(
            { length: routines },
            (_, i) => CARDIO_UNIT_MIN * (base + (i < rest ? 1 : 0)),
        ),
        planned: planned * CARDIO_UNIT_MIN,
        on_your_own: targetMin - planned * CARDIO_UNIT_MIN,
    };
}

export const MAX_SETS_PER_SLOT = 5;
export const MIN_SETS_PER_SLOT = 2;
/** Weekly sets per muscle group: the starting point of every experience class (07 section 3, amended 2026-11-01). */
export const BASE_WEEKLY_SETS = 10;
export const SECONDARY_WEEKLY_SETS = 6;
export const SECONDARY_GROUPS: MuscleGroup[] = ["forearms", "calves", "core"];

/** Rests in seconds by role: bounds are inclusive. */
export const REST = {
    main: { min: 120, max: 180, default: 150 },
    isolation: { min: 60, max: 120, default: 90 },
} as const;

/** Heavy / medium / light ranges that "Ganar fuerza" alternates across the sessions of a week. */
export const STRENGTH_RANGES: ReadonlyArray<readonly [number, number]> = [
    [3, 5],
    [5, 6],
    [6, 8],
];
export const MAIN_RANGE: readonly [number, number] = [6, 10];
export const ISOLATION_RANGE: readonly [number, number] = [10, 15];

/** Multi-joint patterns (E13 Q3): the "main" lifts. */
export const MAIN_PATTERNS: readonly string[] = [
    "squat",
    "hinge",
    "lunge",
    "horizontal_push",
    "vertical_push",
    "horizontal_pull",
    "vertical_pull",
];
/** Patterns of single-joint (and core) work: the "isolation" slots. */
export const ISOLATION_PATTERNS: readonly string[] = ["isolation", "core"];

export const isMainLift = (pattern: string | null | undefined): boolean =>
    pattern != null && MAIN_PATTERNS.includes(pattern);

/** Groups that have a main (multi-joint) lift; the rest only get isolation slots. */
export const MAIN_GROUPS: MuscleGroup[] = [
    "quads",
    "hamstrings",
    "glutes",
    "chest",
    "back",
    "shoulders",
];

const FULL_BODY_PRIORITY: MuscleGroup[] = [
    "quads",
    "chest",
    "back",
    "hamstrings",
    "shoulders",
    "glutes",
    "biceps",
    "triceps",
    "core",
    "calves",
    "forearms",
];

type Template = { name: string; groups: MuscleGroup[]; fullBody: boolean };
const FULL: Template = {
    name: "Cuerpo completo",
    groups: FULL_BODY_PRIORITY,
    fullBody: true,
};
const UPPER: Template = {
    name: "Tren superior",
    groups: ["chest", "back", "shoulders", "triceps", "biceps", "forearms"],
    fullBody: false,
};
const LOWER: Template = {
    name: "Tren inferior",
    groups: ["quads", "hamstrings", "glutes", "calves", "core"],
    fullBody: false,
};
const PUSH: Template = {
    name: "Empuje",
    groups: ["chest", "shoulders", "triceps"],
    fullBody: false,
};
const PULL: Template = {
    name: "Tirón",
    groups: ["back", "biceps", "forearms"],
    fullBody: false,
};
const LEGS: Template = {
    name: "Piernas",
    groups: ["quads", "hamstrings", "glutes", "calves", "core"],
    fullBody: false,
};

const TEMPLATES: Record<number, Template[]> = {
    1: [FULL],
    2: [FULL, FULL],
    3: [FULL, FULL, FULL],
    4: [UPPER, LOWER, UPPER, LOWER],
    5: [PUSH, PULL, LEGS, UPPER, LOWER],
    6: [PUSH, PULL, LEGS, PUSH, PULL, LEGS],
    7: [PUSH, PULL, LEGS, PUSH, PULL, LEGS, FULL],
};

export type Frequency = {
    days_per_week: number;
    days_chosen: boolean;
    session_minutes: number;
};

/** Days set by the user are used as they are; otherwise the coach chooses (and states it): 04 section 9.1. */
export function chooseFrequency(
    prefs: Pick<
        BlockPrefs,
        "days_per_week" | "session_minutes" | "experience"
    >,
): Frequency {
    const minutes = prefs.session_minutes ?? DEFAULT_SESSION_MINUTES;
    if (prefs.days_per_week != null) {
        return {
            days_per_week: prefs.days_per_week,
            days_chosen: false,
            session_minutes: minutes,
        };
    }
    const days = minutes <= 45 ? 3 : prefs.experience === "beginner" ? 3 : 4;
    return {
        days_per_week: Math.min(5, days),
        days_chosen: true,
        session_minutes: minutes,
    };
}

/**
 * Weekly cardio target of a block (07 section 3 "Cardio by phase"): the minimal phase asks for 100 minutes (150 once
 * 4 minimal blocks have run in a row), every other phase for the recommendation (150); the user's own edit of the
 * plan's cardio always wins. Deload and diet-break weeks do not touch it.
 */
export function cardioTargetFor(
    phase: Phase,
    minimalBlocksInARow: number,
    edited: boolean,
    planned: number | null | undefined,
): number {
    if (edited && typeof planned === "number") return planned;
    if (phase === "minimal") {
        return minimalBlocksInARow >= MINIMAL_CARDIO_BLOCKS
            ? CARDIO_MIN_WEEK
            : CARDIO_MIN_WEEK_MINIMAL;
    }
    return CARDIO_MIN_WEEK;
}

export const exercisesPerSession = (sessionMinutes: number): number =>
    Math.min(
        MAX_EXERCISES,
        Math.max(
            MIN_EXERCISES,
            Math.floor(sessionMinutes / MINUTES_PER_EXERCISE),
        ),
    );

export const repScheme = (intention: Intention): "single" | "undulating" =>
    intention === "strength" ? "undulating" : "single";

/** Rep range of a slot: strength mains alternate by session; everything else has one range per role. */
export function repRange(
    intention: Intention,
    role: Role,
    sessionIndex: number,
): [number, number] {
    if (role === "isolation") return [...ISOLATION_RANGE];
    if (repScheme(intention) === "undulating") {
        return [
            ...STRENGTH_RANGES[(sessionIndex - 1) % STRENGTH_RANGES.length],
        ];
    }
    return [...MAIN_RANGE];
}

/**
 * Groups that must appear in at least two sessions when days >= 2: the six major groups of a split, or the first
 * floor(slots / 2) of the full-body priority list (a short session cannot carry every group).
 */
export function requiredGroups(
    days: number,
    perSession: number,
): MuscleGroup[] {
    if (days < 2) return [];
    const template = TEMPLATES[days];
    if (template.every((t) => t.fullBody)) {
        const r = Math.min(6, Math.floor(days * perSession / 2));
        return FULL_BODY_PRIORITY.slice(0, r);
    }
    return MAIN_GROUPS;
}

const sorted = <T>(list: T[], key: (t: T) => number) =>
    list.map((v, i) => ({ v, i })).sort((a, b) =>
        key(a.v) - key(b.v) || a.i - b.i
    )
        .map((x) => x.v);

/** Full-body sessions: every required group twice, in different sessions, then single extras by priority. */
function allocateFullBody(days: number, perSession: number): MuscleGroup[][] {
    const required = requiredGroups(days, perSession);
    const total = days * perSession;
    const sequence: MuscleGroup[] = [...required, ...required];
    for (let i = required.length; sequence.length < total; i++) {
        sequence.push(FULL_BODY_PRIORITY[i % FULL_BODY_PRIORITY.length]);
    }
    const sessions: MuscleGroup[][] = Array.from({ length: days }, () => []);
    for (const g of sequence.slice(0, total)) {
        const room = sessions
            .map((s, i) => ({ s, i }))
            .filter((x) => x.s.length < perSession);
        const fresh = room.filter((x) => !x.s.includes(g));
        const pool = fresh.length ? fresh : room;
        // Fewest slots first, then the lowest index.
        pool.sort((a, b) => a.s.length - b.s.length || a.i - b.i);
        pool[0].s.push(g);
    }
    return sessions;
}

function cycle(groups: MuscleGroup[], n: number): MuscleGroup[] {
    return Array.from({ length: n }, (_, i) => groups[i % groups.length]);
}

type Planned = {
    group: MuscleGroup;
    role: Role;
    /** A minimal block keeps the previous main lift (and its rep range) in this slot. */
    fixed?: { id: string; rep_min: number; rep_max: number };
};

function planSession(groups: MuscleGroup[]): Planned[] {
    const seen = new Set<MuscleGroup>();
    const planned = groups.map((group): Planned => {
        const first = !seen.has(group);
        seen.add(group);
        return {
            group,
            role: first && MAIN_GROUPS.includes(group) ? "main" : "isolation",
        };
    });
    // Main lifts first (lower body, then push/pull), isolation after, each in its planned order.
    return sorted(
        planned,
        (p) =>
            p.role === "main"
                ? MAIN_GROUPS.indexOf(p.group)
                : MAIN_GROUPS.length + 1,
    );
}

/** The session skeleton: split, slots per session, sets, rep ranges and rests. 04 section 9. */
export function buildSkeleton(prefs: BlockPrefs): BlockSkeleton {
    const phase = prefs.phase ?? firstPhase(prefs.intention, null).phase;
    const minimal = phase === "minimal";
    const senior = (prefs.age ?? 0) >= MINIMAL_SENIOR_AGE;
    const freq = chooseFrequency(prefs);
    // The minimal phase fixes the frequency: 1 full-body session (2 from 65 years).
    const days = minimal
        ? (senior ? 2 : 1)
        : Math.min(MAX_DAYS, Math.max(1, freq.days_per_week));
    const n = exercisesPerSession(freq.session_minutes);
    const templates = TEMPLATES[days];
    const fullBodyOnly = templates.every((t) => t.fullBody);
    const allocated = fullBodyOnly && days >= 2
        ? allocateFullBody(days, n)
        : templates.map((t) =>
            t.fullBody ? FULL_BODY_PRIORITY.slice(0, n) : cycle(t.groups, n)
        );

    let planned: Planned[][] = allocated.map(planSession);
    if (minimal) {
        const lifts = (prefs.previous?.main_lifts ?? []).slice(0, n);
        planned = lifts.length > 0
            // The previous main lifts, the same in every session, each with its own rep range.
            ? planned.map(() =>
                lifts.map((l): Planned => ({
                    group: l.muscle_group,
                    role: "main",
                    fixed: {
                        id: l.exercise_id,
                        rep_min: l.rep_min,
                        rep_max: l.rep_max,
                    },
                }))
            )
            // No previous block: the main-lift slots of the full-body skeleton, normal rep ranges.
            : planned.map((s) => s.filter((p) => p.role === "main"));
    }
    // Weekly sets per group, spread over the slots of that group.
    const slotCount = new Map<MuscleGroup, number>();
    for (const s of planned) {
        for (const p of s) {
            slotCount.set(p.group, (slotCount.get(p.group) ?? 0) + 1);
        }
    }
    const placed = new Map<MuscleGroup, number>();
    const setsFor = (group: MuscleGroup): number => {
        if (minimal) return senior ? 2 : 1;
        let total = SECONDARY_GROUPS.includes(group)
            ? SECONDARY_WEEKLY_SETS
            : BASE_WEEKLY_SETS;
        // Cut and regain add no volume: the previous block's sets of the group are kept (07 section 12.3).
        if (phase === "cut" || phase === "regain") {
            const kept = prefs.previous?.sets_per_group[group];
            if (typeof kept === "number" && kept > 0) total = kept;
        }
        const count = slotCount.get(group)!;
        const k = placed.get(group) ?? 0;
        placed.set(group, k + 1);
        const share = Math.floor(total / count) + (k < total % count ? 1 : 0);
        return Math.min(MAX_SETS_PER_SLOT, Math.max(MIN_SETS_PER_SLOT, share));
    };

    const split = splitCardio(
        prefs.cardio_min_week,
        days,
        minimal ? CARDIO_ROUTINE_CAP_MINIMAL_MIN : CARDIO_ROUTINE_CAP_MIN,
    );
    const nameCount = new Map<string, number>();
    templates.forEach((t) =>
        nameCount.set(t.name, (nameCount.get(t.name) ?? 0) + 1)
    );
    const nameSeen = new Map<string, number>();
    const sessions: SkeletonSession[] = planned.map((slots, i) => {
        const t = templates[i];
        const k = nameSeen.get(t.name) ?? 0;
        nameSeen.set(t.name, k + 1);
        const name = nameCount.get(t.name)! > 1
            ? `${t.name} ${String.fromCharCode(65 + k)}`
            : t.name;
        const sessionIndex = i + 1;
        const built: BlockSlot[] = slots.map((p, j) => {
            const [rep_min, rep_max] = p.fixed
                ? [p.fixed.rep_min, p.fixed.rep_max]
                : repRange(prefs.intention, p.role, sessionIndex);
            return {
                slot_id: `s${sessionIndex}.e${j + 1}`,
                muscle_group: p.group,
                role: p.role,
                sets: setsFor(p.group),
                rep_min,
                rep_max,
                rest_seconds: REST[p.role].default,
                ...(p.fixed ? { fixed_exercise_id: p.fixed.id } : {}),
            };
        });
        const minutes = split.per_routine[i];
        return {
            session_index: sessionIndex,
            name,
            focus: [...new Set(built.map((s) => s.muscle_group))],
            slots: built,
            // Cardio is a separate slot: it never touches the lifting slots, sets, rests or ranges.
            ...(minutes > 0
                ? { cardio: { slot_id: `s${sessionIndex}.cardio`, minutes } }
                : {}),
        };
    });

    return {
        phase,
        days_per_week: days,
        days_chosen: minimal ? true : freq.days_chosen,
        session_minutes: freq.session_minutes,
        rep_scheme: repScheme(prefs.intention),
        block_weeks: BLOCK_WEEKS,
        sessions,
        cardio: {
            target_min_week: prefs.cardio_min_week,
            planned_min_week: split.planned,
            on_your_own_min: split.on_your_own,
            per_routine_min: split.per_routine,
        },
    };
}

/** Sum of sets per muscle group over a week (server arithmetic, never the model's). */
export function weeklySetsByMuscle(
    items: { muscle_group: string; sets: number }[],
): Record<string, number> {
    const out: Record<string, number> = {};
    for (const it of items) {
        out[it.muscle_group] = (out[it.muscle_group] ?? 0) + it.sets;
    }
    return out;
}

/** In how many sessions of the week each group appears. */
export function sessionsPerMuscle(
    sessions: { muscle_group: string }[][],
): Record<string, number> {
    const out: Record<string, number> = {};
    for (const s of sessions) {
        for (const g of new Set(s.map((x) => x.muscle_group))) {
            out[g] = (out[g] ?? 0) + 1;
        }
    }
    return out;
}
