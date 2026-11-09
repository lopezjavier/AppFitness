import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import {
    COLUMNS,
    favoriteFoodIds,
    findByBarcode,
    offRow,
    searchLocal,
    toFood,
} from "./food-store.ts";
import { normalizeProduct } from "./off-client.ts";

function fullProduct() {
    const raw = JSON.parse(
        Deno.readTextFileSync(
            new URL("./fixtures/off/product-full.json", import.meta.url),
        ),
    );
    return normalizeProduct(raw.product)!;
}

/** Fake PostgREST client: records the query and answers with the rows matching the `source` filter, if any. */
function fakeClient(rows: Array<Record<string, unknown>>) {
    const seen = {
        select: "",
        from: "",
        sources: [] as string[],
        limits: [] as number[],
        orders: [] as string[],
    };
    function chain() {
        let source: string | undefined;
        // deno-lint-ignore no-explicit-any
        const builder: any = {
            select(s: string) {
                seen.select = s;
                return builder;
            },
            textSearch: () => builder,
            eq(column: string, value: string) {
                if (column === "source") {
                    source = value;
                    seen.sources.push(value);
                }
                return builder;
            },
            order(column: string) {
                seen.orders.push(column);
                return builder;
            },
            limit(n: number) {
                seen.limits.push(n);
                return builder;
            },
            // deno-lint-ignore no-explicit-any
            then(resolve: (v: any) => unknown) {
                const data = rows.filter((r) => !source || r.source === source);
                return Promise.resolve({ data, error: null }).then(resolve);
            },
        };
        return builder;
    }
    return {
        client: {
            from: (t: string) => {
                seen.from = t;
                return chain();
            },
            // deno-lint-ignore no-explicit-any
        } as any,
        seen,
    };
}

const dbRow = {
    id: "f1",
    source: "off",
    barcode: "8410000810004",
    name: "Copos de avena integral",
    brand: null,
    base_unit: "g",
    kcal_100: "372",
    protein_100: "13.5",
    carbs_100: "58.7",
    fat_100: "7",
    serving_label: null,
    serving_amount: null,
    image_url: null,
    fetched_at: "2026-10-21T10:00:00Z",
    food_portions: [],
};

Deno.test("offRow stores the untouched OFF product in off_data next to the typed columns", () => {
    const food = fullProduct();
    const row = offRow(food, "2026-10-21T10:00:00Z");
    assertEquals(row.off_data, food.off_data);
    assert("ingredients_text" in row.off_data);
    assertEquals(row.kcal_100, 372);
    assertEquals(row.fetched_at, "2026-10-21T10:00:00Z");
    assertEquals("portions" in row, false);
});

Deno.test("toFood never emits off_data or micronutrients", () => {
    const food = toFood({
        ...dbRow,
        off_data: { code: "1" },
        micronutrients: { "fiber_100g": 1 },
    });
    assertEquals("off_data" in food, false);
    assertEquals("micronutrients" in food, false);
    assertEquals(food.kcal_100, 372);
});

Deno.test("searchLocal selects explicit columns, so off_data is not downloaded", async () => {
    const { client, seen } = fakeClient([dbRow]);
    const foods = await searchLocal(client, "avena", 20);
    assertEquals(foods.length, 1);
    assertEquals(seen.select.includes("*"), false);
    assertEquals(seen.select.includes("off_data"), false);
});

Deno.test("searchLocal reads the Spain-visibility view, not the foods table", async () => {
    const { client, seen } = fakeClient([dbRow]);
    await searchLocal(client, "avena", 20);
    assertEquals(seen.from, "foods_searchable");
});

Deno.test("findByBarcode reports whether the row already has off_data", async () => {
    const without = await findByBarcode(
        fakeClient([{ ...dbRow, off_data: null }]).client,
        "8410000810004",
    );
    assertEquals(without?.hasOffData, false);
    const withData = await findByBarcode(
        fakeClient([{ ...dbRow, off_data: { code: "1" } }]).client,
        "8410000810004",
    );
    assertEquals(withData?.hasOffData, true);
    assertEquals("off_data" in withData!.food, false);
});

Deno.test("offRow writes alcohol_g_100 from the normalised food", () => {
    const food = { ...fullProduct(), alcohol_100: 9.5 };
    assertEquals(offRow(food, "2026-10-21T10:00:00Z").alcohol_g_100, 9.5);
    assertEquals(
        offRow({ ...food, alcohol_100: null }, "2026-10-21T10:00:00Z")
            .alcohol_g_100,
        null,
    );
});

Deno.test("toFood maps alcohol_g_100 to alcohol_100 and keeps null", () => {
    assertEquals(toFood({ ...dbRow, alcohol_g_100: "9.5" }).alcohol_100, 9.5);
    assertEquals(toFood({ ...dbRow, alcohol_g_100: null }).alcohol_100, null);
    assertEquals(toFood(dbRow).alcohol_100, null);
});

Deno.test("COLUMNS includes alcohol_g_100 but the view select does not (foods_searchable has no such column)", async () => {
    assert(COLUMNS.split(",").includes("alcohol_g_100"));
    const { client, seen } = fakeClient([dbRow]);
    await searchLocal(client, "avena", 20);
    assert(!seen.select.includes("alcohol_g_100"));
    const barcode = fakeClient([dbRow]);
    await findByBarcode(barcode.client, "8410000810004");
    assert(barcode.seen.select.includes("alcohol_g_100"));
});

Deno.test("searchLocal queries own foods and OFF separately, each with the limit, and lists own first", async () => {
    const offRows = Array.from(
        { length: 25 },
        (_, i) => ({ ...dbRow, id: `off${i}`, source: "off" }),
    );
    const own = { ...dbRow, id: "mine", source: "user" };
    const { client, seen } = fakeClient([...offRows, own]);
    const foods = await searchLocal(client, "avena", 20);
    assertEquals(seen.sources.sort(), ["off", "user"]);
    assertEquals(seen.limits, [20, 20]);
    assertEquals(foods[0].id, "mine");
    assertEquals(foods.length, 26);
});

Deno.test("favoriteFoodIds returns the ids of the favourite foods", async () => {
    const { client, seen } = fakeClient([
        { food_id: "a" },
        { food_id: "b" },
    ]);
    const ids = await favoriteFoodIds(client);
    assertEquals(seen.from, "favorite_foods");
    assertEquals([...ids].sort(), ["a", "b"]);
});

Deno.test("findByBarcode asks for the oldest first and the oldest own food wins", async () => {
    const { client, seen } = fakeClient([
        { ...dbRow, id: "off", source: "off", off_data: null },
        { ...dbRow, id: "old-own", source: "user", off_data: null },
        { ...dbRow, id: "new-own", source: "user", off_data: null },
    ]);
    const found = await findByBarcode(client, "8410000810004");
    assertEquals(seen.orders, ["created_at"]);
    assertEquals(found?.food.id, "old-own");
});
