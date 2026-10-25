# food-photo fixtures

These files are **hand-written**, not recorded from the real API. They follow
the documented Gemini `generateContent` response shape
(`candidates[0].content.parts[0].text` holds the JSON string; errors use the
`{ "error": { code, message, status } }` body). Written 2026-10-25 for model
`gemini-2.5-flash`.

The check against the real API is deferred to the T-106 manual checkpoint (the
key lives only in the Supabase secret `GEMINI_API_KEY`).
