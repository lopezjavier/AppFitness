# E14 · Block follow-up → `v1.x` (version to be defined)

> **Status: BACKLOG (user, 2026-11-03).** Moved out of E13 by the user's decision "we're complicating things; we don't have a proper block yet". Nothing here is planned or sequenced: the epic-planner re-plans E14 (stories, Gherkin alignment with ADR-93/ADR-94, tasks, checkpoints) **after the user approves the new block** at E13's block checkpoint (Checkpoint E13-H). The task descriptions and Gherkin listed below are kept as written in E13 as input for that re-plan; they are **not** aligned with ADR-93/ADR-94 yet and must not be started as they are.

## Goal
Follow the user after a block exists, like a real trainer: weeks that follow the training (breaks pause the block), an end-of-block review that proposes the next block and any phase change, small changes applied on their own with "Deshacer" (load progression, calories inside a safe band, deloads), training around an injury with a graded return, and sensible reactions when life changes mid-block.

## Scope
- Includes (from E13, as backlog): plan weeks that follow the training and pause on breaks (US-73 amended part, US-88 break and return scenarios); end-of-block review, phase changes and the next block (US-83); "Priorizar" / "Mantener" (US-84); the "Entrenador" behaviour: automatic progression, calories and deloads with "Deshacer" / "Cancelar descarga" (US-85, US-86; the automatic parts of US-69, US-70, US-75); training around an injury, graded return and the persistent-discomfort note (US-87, US-90); life changes mid-block: "Revisar el bloque" with "Adaptar el bloque", "Bloque nuevo" or "Solo unos días", goal change, new exercises, turning 65 (US-91, US-92).
- Out of scope: as E13 (medical advice, generic disclaimers, illness rules, reverse dieting, refeeds, water and voice).

## Decisions
ADR-86 (end-of-block review, breaks, life changes; amended 2026-11-03), ADR-88 ("Entrenador"), ADR-89 (health note, train around an injury), ADR-93 and ADR-94 (how blocks are built and their lifecycle; E14 builds on them). Design input: `07-coach-design.md` §3 (progression, deload), §4 (safe band), §7, §12.3-§12.8 (incl. the fitness-scientist's 2026-11-03 rows 1-3 of §12.8: "Adaptar el bloque" only for place or equipment; days or minutes offer "Bloque nuevo" or "Solo unos días"). Open questions carried: **E13 Q55** ("Adaptar el bloque" and T-196/T-197), E13 Q44, Q45, Q47 (settled or recommended), Q52 (injury without an area), and the calorie part of **E13 Q58** (automatic calorie changes for a plan without a block) — all in `09-open-questions.md`.

**Minimal phase removed (user, 2026-11-03, E13 Q56; built out in E13 by T-205/T-206):** the backlog items that relied on "Mínimo" must be re-thought at the re-plan: `07` §12.8 row 2 (at 1 day a week the minimal phase was proposed), "Solo unos días" with fewer days (used the minimal routine), row 12 and US-84 (65+ minimal rules), the minimal exceptions of H1, the scheduled deload and the cardio by phase (`07` §3), US-86 "No scheduled deload in the minimal phase", US-88 busy-period and long-minimal scenarios.

## Dependencies
E13 complete (the model-built block, the routine checker, "Eliminar bloque", favourites, the report, history and chat).

## Stories (backlog; Gherkin kept in the E13 file until the re-plan)
The Gherkin of these stories stays in `E13-performance-assistant.md` under headings marked "→ E14 (backlog)", so the history of the decisions stays in one place; the re-plan moves and aligns them here.
- US-83 · End-of-block review and the next block
- US-84 · "Priorizar" or "Mantener" a muscle group
- US-85 · "Entrenador": small changes apply on their own
- US-86 · Scheduled deloads, cancelling one or asking for one
- US-87 · Training around an injury and coming back step by step
- US-88 · Breaks, busy periods and coming back (the break and return scenarios; the minimal phase was built in E13 by T-187)
- US-90 · A note when a severe discomfort does not go away
- US-91 · "Revisar el bloque" when my training situation changes
- US-92 · Other life changes
- The automatic and plan-week parts of US-69 ("Changing the calories within a block" and the safe band), US-70 (inside a block the progression applies on its own; coming back after 14 days), US-73 (a week without training does not count; coming back resumes), US-75 (the deload applies on its own)

## Tasks (backlog; detail kept in `tasks/todo.md` under "E14 backlog", not sequenced)
T-189 (plan weeks follow the training), T-190 and T-191 (end-of-block review, backend and UI), T-192 (automatic progression with "Deshacer"), T-193 (automatic calories and deloads, data cleaning), T-194 (injury, graded return, health note), T-196 and T-197 (life changes, pending E13 Q55); checkpoints E13-E and E13-F (proposed in E13, to be re-proposed). Parts of T-166 and T-167 that list automatic changes, reviews or health notes also move here (see those tasks).
