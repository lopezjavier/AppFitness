// Open Food Facts (OFF) client: pure normalization plus an injectable `fetch`.
// Never reads the environment: the User-Agent is passed in by the entrypoint.

export type BaseUnit = "g" | "ml";

export type Portion = { label: string; amount: number; position: number };

/** A product ready to be stored in `foods` (source = 'off'). */
export type NormalizedFood = {
    barcode: string;
    name: string;
    brand: string | null;
    base_unit: BaseUnit;
    kcal_100: number;
    protein_100: number;
    carbs_100: number;
    fat_100: number;
    serving_label: string | null;
    serving_amount: number | null;
    image_url: string | null;
    portions: Portion[];
};

/** A persisted food as returned to clients by the food functions. */
export type Food = Omit<NormalizedFood, "barcode" | "portions"> & {
    id: string;
    source: "off" | "user";
    barcode: string | null;
    portions: Array<Portion & { id: string }>;
};

export type OffDeps = {
    fetch: (input: URL | string, init?: RequestInit) => Promise<Response>;
    userAgent: string;
};

export class OffError extends Error {
    constructor(readonly kind: "timeout" | "unavailable") {
        super(`Open Food Facts ${kind}`);
        this.name = "OffError";
    }
}

const OFF_BASE = "https://world.openfoodfacts.org";
const TIMEOUT_MS = 8000;
const FIELDS = [
    "code",
    "product_name",
    "product_name_es",
    "brands",
    "nutriments",
    "nutrition_data_per",
    "serving_size",
    "image_front_small_url",
].join(",");

const MAX_NUTRIENT = 9999.99; // numeric(6,2)
const MAX_AMOUNT = 99999.9; // numeric(7,1)

/** Numeric value (number or numeric string) or undefined when absent; NaN marker -> "invalid". */
function readNumber(v: unknown): number | undefined | "invalid" {
    if (v === undefined || v === null || v === "") return undefined;
    const n = typeof v === "number"
        ? v
        : typeof v === "string" && /^-?\d+(\.\d+)?$/.test(v.trim())
        ? Number(v)
        : NaN;
    if (!Number.isFinite(n) || n < 0 || n > MAX_NUTRIENT) return "invalid";
    return n;
}

const round2 = (n: number) => Math.round(n * 100) / 100;

function text(v: unknown, max: number): string | null {
    if (typeof v !== "string") return null;
    const t = v.trim();
    return t === "" ? null : t.slice(0, max);
}

function parseServing(
    raw: unknown,
    unit: BaseUnit,
): { label: string; amount: number } | null {
    if (typeof raw !== "string") return null;
    const s = raw.trim();
    const num = String.raw`(\d+(?:[.,]\d+)?)`;
    const withLabel = new RegExp(
        String.raw`^(.*?)\s*\(\s*${num}\s*(g|ml)\s*\)$`,
        "i",
    ).exec(s);
    const plain = new RegExp(String.raw`^${num}\s*(g|ml)$`, "i").exec(s);
    let label: string;
    let amountText: string;
    let servingUnit: string;
    if (withLabel && withLabel[1].trim() !== "") {
        label = withLabel[1].trim();
        [, , amountText, servingUnit] = withLabel;
    } else if (plain) {
        [, amountText, servingUnit] = plain;
        label = s;
    } else if (withLabel) {
        // "(30 g)" without a leading label
        [, , amountText, servingUnit] = withLabel;
        label = `${amountText} ${servingUnit}`;
    } else {
        return null;
    }
    if (servingUnit.toLowerCase() !== unit) return null;
    const amount = Math.round(Number(amountText.replace(",", ".")) * 10) / 10;
    if (!(amount > 0) || amount > MAX_AMOUNT) return null;
    return { label: label.slice(0, 100), amount };
}

/** Converts a raw OFF product into a storable food, or null when it is unusable. Never throws. */
export function normalizeProduct(raw: unknown): NormalizedFood | null {
    try {
        if (typeof raw !== "object" || raw === null || Array.isArray(raw)) {
            return null;
        }
        const p = raw as Record<string, unknown>;
        const n = (typeof p.nutriments === "object" && p.nutriments !== null
            ? p.nutriments
            : {}) as Record<string, unknown>;

        const barcode = typeof p.code === "string" || typeof p.code === "number"
            ? String(p.code).trim()
            : "";
        const name = text(p.product_name_es, 200) ?? text(p.product_name, 200);
        if (!barcode || !name) {
            return null;
        }

        const has = (suffix: string) =>
            ["energy-kcal", "energy"].some((k) =>
                n[`${k}_${suffix}`] !== undefined
            );
        const unit: BaseUnit = p.nutrition_data_per === "100ml"
            ? "ml"
            : p.nutrition_data_per === "100g"
            ? "g"
            : !has("100g") && has("100ml")
            ? "ml"
            : "g";
        const sfx = unit === "ml" ? "100ml" : "100g";

        let kcal = readNumber(n[`energy-kcal_${sfx}`]);
        if (kcal === "invalid") {
            return null;
        }
        if (kcal === undefined) {
            const kj = readNumber(n[`energy_${sfx}`]);
            if (kj === "invalid" || kj === undefined) {
                return null;
            }
            kcal = round2(kj / 4.184);
        }
        const macro = (key: string) =>
            readNumber(n[`${key}_${sfx}`]);
        const protein = macro("proteins");
        const carbs = macro("carbohydrates");
        const fat = macro("fat");
        if (protein === "invalid" || carbs === "invalid" || fat === "invalid") {
            return null;
        }

        const serving = parseServing(p.serving_size, unit);
        const brand = typeof p.brands === "string"
            ? text(p.brands.split(",")[0], 200)
            : null;
        return {
            barcode,
            name,
            brand,
            base_unit: unit,
            kcal_100: kcal,
            protein_100: protein ?? 0,
            carbs_100: carbs ?? 0,
            fat_100: fat ?? 0,
            serving_label: serving?.label ?? null,
            serving_amount: serving?.amount ?? null,
            image_url: text(p.image_front_small_url, 2048),
            portions: serving
                ? [{
                    label: serving.label,
                    amount: serving.amount,
                    position: 0,
                }]
                : [],
        };
    } catch {
        return null;
    }
}

async function offGet(deps: OffDeps, url: URL): Promise<unknown> {
    let res: Response;
    try {
        res = await deps.fetch(url, {
            headers: {
                "user-agent": deps.userAgent,
                accept: "application/json",
            },
            signal: AbortSignal.timeout(TIMEOUT_MS),
        });
    } catch (err) {
        const timeout = err instanceof DOMException &&
            (err.name === "TimeoutError" || err.name === "AbortError");
        throw new OffError(timeout ? "timeout" : "unavailable");
    }
    // The body can also time out while streaming.
    try {
        if (!res.ok) throw new OffError("unavailable");
        return await res.json();
    } catch (err) {
        if (err instanceof OffError) throw err;
        const timeout = err instanceof DOMException &&
            err.name === "TimeoutError";
        throw new OffError(timeout ? "timeout" : "unavailable");
    }
}

/** Free-text search; unusable products are dropped. Throws OffError when OFF is unreachable. */
export async function searchOff(
    deps: OffDeps,
    q: string,
    page: number,
): Promise<NormalizedFood[]> {
    const url = new URL(`${OFF_BASE}/cgi/search.pl`);
    url.searchParams.set("search_terms", q);
    url.searchParams.set("json", "1");
    url.searchParams.set("page", String(page));
    url.searchParams.set("page_size", "20");
    url.searchParams.set("lc", "es");
    url.searchParams.set("cc", "es");
    url.searchParams.set("fields", FIELDS);
    const body = await offGet(deps, url) as { products?: unknown };
    if (!Array.isArray(body?.products)) return [];
    return body.products
        .map(normalizeProduct)
        .filter((f): f is NormalizedFood => f !== null);
}

/** Single product by barcode; null when OFF does not know it or it has no usable nutrition. */
export async function fetchOffProduct(
    deps: OffDeps,
    code: string,
): Promise<NormalizedFood | null> {
    const url = new URL(
        `${OFF_BASE}/api/v2/product/${encodeURIComponent(code)}.json`,
    );
    url.searchParams.set("lc", "es");
    url.searchParams.set("cc", "es");
    url.searchParams.set("fields", FIELDS);
    const body = await offGet(deps, url) as {
        status?: unknown;
        product?: unknown;
    };
    if (body?.status !== 1 || !body.product) return null;
    const food = normalizeProduct(body.product);
    return food && { ...food, barcode: food.barcode || code };
}
