import { assertEquals } from "jsr:@std/assert@1.0.19";
import { createFoodBarcodeHandler } from "./handler.ts";
import {
    type Food,
    normalizeProduct,
    OffError,
} from "../_shared/off-client.ts";

const NOW = new Date("2026-10-20T12:00:00Z");
const DAY = 24 * 60 * 60 * 1000;
const BASE = "http://localhost/functions/v1/food-barcode";

function fixtureFood(name: string) {
    const raw = JSON.parse(
        Deno.readTextFileSync(
            new URL(`../_shared/fixtures/off/${name}.json`, import.meta.url),
        ),
    );
    return normalizeProduct(raw.product)!;
}

function stored(source: "off" | "user" = "off"): Food {
    const f = fixtureFood("product-8410000810004");
    return {
        ...f,
        id: "food-1",
        source,
        portions: [{ id: "p1", ...f.portions[0] }],
    };
}

function setup(opts: {
    local?: { food: Food; fetchedAt: Date; hasOffData?: boolean } | null;
    off?: "found" | "none" | "fail";
    validToken?: boolean;
} = {}) {
    const calls = { off: 0, upsert: 0, findLocal: [] as string[] };
    const handler = createFoodBarcodeHandler({
        authenticate: (t) =>
            Promise.resolve(
                opts.validToken === false ? null : t === "ok" ? "user-a" : null,
            ),
        findLocal: (code, userId) => {
            calls.findLocal.push(`${userId}:${code}`);
            return Promise.resolve(opts.local ?? null);
        },
        fetchOff: () => {
            calls.off++;
            if (opts.off === "fail") {
                return Promise.reject(new OffError("unavailable"));
            }
            return Promise.resolve(
                opts.off === "found"
                    ? fixtureFood("product-8410000810004")
                    : null,
            );
        },
        upsertOff: (food) => {
            calls.upsert++;
            return Promise.resolve({
                ...food,
                id: "food-new",
                source: "off" as const,
                portions: food.portions.map((p, i) => ({ id: `n${i}`, ...p })),
            });
        },
        now: () => NOW,
    });
    const call = (
        query: string,
        init: RequestInit = {},
        token: string | null = "ok",
    ) => handler(
        new Request(`${BASE}${query}`, {
            ...init,
            headers: token ? { authorization: `Bearer ${token}` } : {},
        }),
    );
    return { call, calls };
}

Deno.test("returns a fresh cached food without calling OFF", async () => {
    const { call, calls } = setup({
        local: { food: stored(), fetchedAt: new Date(NOW.getTime() - DAY) },
    });
    const res = await call("?code=8410000810004");
    assertEquals(res.status, 200);
    assertEquals((await res.json()).food.id, "food-1");
    assertEquals(calls.off, 0);
});

Deno.test("fetches from OFF and stores the food when it is not cached", async () => {
    const { call, calls } = setup({ off: "found" });
    const res = await call("?code=8410000810004");
    assertEquals(res.status, 200);
    const { food } = await res.json();
    assertEquals(food.id, "food-new");
    assertEquals(food.portions.length, 1);
    assertEquals([calls.off, calls.upsert], [1, 1]);
});

Deno.test("refetches a cached food older than 30 days", async () => {
    const { call, calls } = setup({
        off: "found",
        local: {
            food: stored(),
            fetchedAt: new Date(NOW.getTime() - 31 * DAY),
        },
    });
    const res = await call("?code=8410000810004");
    assertEquals((await res.json()).food.id, "food-new");
    assertEquals(calls.off, 1);
});

Deno.test("does not refetch a food cached 29 days ago", async () => {
    const { call, calls } = setup({
        off: "found",
        local: {
            food: stored(),
            fetchedAt: new Date(NOW.getTime() - 29 * DAY),
        },
    });
    await call("?code=8410000810004");
    assertEquals(calls.off, 0);
});

Deno.test("returns the stale food when OFF fails", async () => {
    const { call } = setup({
        off: "fail",
        local: {
            food: stored(),
            fetchedAt: new Date(NOW.getTime() - 40 * DAY),
        },
    });
    const res = await call("?code=8410000810004");
    assertEquals(res.status, 200);
    assertEquals((await res.json()).food.id, "food-1");
});

Deno.test("personal foods are never refetched from OFF", async () => {
    const { call, calls } = setup({
        off: "found",
        local: {
            food: stored("user"),
            fetchedAt: new Date(NOW.getTime() - 400 * DAY),
        },
    });
    const res = await call("?code=8410000810004");
    assertEquals((await res.json()).food.id, "food-1");
    assertEquals(calls.off, 0);
});

Deno.test("unknown barcode returns 404 FOOD_NOT_FOUND", async () => {
    const { call } = setup({ off: "none" });
    const res = await call("?code=0000000000000");
    assertEquals(res.status, 404);
    assertEquals((await res.json()).error.code, "FOOD_NOT_FOUND");
});

Deno.test("product without usable nutrition (OFF returns none) is 404", async () => {
    const { call } = setup({ off: "none" });
    assertEquals((await call("?code=8410000830002")).status, 404);
});

Deno.test("OFF down and nothing cached returns 502 OFF_UNAVAILABLE as JSON", async () => {
    const { call } = setup({ off: "fail" });
    const res = await call("?code=8410000810004");
    assertEquals(res.status, 502);
    assertEquals((await res.json()).error.code, "OFF_UNAVAILABLE");
});

Deno.test("invalid code returns 400 INVALID_INPUT", async () => {
    const { call, calls } = setup();
    for (const q of ["?code=abc", "", "?code=1234567"]) {
        const res = await call(q);
        assertEquals(res.status, 400, q);
        assertEquals((await res.json()).error.code, "INVALID_INPUT");
    }
    assertEquals(calls.off, 0);
});

Deno.test("missing or invalid token returns 401", async () => {
    const { call } = setup();
    assertEquals((await call("?code=8410000810004", {}, null)).status, 401);
    const bad = await call("?code=8410000810004", {}, "bad");
    assertEquals(bad.status, 401);
    assertEquals((await bad.json()).error.code, "UNAUTHORIZED");
});

Deno.test("other methods return 405 with Allow: GET", async () => {
    const { call } = setup();
    const res = await call("?code=8410000810004", { method: "POST" });
    assertEquals(res.status, 405);
    assertEquals(res.headers.get("allow"), "GET");
});

Deno.test("local lookup runs on behalf of the authenticated user", async () => {
    const { call, calls } = setup({ off: "none" });
    await call("?code=8410000810004");
    assertEquals(calls.findLocal, ["user-a:8410000810004"]);
});

Deno.test("a cached OFF food without off_data is refetched even when it is recent", async () => {
    const { call, calls } = setup({
        off: "found",
        local: {
            food: stored(),
            fetchedAt: new Date(NOW.getTime() - DAY),
            hasOffData: false,
        },
    });
    const res = await call("?code=8410000810004");
    assertEquals((await res.json()).food.id, "food-new");
    assertEquals([calls.off, calls.upsert], [1, 1]);
});

Deno.test("a cached OFF food without off_data is returned as is when OFF fails", async () => {
    const { call, calls } = setup({
        off: "fail",
        local: {
            food: stored(),
            fetchedAt: new Date(NOW.getTime() - DAY),
            hasOffData: false,
        },
    });
    const res = await call("?code=8410000810004");
    assertEquals(res.status, 200);
    assertEquals((await res.json()).food.id, "food-1");
    assertEquals(calls.upsert, 0);
});

Deno.test("a cached OFF food without off_data is returned as is when OFF has no usable product", async () => {
    const { call } = setup({
        off: "none",
        local: {
            food: stored(),
            fetchedAt: new Date(NOW.getTime() - DAY),
            hasOffData: false,
        },
    });
    assertEquals(
        (await (await call("?code=8410000810004")).json()).food.id,
        "food-1",
    );
});

Deno.test("a fresh cached OFF food with off_data is not refetched", async () => {
    const { call, calls } = setup({
        off: "found",
        local: {
            food: stored(),
            fetchedAt: new Date(NOW.getTime() - DAY),
            hasOffData: true,
        },
    });
    await call("?code=8410000810004");
    assertEquals(calls.off, 0);
});

Deno.test("a personal food without off_data is never refetched", async () => {
    const { call, calls } = setup({
        off: "found",
        local: {
            food: stored("user"),
            fetchedAt: new Date(NOW.getTime() - DAY),
            hasOffData: false,
        },
    });
    await call("?code=8410000810004");
    assertEquals(calls.off, 0);
});
