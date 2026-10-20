// Input validation for the food Edge Functions.

export type Validated<T> = { ok: true; value: T } | {
    ok: false;
    message: string;
};

/** `q`: trimmed, 2-60 characters. */
export function parseQuery(raw: string | null): Validated<string> {
    const q = (raw ?? "").trim();
    if (q.length < 2 || q.length > 60) {
        return { ok: false, message: "q must be between 2 and 60 characters" };
    }
    return { ok: true, value: q };
}

/** `page`: absent -> 1; otherwise an integer >= 1. */
export function parsePage(raw: string | null): Validated<number> {
    if (raw === null || raw === "") return { ok: true, value: 1 };
    if (!/^\d{1,4}$/.test(raw) || Number(raw) < 1) {
        return { ok: false, message: "page must be an integer >= 1" };
    }
    return { ok: true, value: Number(raw) };
}

/** `code`: digits only; EAN-8 / UPC-E (8), UPC-A (12) or EAN-13 (13). */
export function parseBarcode(raw: string | null): Validated<string> {
    const code = (raw ?? "").trim();
    if (!/^\d+$/.test(code) || ![8, 12, 13].includes(code.length)) {
        return { ok: false, message: "code must be 8, 12 or 13 digits" };
    }
    return { ok: true, value: code };
}
