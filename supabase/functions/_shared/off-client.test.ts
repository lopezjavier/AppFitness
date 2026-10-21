import { assert, assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import {
    fetchOffProduct,
    isSpainSearchHit,
    normalizeProduct,
    OffError,
    searchOff,
} from "./off-client.ts";

function fixture(name: string): Record<string, unknown> & { product: unknown } {
    return JSON.parse(
        Deno.readTextFileSync(
            new URL(`./fixtures/off/${name}.json`, import.meta.url),
        ),
    );
}

Deno.test("normalizeProduct: per-100g product keeps grams, macros and Spanish name", () => {
    const food = normalizeProduct(fixture("product-8410000810004").product)!;
    assertEquals(food.barcode, "8410000810004");
    assertEquals(food.name, "Copos de avena integral");
    assertEquals(food.brand, "Marca Ejemplo");
    assertEquals(food.base_unit, "g");
    assertEquals(
        [food.kcal_100, food.protein_100, food.carbs_100, food.fat_100],
        [372, 13.5, 58.7, 7],
    );
    assertEquals(food.serving_label, "40 g");
    assertEquals(food.serving_amount, 40);
    assertEquals(food.portions, [{ label: "40 g", amount: 40, position: 0 }]);
});

Deno.test("normalizeProduct: per-100ml drink such as Coca-Cola Zero uses ml", () => {
    const food = normalizeProduct(
        fixture("product-coca-cola-zero-ml").product,
    )!;
    assertEquals(food.base_unit, "ml");
    assertEquals(food.kcal_100, 0.3);
    assertEquals(food.portions, [{
        label: "1 lata",
        amount: 330,
        position: 0,
    }]);
});

Deno.test("normalizeProduct: derives kcal from kJ dividing by 4.184", () => {
    const food = normalizeProduct(fixture("product-kj-only").product)!;
    assertEquals(food.kcal_100, 450.05); // 1883 / 4.184 = 450.047...
});

Deno.test("normalizeProduct: serving '1 vaso (200 ml)' becomes a labelled portion", () => {
    const food = normalizeProduct(fixture("product-serving-vaso").product)!;
    assertEquals(food.serving_label, "1 vaso");
    assertEquals(food.serving_amount, 200);
    assertEquals(food.portions, [{
        label: "1 vaso",
        amount: 200,
        position: 0,
    }]);
});

Deno.test("normalizeProduct: no kcal or no name returns null", () => {
    assertEquals(normalizeProduct(fixture("product-no-kcal").product), null);
    assertEquals(
        normalizeProduct({
            code: "1",
            product_name: "  ",
            nutriments: { "energy-kcal_100g": 10 },
        }),
        null,
    );
});

Deno.test("normalizeProduct: missing macros default to 0", () => {
    const food = normalizeProduct({
        code: "12345678",
        product_name: "Algo",
        nutriments: { "energy-kcal_100g": 10 },
    })!;
    assertEquals([food.protein_100, food.carbs_100, food.fat_100], [0, 0, 0]);
    assertEquals(food.portions, []);
});

Deno.test("normalizeProduct: garbage, negative or out-of-range values return null without throwing", () => {
    const base = { code: "12345678", product_name: "Algo" };
    for (
        const nutriments of [
            { "energy-kcal_100g": "abc" },
            { "energy-kcal_100g": -5 },
            { "energy-kcal_100g": 20000 },
            { "energy-kcal_100g": 10, proteins_100g: "x" },
            { "energy-kcal_100g": 10, fat_100g: -1 },
        ]
    ) {
        assertEquals(normalizeProduct({ ...base, nutriments }), null);
    }
    for (const raw of [null, undefined, 5, "x", [], {}]) {
        assertEquals(normalizeProduct(raw), null);
    }
});

Deno.test("normalizeProduct: numeric strings from OFF are accepted", () => {
    const food = normalizeProduct({
        code: "12345678",
        product_name: "Algo",
        nutriments: { "energy-kcal_100g": "52.5", proteins_100g: "1" },
    })!;
    assertEquals(food.kcal_100, 52.5);
    assertEquals(food.protein_100, 1);
});

Deno.test("normalizeProduct: clamps long strings to the column limits", () => {
    const food = normalizeProduct({
        code: "12345678",
        product_name: "n".repeat(500),
        brands: "b".repeat(500),
        nutriments: { "energy-kcal_100g": 10 },
    })!;
    assertEquals(food.name.length, 200);
    assertEquals(food.brand!.length, 200);
});

Deno.test("searchOff: sends User-Agent, Spanish locale, timeout signal and drops unusable products", async () => {
    let seen: { url: string; init?: RequestInit } | undefined;
    const fakeFetch = (input: Request | URL | string, init?: RequestInit) => {
        seen = { url: String(input), init };
        return Promise.resolve(Response.json(fixture("search-avena")));
    };
    const foods = await searchOff(
        { fetch: fakeFetch, userAgent: "AppFitness/test" },
        "avena",
        2,
    );
    assertEquals(foods.map((f) => f.barcode), [
        "8410000810004",
        "8410000840001",
    ]);
    const url = new URL(seen!.url);
    assertEquals(url.searchParams.get("search_terms"), "avena");
    assertEquals(url.searchParams.get("page"), "2");
    assertEquals(url.searchParams.get("lc"), "es");
    assertEquals(url.searchParams.get("cc"), "es");
    assertEquals(
        new Headers(seen!.init!.headers).get("user-agent"),
        "AppFitness/test",
    );
    assert(seen!.init!.signal);
});

Deno.test("fetchOffProduct: found, not found and no-kcal", async () => {
    const respond = (name: string) => () =>
        Promise.resolve(Response.json(fixture(name)));
    const deps = (name: string) => ({
        fetch: respond(name),
        userAgent: "AppFitness/test",
    });
    const found = await fetchOffProduct(
        deps("product-8410000810004"),
        "8410000810004",
    );
    assertEquals(found?.name, "Copos de avena integral");
    assertEquals(
        await fetchOffProduct(deps("product-not-found"), "0000000000000"),
        null,
    );
    assertEquals(
        await fetchOffProduct(deps("product-no-kcal"), "8410000830002"),
        null,
    );
});

Deno.test("searchOff: retries once after a transient OFF 503 and returns the products", async () => {
    let calls = 0;
    const fakeFetch = () => {
        calls++;
        return Promise.resolve(
            calls === 1
                ? new Response("busy", { status: 503 })
                : Response.json(fixture("search-avena")),
        );
    };
    const foods = await searchOff(
        { fetch: fakeFetch, userAgent: "AppFitness/test", sleep: noSleep },
        "cereales",
        1,
    );
    assertEquals(calls, 2);
    assertEquals(foods.length, 2);
});

const noSleep = () => Promise.resolve();

function countingDeps(respond: () => Promise<Response>) {
    const state = { calls: 0 };
    return {
        state,
        deps: {
            fetch: () => {
                state.calls++;
                return respond();
            },
            userAgent: "AppFitness/test",
            sleep: noSleep,
        },
    };
}

Deno.test("searchOff: two consecutive 503s surface OffError after exactly two calls", async () => {
    const { state, deps } = countingDeps(() =>
        Promise.resolve(new Response("busy", { status: 503 }))
    );
    const err = await assertRejects(
        () => searchOff(deps, "avena", 1),
        OffError,
    );
    assertEquals(err.status, 503);
    assertEquals(state.calls, 2);
});

Deno.test("searchOff: a timeout is not retried", async () => {
    const { state, deps } = countingDeps(() =>
        Promise.reject(new DOMException("t", "TimeoutError"))
    );
    const err = await assertRejects(
        () => searchOff(deps, "avena", 1),
        OffError,
    );
    assertEquals(err.kind, "timeout");
    assertEquals(state.calls, 1);
});

Deno.test("searchOff: 4xx and 429 responses are not retried", async () => {
    for (const status of [400, 404, 429]) {
        const { state, deps } = countingDeps(() =>
            Promise.resolve(new Response("x", { status }))
        );
        const err = await assertRejects(
            () => searchOff(deps, "avena", 1),
            OffError,
        );
        assertEquals(err.status, status);
        assertEquals(state.calls, 1);
    }
});

Deno.test("searchOff: invalid JSON is not retried", async () => {
    const { state, deps } = countingDeps(() =>
        Promise.resolve(new Response("<html>", { status: 200 }))
    );
    await assertRejects(() => searchOff(deps, "avena", 1), OffError);
    assertEquals(state.calls, 1);
});

Deno.test("OFF failures surface as OffError (HTTP error, network error, timeout)", async () => {
    const mk = (f: () => Promise<Response>) => ({
        fetch: f,
        userAgent: "AppFitness/test",
        sleep: noSleep,
    });
    const http = await assertRejects(
        () =>
            searchOff(
                mk(() => Promise.resolve(new Response("x", { status: 503 }))),
                "avena",
                1,
            ),
        OffError,
    );
    assertEquals(http.kind, "unavailable");
    const net = await assertRejects(
        () =>
            fetchOffProduct(
                mk(() => Promise.reject(new TypeError("boom"))),
                "8410000810004",
            ),
        OffError,
    );
    assertEquals(net.kind, "unavailable");
    const timeout = await assertRejects(
        () =>
            searchOff(
                mk(() => Promise.reject(new DOMException("t", "TimeoutError"))),
                "avena",
                1,
            ),
        OffError,
    );
    assertEquals(timeout.kind, "timeout");
});

Deno.test("searchOff and fetchOffProduct do not narrow the response with a fields parameter", async () => {
    const urls: URL[] = [];
    const fakeFetch = (input: Request | URL | string) => {
        urls.push(new URL(String(input)));
        return Promise.resolve(
            Response.json(
                urls.length === 1
                    ? fixture("search-avena")
                    : fixture("product-full"),
            ),
        );
    };
    const deps = { fetch: fakeFetch, userAgent: "AppFitness/test" };
    await searchOff(deps, "avena", 1);
    await fetchOffProduct(deps, "8410000810004");
    assertEquals(urls.length, 2);
    for (const url of urls) assertEquals(url.searchParams.has("fields"), false);
});

Deno.test("normalizeProduct: the whole OFF product lands untouched in off_data", () => {
    const raw = fixture("product-full").product as Record<string, unknown>;
    const food = normalizeProduct(raw)!;
    assertEquals(food.off_data, raw);
    for (
        const key of [
            "ingredients_text",
            "nutriscore_grade",
            "nova_group",
            "additives_tags",
            "allergens_tags",
        ]
    ) {
        assert(key in food.off_data, key);
    }
    assertEquals(food.name, "Copos de avena integral");
    assertEquals(
        [food.kcal_100, food.protein_100, food.carbs_100, food.fat_100],
        [372, 13.5, 58.7, 7],
    );
});

Deno.test("fetchOffProduct and searchOff keep off_data on every usable product and still drop unusable ones", async () => {
    const found = await fetchOffProduct(
        {
            fetch: () =>
                Promise.resolve(Response.json(fixture("product-full"))),
            userAgent: "AppFitness/test",
        },
        "8410000810004",
    );
    assert(found?.off_data.ingredients_text);
    const foods = await searchOff(
        {
            fetch: () =>
                Promise.resolve(Response.json(fixture("search-avena"))),
            userAgent: "AppFitness/test",
        },
        "avena",
        1,
    );
    assertEquals(foods.length, 2);
    for (const f of foods) assert(typeof f.off_data.code === "string");
    assertEquals(normalizeProduct({ code: "1", product_name: "x" }), null);
});

Deno.test("isSpainSearchHit: Spain plus product_name_es is a hit", () => {
    assertEquals(
        isSpainSearchHit({
            countries_tags: ["en:spain"],
            product_name_es: "Avena",
        }),
        true,
    );
});

Deno.test("isSpainSearchHit: Spain plus lang es and product_name is a hit", () => {
    assertEquals(
        isSpainSearchHit({
            countries_tags: ["en:spain"],
            lang: "es",
            product_name: "Avena",
        }),
        true,
    );
});

Deno.test("isSpainSearchHit: Spain with only an English name is not a hit", () => {
    assertEquals(
        isSpainSearchHit({
            countries_tags: ["en:spain"],
            lang: "en",
            product_name: "Oats",
        }),
        false,
    );
});

Deno.test("isSpainSearchHit: a product not sold in Spain is not a hit", () => {
    assertEquals(
        isSpainSearchHit({
            countries_tags: ["en:france"],
            product_name_es: "Avena",
        }),
        false,
    );
});

Deno.test("isSpainSearchHit: multi-country product including Spain is a hit", () => {
    assertEquals(
        isSpainSearchHit({
            countries_tags: ["en:france", "en:spain"],
            product_name_es: "Avena",
        }),
        true,
    );
});

Deno.test("isSpainSearchHit: invalid input is false and never throws", () => {
    const invalid: unknown[] = [
        {},
        { product_name_es: "Avena" },
        { countries_tags: "en:spain", product_name_es: "Avena" },
        { countries_tags: ["en:spain"], product_name_es: "   " },
        { countries_tags: ["en:spain"], lang: "es", product_name: " " },
        { countries_tags: ["en:spain"], lang: "es" },
        null,
        undefined,
        [],
        "en:spain",
        5,
    ];
    for (const raw of invalid) assertEquals(isSpainSearchHit(raw), false);
});

Deno.test("searchOff: restricts OFF to Spain, keeps the other params and drops non-hits", async () => {
    let seen: URL | undefined;
    const fakeFetch = (input: Request | URL | string) => {
        seen = new URL(String(input));
        return Promise.resolve(Response.json(fixture("search-mixed")));
    };
    const foods = await searchOff(
        { fetch: fakeFetch, userAgent: "AppFitness/test" },
        "avena",
        1,
    );
    assertEquals(foods.map((f) => f.barcode), [
        "8410000810004",
        "3000000000002",
    ]);
    const p = seen!.searchParams;
    assertEquals(p.get("tagtype_0"), "countries");
    assertEquals(p.get("tag_contains_0"), "contains");
    assertEquals(p.get("tag_0"), "spain");
    assertEquals(p.get("search_terms"), "avena");
    assertEquals(p.get("json"), "1");
    assertEquals(p.get("page"), "1");
    assertEquals(p.get("page_size"), "20");
    assertEquals(p.get("lc"), "es");
    assertEquals(p.get("cc"), "es");
    assertEquals(p.has("sort_by"), false);
    assertEquals(p.has("fields"), false);
});

Deno.test("searchOff: a page where every product is filtered returns [] and the next page still works", async () => {
    const pages: (string | null)[] = [];
    const allForeign = {
        products: [{
            code: "3000000000001",
            product_name_es: "Copos",
            countries_tags: ["en:france"],
            nutriments: { "energy-kcal_100g": 370 },
        }],
    };
    const fakeFetch = (input: Request | URL | string) => {
        const page = new URL(String(input)).searchParams.get("page");
        pages.push(page);
        return Promise.resolve(
            Response.json(page === "2" ? allForeign : fixture("search-mixed")),
        );
    };
    const deps = { fetch: fakeFetch, userAgent: "AppFitness/test" };
    assertEquals(await searchOff(deps, "avena", 2), []);
    const next = await searchOff(deps, "avena", 3);
    assertEquals(next.length, 2);
    assertEquals(pages, ["2", "3"]);
});

Deno.test("fetchOffProduct: barcode lookup does not apply the Spain filter", async () => {
    const found = await fetchOffProduct(
        {
            fetch: () =>
                Promise.resolve(Response.json({
                    status: 1,
                    product: {
                        code: "3000000000001",
                        product_name: "Flocons",
                        lang: "fr",
                        countries_tags: ["en:france"],
                        nutriments: { "energy-kcal_100g": 370 },
                    },
                })),
            userAgent: "AppFitness/test",
        },
        "3000000000001",
    );
    assertEquals(found?.name, "Flocons");
});
