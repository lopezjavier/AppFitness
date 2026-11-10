// Mode `text` of `food-photo` (T-271, ADR-105): the meal is typed, no image is sent.
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { generateFoodJson } from "../_shared/gemini-client.ts";
import { createFoodPhotoHandler } from "./handler.ts";
import { MEAL_PREFIX, PROMPTS } from "./prompts.ts";
import { MAX_TEXT_CHARS, normalizeDraft } from "./schema.ts";

const URL_ = "http://localhost/functions/v1/food-photo";
const IMAGE = "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBD";
const SENTENCE = "pechuga de pollo con ensalada, 2 cervezas y 4 calamares";

globalThis.fetch = () => {
    throw new Error("real fetch called");
};

function answer(food: unknown): string {
    return JSON.stringify({
        candidates: [{
            content: { parts: [{ text: JSON.stringify(food) }] },
        }],
    });
}

const MEAL = {
    name: "Pechuga con ensalada, 2 cervezas y calamares",
    brand: null,
    base_unit: "g",
    nutrition: {
        kcal: 90,
        protein_g: 8,
        carbs_g: 4,
        fat_g: 3,
        alcohol_g: 1.5,
    },
    micronutrients: {},
    estimated_grams: 900,
    confidence: "low",
};

function setup(food: unknown = MEAL) {
    const sent: Record<string, unknown>[] = [];
    const handler = createFoodPhotoHandler({
        authenticate: (t) => Promise.resolve(t === "ok" ? "user-a" : null),
        generate: (req) =>
            generateFoodJson({
                apiKey: "k",
                fetch: (_url, init) => {
                    sent.push(JSON.parse(init.body as string));
                    return Promise.resolve(
                        new Response(answer(food), { status: 200 }),
                    );
                },
            }, req),
    });
    const call = (payload: unknown) =>
        handler(
            new Request(URL_, {
                method: "POST",
                headers: { authorization: "Bearer ok" },
                body: JSON.stringify(payload),
            }),
        );
    return { call, sent };
}

const text = (extra: Record<string, unknown> = {}) => ({
    mode: "text",
    comment: SENTENCE,
    ...extra,
});

async function errorCode(res: Response) {
    return (await res.json()).error.code as string;
}

const PREVIOUS = {
    estimate: {
        name: "Comida",
        base_unit: "g",
        nutrition: {
            kcal: 300,
            protein_g: 1,
            carbs_g: 1,
            fat_g: 1,
            alcohol_g: 0,
        },
        micronutrients: {},
    },
    reason: { result: "clearly_wrong", kcal: 300, computed: 17, gap: 283 },
};

Deno.test("text: no image, returns the draft with the whole-meal grams and alcohol", async () => {
    const { call, sent } = setup();
    const res = await call(text());
    assertEquals(res.status, 200);
    const { draft } = await res.json();
    assertEquals(draft.estimated_grams, 900);
    assertEquals(draft.nutrition.alcohol_g, 1.5);
    assertEquals(Object.keys(draft.micronutrients).length > 0, true);
    const parts =
        (sent[0].contents as { parts: Record<string, unknown>[] }[])[0]
            .parts;
    assert(parts.every((p) => !("inlineData" in p)));
    assertEquals(parts[0].text, MEAL_PREFIX + SENTENCE);
});

Deno.test("text: an image is refused", async () => {
    const { call } = setup();
    const res = await call(text({ image_base64: IMAGE }));
    assertEquals(res.status, 400);
    assertEquals(await errorCode(res), "INVALID_INPUT");
});

Deno.test("text: an explicit null image is accepted", async () => {
    const { call } = setup();
    assertEquals((await call(text({ image_base64: null }))).status, 200);
});

Deno.test("every other mode still requires an image", async () => {
    const { call } = setup();
    for (const mode of ["dish", "label", "barcode", "weight"]) {
        const res = await call({ mode });
        assertEquals(res.status, 400, mode);
        assertEquals(await errorCode(res), "INVALID_INPUT");
    }
});

Deno.test("text: missing, empty and whitespace comments are refused", async () => {
    const { call } = setup();
    for (const comment of [undefined, null, "", "   \n "]) {
        const res = await call({ mode: "text", comment });
        assertEquals(res.status, 400, String(comment));
        assertEquals(await errorCode(res), "INVALID_INPUT");
    }
});

Deno.test("text: 500 characters pass and 501 are refused", async () => {
    const { call } = setup();
    assertEquals(
        (await call(text({ comment: "a".repeat(MAX_TEXT_CHARS) }))).status,
        200,
    );
    const res = await call(text({ comment: "a".repeat(MAX_TEXT_CHARS + 1) }));
    assertEquals(res.status, 400);
    assertEquals(await errorCode(res), "INVALID_INPUT");
});

Deno.test("dish comment is still capped at 200", async () => {
    const { call } = setup();
    const res = await call({
        mode: "dish",
        image_base64: IMAGE,
        comment: "a".repeat(201),
    });
    assertEquals(res.status, 400);
});

Deno.test("text: a body above 8 KB is refused", async () => {
    const { call, sent } = setup();
    const res = await call(text({ padding: "x".repeat(9000) }));
    assertEquals(res.status, 400);
    assertEquals(await errorCode(res), "INVALID_INPUT");
    assertEquals(sent.length, 0);
});

Deno.test("text: previous is accepted and reaches the model", async () => {
    const { call, sent } = setup();
    const res = await call(text({ previous: PREVIOUS }));
    assertEquals(res.status, 200);
    const parts = (sent[0].contents as { parts: { text?: string }[] }[])[0]
        .parts;
    assert(parts.some((p) => p.text?.includes("Previous estimate")));
});

Deno.test("text: a draft failing the energy check returns the issue", async () => {
    const { call } = setup({
        ...MEAL,
        nutrition: { ...MEAL.nutrition, kcal: 500 },
    });
    const body = await (await call(text())).json();
    assertEquals(body.issue.result, "clearly_wrong");
    assert(body.draft);
});

Deno.test("text: no food named is a 422", async () => {
    const { call } = setup({ ...MEAL, name: "" });
    const res = await call(text({ comment: "hola" }));
    assertEquals(res.status, 422);
    assertEquals(await errorCode(res), "NO_FOOD_RECOGNIZED");
});

Deno.test("text: a missing alcohol value is a 422", async () => {
    const { call } = setup({
        ...MEAL,
        nutrition: { kcal: 90, protein_g: 8, carbs_g: 4, fat_g: 3 },
    });
    assertEquals((await call(text())).status, 422);
});

Deno.test("text: the typed sentence never reaches the console", async () => {
    const logged: string[] = [];
    const original = { log: console.log, error: console.error };
    console.log = (...a: unknown[]) => logged.push(a.join(" "));
    console.error = (...a: unknown[]) => logged.push(a.join(" "));
    try {
        const { call } = setup({ ...MEAL, name: "" });
        await call(text({ comment: "secreto-zzqq" }));
        const ok = setup();
        await ok.call(text({ comment: "secreto-zzqq" }));
    } finally {
        Object.assign(console, original);
    }
    assert(logged.every((l) => !l.includes("secreto-zzqq")));
});

Deno.test("PROMPTS.text describes the whole meal and the other modes do not", () => {
    assert(PROMPTS.text.includes("covers everything eaten or drunk"));
    assert(!PROMPTS.text.includes("The photo shows"));
    for (const mode of ["dish", "label", "barcode", "weight"] as const) {
        assert(!PROMPTS[mode].includes("covers everything eaten or drunk"));
    }
});

Deno.test("normalizeDraft treats text like dish: zero-fills and keeps grams", () => {
    const draft = normalizeDraft(
        {
            name: "Cena",
            base_unit: "g",
            nutrition: { kcal: 100, alcohol_g: 0 },
            estimated_grams: 400,
        },
        "text",
    );
    assertEquals(draft?.nutrition.protein_g, 0);
    assertEquals(draft?.estimated_grams, 400);
    assert(Object.keys(draft!.micronutrients).length > 0);
});
