import { assertEquals } from "jsr:@std/assert@1.0.19";
import { type ChatInput, parseChatInput } from "./schema.ts";

const valid = () => ({
    exercise: { name: "Press banca", muscles: ["Pecho"], instructions: "" },
    question: "¿Cómo coloco los codos?",
});

const isError = (v: unknown) => typeof v === "string";

function withEx(patch: Record<string, unknown>) {
    const b = valid();
    return { ...b, exercise: { ...b.exercise, ...patch } };
}

Deno.test("accepts a minimal input with empty instructions", () => {
    const r = parseChatInput(valid()) as ChatInput;
    assertEquals(r.exercise.instructions, "");
    assertEquals(r.exercise.muscles, ["Pecho"]);
});

Deno.test("accepts a full input and trims every text", () => {
    const r = parseChatInput({
        exercise: {
            name: "  Press banca ",
            muscles: [" Pecho ", "Tríceps"],
            instructions: " Baja la barra. ",
        },
        question: "  ¿Y la espalda? ",
    });
    assertEquals(r, {
        exercise: {
            name: "Press banca",
            muscles: ["Pecho", "Tríceps"],
            instructions: "Baja la barra.",
        },
        question: "¿Y la espalda?",
    });
});

Deno.test("name accepts 120 chars and rejects 121 or blank", () => {
    assertEquals(
        isError(parseChatInput(withEx({ name: "a".repeat(120) }))),
        false,
    );
    assertEquals(
        isError(parseChatInput(withEx({ name: "a".repeat(121) }))),
        true,
    );
    assertEquals(isError(parseChatInput(withEx({ name: "  " }))), true);
});

Deno.test("muscles accept 12 items and reject 13, none, blank or non-strings", () => {
    const n = (k: number) => Array.from({ length: k }, () => "Pecho");
    assertEquals(isError(parseChatInput(withEx({ muscles: n(12) }))), false);
    assertEquals(isError(parseChatInput(withEx({ muscles: n(13) }))), true);
    assertEquals(isError(parseChatInput(withEx({ muscles: [] }))), true);
    assertEquals(isError(parseChatInput(withEx({ muscles: [" "] }))), true);
    assertEquals(isError(parseChatInput(withEx({ muscles: [3] }))), true);
    assertEquals(isError(parseChatInput(withEx({ muscles: "Pecho" }))), true);
    assertEquals(
        isError(parseChatInput(withEx({ muscles: ["a".repeat(61)] }))),
        true,
    );
});

Deno.test("instructions accept 4000 chars and reject 4001", () => {
    assertEquals(
        isError(parseChatInput(withEx({ instructions: "a".repeat(4000) }))),
        false,
    );
    assertEquals(
        isError(parseChatInput(withEx({ instructions: "a".repeat(4001) }))),
        true,
    );
    assertEquals(isError(parseChatInput(withEx({ instructions: null }))), true);
});

Deno.test("question accepts 500 chars and rejects 501, blank or non-string", () => {
    const q = (question: unknown) => ({ ...valid(), question });
    assertEquals(isError(parseChatInput(q("a".repeat(500)))), false);
    assertEquals(isError(parseChatInput(q("a".repeat(501)))), true);
    assertEquals(isError(parseChatInput(q("   "))), true);
    assertEquals(isError(parseChatInput(q(5))), true);
    assertEquals(isError(parseChatInput({ exercise: valid().exercise })), true);
});

Deno.test("rejects a missing or non-object exercise", () => {
    assertEquals(isError(parseChatInput({ question: "x" })), true);
    assertEquals(
        isError(parseChatInput({ ...valid(), exercise: "Press" })),
        true,
    );
    assertEquals(isError(parseChatInput({ ...valid(), exercise: [] })), true);
});

Deno.test("rejects extra top-level keys", () => {
    for (
        const key of [
            "history",
            "messages",
            "weight",
            "diary",
            "user_id",
            "health",
            "account",
            "tools",
        ]
    ) {
        assertEquals(
            isError(parseChatInput({ ...valid(), [key]: "x" })),
            true,
            key,
        );
    }
});

Deno.test("rejects extra keys inside exercise", () => {
    assertEquals(isError(parseChatInput(withEx({ equipment: "barra" }))), true);
    assertEquals(isError(parseChatInput(withEx({ id: "x" }))), true);
});

Deno.test("rejects null, arrays and primitives as body", () => {
    for (const b of [null, [], "x", 3]) {
        assertEquals(isError(parseChatInput(b)), true);
    }
});
