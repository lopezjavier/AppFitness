import { assertEquals } from "jsr:@std/assert@1.0.19";
import { createFoodSearchHandler } from "./handler.ts";
import {
    type Food,
    type NormalizedFood,
    normalizeProduct,
    OffError,
} from "../_shared/off-client.ts";

const BASE = "http://localhost/functions/v1/food-search";

function offFoods(): NormalizedFood[] {
    const raw = JSON.parse(
        Deno.readTextFileSync(
            new URL(
                "../_shared/fixtures/off/search-avena.json",
                import.meta.url,
            ),
        ),
    );
    return raw.products
        .map(normalizeProduct)
        .filter((f: NormalizedFood | null) => f !== null);
}

function food(
    id: string,
    name: string,
    source: "off" | "user" = "off",
    owner?: string,
    barcode: string | null = null,
): Food & { owner?: string } {
    return {
        id,
        source,
        owner,
        barcode,
        name,
        brand: null,
        base_unit: "g",
        kcal_100: 100,
        protein_100: 1,
        carbs_100: 1,
        fat_100: 1,
        serving_label: null,
        serving_amount: null,
        image_url: null,
        portions: [],
    };
}

type Store = Array<Food & { owner?: string }>;

function setup(opts: {
    store?: Store;
    off?: "ok" | "timeout" | "down";
} = {}) {
    const store = opts.store ?? [];
    const calls = { off: [] as string[], upserted: [] as NormalizedFood[][] };
    const handler = createFoodSearchHandler({
        authenticate: (t) =>
            Promise.resolve(
                t === "token-a" ? "user-a" : t === "token-b" ? "user-b" : null,
            ),
        // Models RLS: OFF rows plus the caller's own foods.
        searchLocal: (q, userId) =>
            Promise.resolve(
                store
                    .filter((f) => f.source === "off" || f.owner === userId)
                    .filter((f) =>
                        f.name.toLowerCase().includes(q.toLowerCase())
                    )
                    .map(({ owner: _o, ...f }) => f),
            ),
        searchOff: (q) => {
            calls.off.push(q);
            if (opts.off === "timeout") {
                return Promise.reject(new OffError("timeout"));
            }
            if (opts.off === "down") {
                return Promise.reject(new OffError("unavailable"));
            }
            return Promise.resolve(offFoods());
        },
        upsertOff: (foods) => {
            calls.upserted.push(foods);
            return Promise.resolve(
                foods.map(({ off_data: _raw, ...f }, i) => ({
                    ...f,
                    id: `off-${f.barcode}-${i}`,
                    source: "off" as const,
                    portions: f.portions.map((p, j) => ({ id: `p${j}`, ...p })),
                })),
            );
        },
    });
    const call = (
        query: string,
        init: RequestInit = {},
        token: string | null = "token-a",
    ) => handler(
        new Request(`${BASE}${query}`, {
            ...init,
            headers: token ? { authorization: `Bearer ${token}` } : {},
        }),
    );
    return { call, calls };
}

Deno.test("10 or more cached results are returned without calling OFF", async () => {
    const store = Array.from(
        { length: 12 },
        (_, i) => food(`f${i}`, `Avena ${i}`),
    );
    const { call, calls } = setup({ store });
    const res = await call("?q=avena");
    const body = await res.json();
    assertEquals(res.status, 200);
    assertEquals(body.source, "cache");
    assertEquals(body.results.length, 12);
    assertEquals(calls.off, []);
});

Deno.test("fewer than 10 cached results query OFF once, cache the products and merge", async () => {
    const store = [
        food("local-1", "Avena en copos", "off", undefined, "8410000810004"),
    ];
    const { call, calls } = setup({ store });
    const res = await call("?q=avena");
    const body = await res.json();
    assertEquals(body.source, "off");
    assertEquals(calls.off, ["avena"]);
    assertEquals(calls.upserted.length, 1);
    assertEquals(calls.upserted[0].length, 2); // the two usable fixture products
    assertEquals(body.results[0].id, "local-1"); // local first
    // The same barcode is not duplicated (dedupe by barcode within the same source).
    assertEquals(
        body.results.filter((f: Food) => f.barcode === "8410000810004").length,
        1,
    );
    assertEquals(body.results.length <= 20, true);
});

Deno.test("results are capped at 20", async () => {
    const store = Array.from(
        { length: 9 },
        (_, i) => food(`f${i}`, `Avena ${i}`),
    );
    const { call } = setup({ store });
    const body = await (await call("?q=avena")).json();
    assertEquals(body.results.length <= 20, true);
});

Deno.test("OFF timeout with cached results returns the cached ones", async () => {
    const { call } = setup({ store: [food("f1", "Avena")], off: "timeout" });
    const res = await call("?q=avena");
    assertEquals(res.status, 200);
    const body = await res.json();
    assertEquals(body.source, "cache");
    assertEquals(body.results.map((f: Food) => f.id), ["f1"]);
});

Deno.test("OFF down with no cached results returns a JSON 502 OFF_UNAVAILABLE", async () => {
    const { call } = setup({ off: "down" });
    const res = await call("?q=avena");
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "OFF_UNAVAILABLE");
});

Deno.test("OFF timeout with no cached results returns 504 OFF_UNAVAILABLE", async () => {
    const { call } = setup({ off: "timeout" });
    const res = await call("?q=avena");
    assertEquals(res.status, 504);
    assertEquals((await res.json()).error.code, "OFF_UNAVAILABLE");
});

Deno.test("a 1-character query returns 400 INVALID_INPUT and never calls OFF", async () => {
    const { call, calls } = setup();
    for (const q of ["?q=a", "", "?q=" + "x".repeat(61), "?q=avena&page=0"]) {
        const res = await call(q);
        assertEquals(res.status, 400, q);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
    }
    assertEquals(calls.off, []);
});

Deno.test("missing or invalid token returns 401", async () => {
    const { call } = setup();
    assertEquals((await call("?q=avena", {}, null)).status, 401);
    const bad = await call("?q=avena", {}, "nope");
    assertEquals(bad.status, 401);
    assertEquals((await bad.json()).error.code, "UNAUTHORIZED");
});

Deno.test("other methods return 405 with Allow: GET", async () => {
    const { call } = setup();
    const res = await call("?q=avena", { method: "POST" });
    assertEquals(res.status, 405);
    assertEquals(res.headers.get("allow"), "GET");
});

Deno.test("a user never sees another user's personal food", async () => {
    const store = [
        food("mine", "Tortilla de mi madre", "user", "user-a"),
        ...Array.from({ length: 10 }, (_, i) => food(`o${i}`, `Tortilla ${i}`)),
    ];
    const { call } = setup({ store });
    const forA = await (await call("?q=tortilla", {}, "token-a")).json();
    const forB = await (await call("?q=tortilla", {}, "token-b")).json();
    assertEquals(forA.results.some((f: Food) => f.id === "mine"), true);
    assertEquals(forB.results.some((f: Food) => f.id === "mine"), false);
});

Deno.test("search responses never carry off_data or micronutrients", async () => {
    const { call } = setup({
        store: [
            food(
                "local-1",
                "Avena en copos",
                "off",
                undefined,
                "8410000810004",
            ),
        ],
    });
    const body = await (await call("?q=avena")).json();
    assertEquals(body.source, "off");
    assertEquals(Object.keys(body).sort(), ["results", "source"]);
    for (const f of body.results) {
        assertEquals("off_data" in f, false);
        assertEquals("micronutrients" in f, false);
    }
});
