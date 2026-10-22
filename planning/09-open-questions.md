# Open questions

Product questions raised by the user that are not (yet) ADRs. A durable resolution becomes an ADR in `00-decisions.md` and is linked here.

| Question | Raised | Status | Resolution |
|---|---|---|---|
| Open Food Facts alternative | 2026-10-20 | resolved 2026-10-20 | OFF stays: the barcode scanner works well; text search is imperfect. |
| "Almuerzo" as a default meal | 2026-10-20 | resolved 2026-10-20 | Not a default; the user creates it through the custom meal structure. |
| E6 Q1 · Per-set planning vs "Routine asks only sets and rest" (US-29); placeholders; target RPE placement | 2026-10-22 | resolved 2026-10-22 | Per-set planning holds; the other scenario is removed. Planned weight/reps are dim placeholders in the live session; target RPE per exercise. ADR-50. |
| E6 Q2 · "Deshacer" when deleting a set in the live session (US-38) | 2026-10-22 | resolved 2026-10-22 | No undo: immediate delete (ADR-45 kept). ADR-51. |
| E6 Q3 · Deleting a routine folder (US-35) | 2026-10-22 | resolved 2026-10-22 | Deletes the folder and all its routines, after a confirmation stating how many (exception to ADR-45). ADR-51. |
| E6 Q4 · Calculator defaults (US-33) | 2026-10-22 | resolved 2026-10-22 | Warm-up bar×10, 40 %×5, 60 %×3, 80 %×2; bars 20, 15, 10 EZ, 7,5 kg; plates 25…0,5 kg incl. 2 and 1 kg; rounding 2,5 kg plates / 2 kg dumbbells. ADR-52, `04` §4.9. |
| E6 Q5 · Live PR notification levels; "Total de repes" (US-34) | 2026-10-22 | resolved 2026-10-22 | Live notification and its levels deferred; "Total de repes" = all-time total. ADR-52; deferred text in `01` §4.1. |
| E6 Q6 · Streak and rest days in the calendar (US-37) | 2026-10-22 | resolved 2026-10-22 | Deferred (streak, rest days, rewards); the calendar shows trained days. ADR-52; `01` §4.1. |
| E6 Q7 · What "copy a workout" does (US-37) | 2026-10-22 | resolved 2026-10-22 | Copy deferred; edit, delete, "Guardar como rutina" and past workouts (empty, from a routine or from a finished workout) kept. ADR-52; `01` §4.1. |
| E6 Q8 · Food editing (story, ADR, split) | 2026-10-22 | resolved 2026-10-22 | US-47: only manual foods editable; edits apply to future entries only (snapshots kept, ADR-32 unchanged); portions can be added, edited and removed; full label nutrients in label order. ADR-53; one task (T-102). |

## Opt-in opencode executor (ADR-41)

- Raised: 2026-10-20. Status: open (waiting for the user).
- The proposal lives on branch `docs/opencode-executor-mode`; apply it only when the user asks (planned for E5). E4 runs with the current flow.
