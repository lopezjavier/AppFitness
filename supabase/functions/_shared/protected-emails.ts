// Protected accounts (ADR-57): emails listed in the PROTECTED_EMAILS secret cannot be deleted.
// Import-free on purpose. The list is never logged or returned to a client.

/** Parses "a@x.com, B@y.com" into a set of trimmed, lowercased emails (empties dropped). */
export function parseProtectedEmails(raw: string | undefined): Set<string> {
    const out = new Set<string>();
    for (const part of (raw ?? "").split(",")) {
        const email = part.trim().toLowerCase();
        if (email) out.add(email);
    }
    return out;
}

/** True when the email is in the secret list. Missing secret or email -> false. */
export function isProtected(
    email: string | null | undefined,
    raw: string | undefined,
): boolean {
    const normalized = email?.trim().toLowerCase();
    if (!normalized) return false;
    return parseProtectedEmails(raw).has(normalized);
}
