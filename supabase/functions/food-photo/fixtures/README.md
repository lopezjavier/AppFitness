# food-photo fixtures

These files are **hand-written**, not recorded from the real API. They follow
the documented Gemini `generateContent` response shape
(`candidates[0].content.parts[0].text` holds the JSON string; parts marked
`thought: true` are skipped; errors use the
`{ "error": { code, message, status } }` body). Written 2026-10-25 for model
`gemini-3.5-flash-lite` (the default; the optional `GEMINI_MODEL` secret
overrides it).

The check against the real API is the T-106b retry after deploy (the key lives
only in the Supabase secret `GEMINI_API_KEY`).
