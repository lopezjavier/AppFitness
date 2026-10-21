import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { findByBarcode, offRow, searchLocal, toFood } from "./food-store.ts";
import { normalizeProduct } from "./off-client.ts";

function fullProduct() {
    const raw = JSON.parse(
        Deno.readTextFileSync(
            new URL("./fixtures/off/product-full.json", import.meta.url),
        ),
    );
    return normalizeProduct(raw.product)!;
}

/** Fake PostgREST client that records the select string and answers with the given rows. */
function fakeClient(rows: unknown[]) {
    const seen = { select: "" };
    // deno-lint-ignore no-explicit-any
    const builder: any = {
        select(s: string) {
            seen.select = s;
            return builder;
        },
        textSearch: () => builder,
        limit: () => Promise.resolve({ data: rows, error: null }),
        eq: () => Promise.resolve({ data: rows, error: null }),
    };
    // deno-lint-ignore no-explicit-any
    return { client: { from: () => builder } as any, seen };
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
