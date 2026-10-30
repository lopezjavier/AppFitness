// Deterministic rules of the training block (planning/04 section 9): frequency, session split, slots, volume,
// rests, rep ranges and order. The model never decides any of this; it only picks an exercise per slot.
// Pure: no I/O.
import type {
    BlockPrefs,
    BlockSkeleton,
    BlockSlot,
    Intention,
    MuscleGroup,
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

export const MAX_SETS_PER_SLOT = 5;
export const MIN_SETS_PER_SLOT = 2;
/** Weekly sets per muscle group: starting point (07 section 3), lower for beginners; secondary-only groups. */
export const BASE_WEEKLY_SETS = 10;
export const BEGINNER_WEEKLY_SETS = 8;
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

type Planned = { group: MuscleGroup; role: Role };

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
    const freq = chooseFrequency(prefs);
    const days = Math.min(MAX_DAYS, Math.max(1, freq.days_per_week));
    const n = exercisesPerSession(freq.session_minutes);
    const templates = TEMPLATES[days];
    const fullBodyOnly = templates.every((t) => t.fullBody);
    const allocated = fullBodyOnly && days >= 2
        ? allocateFullBody(days, n)
        : templates.map((t) =>
            t.fullBody ? FULL_BODY_PRIORITY.slice(0, n) : cycle(t.groups, n)
        );

    const planned = allocated.map(planSession);
    // Weekly sets per group, spread over the slots of that group.
    const slotCount = new Map<MuscleGroup, number>();
    for (const s of planned) {
        for (const p of s) {
            slotCount.set(p.group, (slotCount.get(p.group) ?? 0) + 1);
        }
    }
    const placed = new Map<MuscleGroup, number>();
    const setsFor = (group: MuscleGroup): number => {
        const total = SECONDARY_GROUPS.includes(group)
            ? SECONDARY_WEEKLY_SETS
            : prefs.experience === "beginner"
            ? BEGINNER_WEEKLY_SETS
            : BASE_WEEKLY_SETS;
        const count = slotCount.get(group)!;
        const k = placed.get(group) ?? 0;
        placed.set(group, k + 1);
        const share = Math.floor(total / count) + (k < total % count ? 1 : 0);
        return Math.min(MAX_SETS_PER_SLOT, Math.max(MIN_SETS_PER_SLOT, share));
    };

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
            const [rep_min, rep_max] = repRange(
                prefs.intention,
                p.role,
                sessionIndex,
            );
            return {
                slot_id: `s${sessionIndex}.e${j + 1}`,
                muscle_group: p.group,
                role: p.role,
                sets: setsFor(p.group),
                rep_min,
                rep_max,
                rest_seconds: REST[p.role].default,
            };
        });
        return {
            session_index: sessionIndex,
            name,
            focus: [...new Set(built.map((s) => s.muscle_group))],
            slots: built,
        };
    });

    return {
        days_per_week: days,
        days_chosen: freq.days_chosen,
        session_minutes: freq.session_minutes,
        rep_scheme: repScheme(prefs.intention),
        block_weeks: BLOCK_WEEKS,
        sessions,
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
