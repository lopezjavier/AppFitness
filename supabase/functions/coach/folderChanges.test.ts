import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import {
    applyChange,
    applyChanges,
    type ChangeBody,
    conflicts,
    type FolderRoutine,
    toCheckerRoutines,
} from "./folderChanges.ts";
import { ex } from "./blockFixtures.ts";

const FIXTURE_PATH = new URL("./fixtures/folder_changes.json", import.meta.url);
const SQL_TEST_PATH = new URL(
    "../../tests/coach_apply_change.test.sql",
    import.meta.url,
);
const fixtureText = Deno.readTextFileSync(FIXTURE_PATH);
const fixture = JSON.parse(fixtureText) as {
    routine: FolderRoutine;
    base: unknown[];
    cases: { name: string; change: ChangeBody; after?: unknown[] }[];
};

const resolve = (key: string) =>
    key === "no-such-exercise" ? null : { id: `id-of-${key}` };

/** What is saved of a routine, without ids: the same projection as the pgTAP test. */
const project = (r: FolderRoutine) =>
    r.exercises.map((e) => ({
        exercise_key: e.exercise_key,
        superset_group: e.superset_group,
        rest_seconds: e.rest_seconds,
        rep_min: e.rep_min,
        rep_max: e.rep_max,
        target_rpe: e.target_rpe,
        notes: e.notes,
        sets: e.sets.map((s) => [s.reps, s.set_type, s.weight_kg]),
    }));

for (const c of fixture.cases) {
    Deno.test(`applyChange (shared fixture): ${c.name}`, () => {
        const out = applyChange(fixture.routine, c.change, resolve) ??
            fixture.routine;
        assertEquals(project(out), c.after ?? fixture.base);
    });
}

Deno.test("the shared fixture is embedded verbatim in the pgTAP test of coach_apply_change", () => {
    const sql = Deno.readTextFileSync(SQL_TEST_PATH);
    assert(
        sql.includes(fixtureText),
        "run scripts/gen-apply-change-test.sh after editing fixtures/folder_changes.json",
    );
});

Deno.test("applyChange does not mutate its input", () => {
    const before = JSON.stringify(fixture.routine);
    applyChange(fixture.routine, {
        op: "remove_exercise",
        routine_index: 1,
        position: 2,
    }, resolve);
    assertEquals(JSON.stringify(fixture.routine), before);
});

Deno.test("set_sets accepts the boundaries 1 and 10 and refuses 0 and 11", () => {
    const at = (n: number) =>
        applyChange(fixture.routine, {
            op: "set_sets",
            routine_index: 1,
            position: 1,
            n,
        }, resolve);
    assertEquals(at(1)!.exercises[0].sets.length, 1);
    assertEquals(at(10)!.exercises[0].sets.length, 10);
    assertEquals(at(0), null);
    assertEquals(at(11), null);
});

Deno.test("set_rep_range refuses an inverted or unstorable range", () => {
    const at = (rep_min: number, rep_max: number) =>
        applyChange(fixture.routine, {
            op: "set_rep_range",
            routine_index: 1,
            position: 1,
            rep_min,
            rep_max,
        }, resolve);
    assertEquals(at(12, 8), null);
    assertEquals(at(0, 8), null);
    assertEquals(at(8, 51), null);
    assertEquals(at(1, 50)!.exercises[0].rep_max, 50);
});

Deno.test("applyChanges addresses the original positions even after an earlier removal", () => {
    const out = applyChanges(
        [fixture.routine],
        [
            { op: "remove_exercise", routine_index: 1, position: 1 },
            { op: "set_rpe", routine_index: 1, position: 3, rpe: 9 },
            { op: "set_rpe", routine_index: 1, position: 1, rpe: 9 },
        ],
        resolve,
    );
    assertEquals(
        out[0].exercises.map((e) => [e.exercise_key, e.target_rpe]),
        [["seated-cable-row", 8], ["leg-extension", 9]],
    );
});

Deno.test("applyChanges skips a routine index that does not exist", () => {
    const out = applyChanges(
        [fixture.routine],
        [{ op: "set_rpe", routine_index: 4, position: 1, rpe: 9 }],
        resolve,
    );
    assertEquals(out, [fixture.routine]);
});

Deno.test("conflicts: same target with a removal or the same op, never an addition", () => {
    const rpe = (position: number): ChangeBody => ({
        op: "set_rpe",
        routine_index: 1,
        position,
        rpe: 9,
    });
    const rest: ChangeBody = {
        op: "set_rest",
        routine_index: 1,
        position: 1,
        rest_seconds: 90,
    };
    const remove: ChangeBody = {
        op: "remove_exercise",
        routine_index: 1,
        position: 1,
    };
    const add: ChangeBody = {
        op: "add_exercise",
        routine_index: 1,
        exercise_key: "k",
        sets: [],
    };
    assert(conflicts(rpe(1), rpe(1)));
    assert(conflicts(rpe(1), remove));
    assert(!conflicts(rpe(1), rest));
    assert(!conflicts(rpe(1), rpe(2)));
    assert(!conflicts(add, add));
});

Deno.test("toCheckerRoutines leaves cardio out and reads a missing RPE or range as invalid", () => {
    const squat = ex({ muscle_group: "quads", movement_pattern: "squat" });
    const walk = ex({
        muscle_group: "full_body",
        movement_pattern: "cardio",
        equipment: "bodyweight",
    });
    const byId = new Map([[squat.id, squat], [walk.id, walk]]);
    const routine: FolderRoutine = {
        id: "r",
        name: "R",
        notes: null,
        track_as_routine: true,
        exercises: [
            {
                position: 1,
                exercise_id: squat.id,
                exercise_key: squat.key!,
                superset_group: null,
                rest_seconds: 150,
                rep_min: null,
                rep_max: null,
                target_rpe: null,
                notes: null,
                note_kind: null,
                sets: [{
                    reps: 8,
                    set_type: "working",
                    weight_kg: null,
                    duration_s: null,
                }],
            },
            {
                position: 2,
                exercise_id: walk.id,
                exercise_key: walk.key!,
                superset_group: null,
                rest_seconds: 0,
                rep_min: null,
                rep_max: null,
                target_rpe: null,
                notes: null,
                note_kind: null,
                sets: [{
                    reps: null,
                    set_type: "working",
                    weight_kg: null,
                    duration_s: 1800,
                }],
            },
        ],
    };
    const [out] = toCheckerRoutines([routine], byId);
    assertEquals(out.exercises.length, 1);
    assertEquals(out.exercises[0].sets[0], {
        type: "working",
        rep_min: 8,
        rep_max: 8,
        rpe: 0,
        rest_seconds: 150,
    });
});
