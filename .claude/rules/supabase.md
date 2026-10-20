---
paths:
  - "supabase/**"
---

- Every new table gets RLS using `(select auth.uid())` and a pgTAP cross-user isolation test.
- Migrations: always add a new one; never edit a merged migration.
