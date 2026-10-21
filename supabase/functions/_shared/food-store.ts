// Database access shared by the food Edge Functions (wired by the index.ts entrypoints; handlers never import it).
import {
    createClient,
    type SupabaseClient,
} from "npm:@supabase/supabase-js@2.58.0";
import type { Food, NormalizedFood } from "./off-client.ts";
import { secretKey } from "./secret-key.ts";

const REQUEST_TIMEOUT_MS = 8000;
// Explicit columns: `off_data` (the full OFF product) must never travel with search results.
const COLUMNS =
    "id,source,barcode,name,brand,base_unit,kcal_100,protein_100,carbs_100,fat_100,serving_label,serving_amount,image_url,fetched_at";
const PORTIONS = "food_portions(id,label,amount,position)";
const SELECT = `${COLUMNS}, ${PORTIONS}`;

// deno-lint-ignore no-explicit-any
type Client = SupabaseClient<any, "public", any>;

function options() {
    return {
        auth: { persistSession: false, autoRefreshToken: false },
        global: {
            fetch: (input: URL | Request | string, init?: RequestInit) =>
                fetch(input, {
                    ...init,
                    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
                }),
        },
    };
}

function supabaseUrl(): string {
    const url = Deno.env.get("SUPABASE_URL");
    if (!url) throw new Error("SUPABASE_URL is not set");
    return url;
}

/** Secret-key client: bypasses RLS, only for token validation and OFF cache writes. */
export function adminClient(): Client {
    return createClient(supabaseUrl(), secretKey(), options());
}

/** Client acting as the caller (their JWT), so RLS decides what is readable. */
export function callerClient(token: string): Client {
    const key = Deno.env.get("SUPABASE_ANON_KEY") ??
        firstOf(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS"));
    if (!key) throw new Error("no publishable key available");
    const base = options();
    return createClient(supabaseUrl(), key, {
        ...base,
        global: {
            ...base.global,
            headers: { Authorization: `Bearer ${token}` },
        },
    });
}

function firstOf(json: string | undefined): string | undefined {
    if (!json) return undefined;
    try {
        return Object.values(JSON.parse(json) as Record<string, string>)[0];
    } catch {
        return json;
    }
}

export async function authenticate(token: string): Promise<string | null> {
    const { data, error } = await adminClient().auth.getUser(token);
    if (error || !data.user) return null;
    return data.user.id;
}

/** Removes diacritics: `search_vector` is built over unaccented text. */
export function unaccent(text: string): string {
    return text.normalize("NFD").replace(/[̀-ͯ]/g, "");
}

// deno-lint-ignore no-explicit-any
export function toFood(row: any): Food {
    const portions = [...(row.food_portions ?? [])]
        .sort((a, b) => a.position - b.position)
        .map((p) => ({
            id: p.id,
            label: p.label,
            amount: Number(p.amount),
            position: p.position,
        }));
    return {
        id: row.id,
        source: row.source,
        barcode: row.barcode,
        name: row.name,
        brand: row.brand,
        base_unit: row.base_unit,
        kcal_100: Number(row.kcal_100),
        protein_100: Number(row.protein_100),
        carbs_100: Number(row.carbs_100),
        fat_100: Number(row.fat_100),
        serving_label: row.serving_label,
        serving_amount: row.serving_amount === null
            ? null
            : Number(row.serving_amount),
        image_url: row.image_url,
        portions,
    };
}

export async function searchLocal(
    client: Client,
    q: string,
    limit: number,
): Promise<Food[]> {
    const { data, error } = await client
        .from("foods")
        .select(SELECT)
        .textSearch("search_vector", unaccent(q), {
            type: "websearch",
            config: "spanish",
        })
        .limit(limit);
    if (error) throw error;
    return (data ?? []).map(toFood);
}

export async function findByBarcode(
    client: Client,
    code: string,
): Promise<
    { food: Food; fetchedAt: Date; hasOffData: boolean } | null
> {
    const { data, error } = await client
        .from("foods")
        .select(`${SELECT}, off_data`)
        .eq("barcode", code);
    if (error) throw error;
    const rows = data ?? [];
    // RLS exposes OFF rows and the caller's own foods; the personal food wins.
    const row = rows.find((r) => r.source === "user") ?? rows[0];
    return row
        ? {
            food: toFood(row),
            fetchedAt: new Date(row.fetched_at),
            hasOffData: row.off_data != null,
        }
        : null;
}

/** Database row for an OFF food: typed columns plus the untouched OFF product in `off_data`. */
export function offRow(f: NormalizedFood, fetchedAt: string) {
    return {
        barcode: f.barcode,
        name: f.name,
        brand: f.brand,
        base_unit: f.base_unit,
        kcal_100: f.kcal_100,
        protein_100: f.protein_100,
        carbs_100: f.carbs_100,
        fat_100: f.fat_100,
        serving_label: f.serving_label,
        serving_amount: f.serving_amount,
        image_url: f.image_url,
        off_data: f.off_data,
        fetched_at: fetchedAt,
    };
}

/**
 * Inserts or refreshes OFF foods (source 'off') and replaces their portions. The unique index on barcode
 * is partial, which PostgREST `onConflict` cannot target, so existing rows are looked up first.
 */
export async function upsertOffFoods(
    admin: Client,
    foods: NormalizedFood[],
): Promise<Food[]> {
    const unique = [...new Map(foods.map((f) => [f.barcode, f])).values()];
    if (unique.length === 0) return [];
    const now = new Date().toISOString();

    const { data: existing, error: e1 } = await admin
        .from("foods")
        .select("id,barcode")
        .eq("source", "off")
        .in("barcode", unique.map((f) => f.barcode));
    if (e1) throw e1;
    const idByBarcode = new Map<string, string>(
        (existing ?? []).map((r) => [r.barcode as string, r.id as string]),
    );

    const row = (f: NormalizedFood) => offRow(f, now);

    for (const f of unique.filter((f) => idByBarcode.has(f.barcode))) {
        const { error } = await admin.from("foods").update(row(f)).eq(
            "id",
            idByBarcode.get(f.barcode)!,
        );
        if (error) throw error;
    }
    const fresh = unique.filter((f) => !idByBarcode.has(f.barcode));
    if (fresh.length > 0) {
        const { data, error } = await admin
            .from("foods")
            .insert(fresh.map((f) => ({ ...row(f), source: "off" })))
            .select("id,barcode");
        if (error) throw error;
        for (const r of data ?? []) idByBarcode.set(r.barcode, r.id);
    }

    const ids = unique.map((f) => idByBarcode.get(f.barcode)!);
    const { error: e2 } = await admin.from("food_portions").delete().in(
        "food_id",
        ids,
    );
    if (e2) throw e2;
    const portions = unique.flatMap((f) =>
        f.portions.map((p) => ({ ...p, food_id: idByBarcode.get(f.barcode)! }))
    );
    if (portions.length > 0) {
        const { error } = await admin.from("food_portions").insert(portions);
        if (error) throw error;
    }

    const { data: stored, error: e3 } = await admin
        .from("foods")
        .select(SELECT)
        .in("id", ids);
    if (e3) throw e3;
    const byId = new Map((stored ?? []).map((r) => [r.id, toFood(r)]));
    return ids.map((id) => byId.get(id)!).filter(Boolean);
}
