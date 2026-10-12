## Summary

<!-- What changes and why, in 1–3 sentences. -->

## Story / Task

- Story: US-##
- Task: T-###

## Gherkin scenarios covered

- [ ] Scenario: …

## Tests run

- [ ] `./gradlew ktlintCheck detekt`
- [ ] `./gradlew testLocalDebugUnitTest`
- [ ] `npx supabase test db`
- [ ] `deno test --allow-all supabase/functions`
- [ ] `maestro test --test-output-dir tests/e2e/screenshots tests/e2e/T-###.yaml`

<!-- Remove the lines that do not apply and explain why. -->

## QA notes / screenshots

<!-- Maestro verdict, screenshots of UI changes. "N/A" if the task has no UI. -->

## Security checklist

- [ ] No secrets, keys or personal data in the diff (the repo is public).
- [ ] Only the Supabase publishable key in the app; the secret key only in Edge Functions.
- [ ] Every new table has RLS with `(select auth.uid())` and a pgTAP cross-user isolation test.
- [ ] Migrations are new files; no merged migration was edited.
