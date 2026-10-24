import { assertEquals } from "jsr:@std/assert@1.0.19";
import { isProtected, parseProtectedEmails } from "./protected-emails.ts";

Deno.test("parse trims, lowercases, drops empties and duplicates", () => {
    assertEquals(
        [...parseProtectedEmails(" A@x.com , b@Y.com,,a@X.com, ")],
        ["a@x.com", "b@y.com"],
    );
});

Deno.test("parse of undefined, empty or only commas is empty", () => {
    assertEquals(parseProtectedEmails(undefined).size, 0);
    assertEquals(parseProtectedEmails("").size, 0);
    assertEquals(parseProtectedEmails(" , ,, ").size, 0);
});

Deno.test("isProtected matches exact email", () => {
    assertEquals(isProtected("a@x.com", "a@x.com"), true);
});

Deno.test("isProtected ignores case on either side", () => {
    assertEquals(isProtected("A@X.com", "a@x.COM"), true);
});

Deno.test("isProtected finds an email among several with spaces", () => {
    assertEquals(isProtected("b@y.com", "a@x.com ,  B@y.com , c@z.com"), true);
});

Deno.test("isProtected is false for an unlisted email", () => {
    assertEquals(isProtected("other@x.com", "a@x.com,b@y.com"), false);
});

Deno.test("isProtected is false for a missing or empty secret", () => {
    assertEquals(isProtected("a@x.com", undefined), false);
    assertEquals(isProtected("a@x.com", ""), false);
    assertEquals(isProtected("a@x.com", ",,"), false);
});

Deno.test("isProtected is false for a missing email", () => {
    assertEquals(isProtected(null, "a@x.com"), false);
    assertEquals(isProtected(undefined, "a@x.com"), false);
    assertEquals(isProtected("", ",a@x.com"), false);
});
