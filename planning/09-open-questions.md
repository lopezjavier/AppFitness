# Open questions

Product questions raised by the user that are not (yet) ADRs. A durable resolution becomes an ADR in `00-decisions.md` and is linked here. Phase 1 questions: [`phase1/09-open-questions.md`](phase1/09-open-questions.md).

| Question | Raised | Status | Resolution |
|---|---|---|---|
| Where does the coach live in the 3-tab navigation (ADR-37)? | 2026-10-28 | Resolved | ADR-79: new tab "Trainer" between Entreno and Perfil |
| Training streak and rewards (rest of E8 US-44) | 2026-10-28 | Deferred, not in Phase 2 | `01-scope.md` §5 |
| Release version of each Phase 2 epic | 2026-10-28 | Resolved | The PM owns the version: each epic raises the minor, each phase the major (`06` §6) |
| E12 Q1: strong-pain term list and fixed message (US-67, US-66) | 2026-10-28 | Superseded by ADR-81 (T-152): no term list and no message; the user picks a level | ~~Terms "dolor fuerte", "dolor agudo", "dolor intenso", "pinchazo", "chasquido", "hormigueo", "entumecimiento", "hinchazón", "lesión", "me he lesionado", "desgarro", "no puedo mover" (plain "dolor" and "molestia" deliberately excluded); message "Si el dolor es fuerte o no mejora, consulta a un profesional sanitario." (E12 epic, US-67 notes)~~ |
| E12 Q2: session length range in "Mi entrenamiento" (US-65) | 2026-10-28 | Resolved | 15-180 min in 5-min steps, shown with the app's existing duration format ("45 min", "1 h", "1 h 30 min") (E12 epic, US-65 notes) |
| E13 Q1: US-70 discomfort scenarios still showed the fixed message (ADR-81) | 2026-10-29 | Resolved (user, 2026-10-29) | No health message; levels 1-3 block load increases only (reps rule and 5 % drop still apply) and the reason states neutrally that the load is kept because of the open discomfort; levels 4-5 give no suggestion (US-70 amended) |
| E13 Q2: where an exercise's rep range comes from | 2026-10-29 | Resolved | Optional rep range per routine exercise in the routine editor (R-47 control), per session, filled by the block creator; no range = no suggestion (US-70 amended, T-159) |
| E13 Q3: "main lifts", deload scope and rounding | 2026-10-29 | Resolved | Main / multi-joint lift = `movement_pattern` squat, hinge, lunge, horizontal or vertical push or pull (deload trigger: done in each of the three weeks); deload only inside an accepted block; 40 % cut rounded to the nearest whole set, at least 1 (US-72, US-75 notes; `07` §3) |
| E13 Q4: does the evaluation gate the plan and report before the checkpoints? | 2026-10-29 | Resolved | No: usable at the checkpoints (validators enforce the hard rules); the evaluation gates the chat and sharing the app (epic §Evaluation) |
| E13 Q5: coach labels vs the glossary | 2026-10-29 | Resolved | Glossary entries "Confirmar" (derived values) and "Aceptar" / "Descartar" (coach proposals); "Borrar historial del asistente" renamed "Eliminar historial del entrenador" (US-74 amended) |
| E13 Q6: when "the coach runs" | 2026-10-29 | Resolved | On each Trainer open and after saving the weekly check-in, at most once a day; never in the background (ADR-73) |
| E13 Q7: where the coach settings (AI usage) live | 2026-10-29 | Resolved | Read-only "Uso de IA" from the Trainer root's ⋮ menu (US-76 notes) |
| E13 Q8: Preferencias recalculation vs the plan's targets | 2026-10-29 | Resolved | Any save that changes the nutrition goal counts as a manual edit and marks the plan's nutrition as edited by the user (US-69 notes) |
| E13 Q9: one plan acceptance or two | 2026-10-29 | Resolved | Nutrition can be accepted alone before the block exists; "Guardar bloque" accepts the training part; both stored (US-69 notes) |
| E13 Q10: leaving the block preview | 2026-10-29 | Resolved | No routine created; the proposal stays reopenable until superseded; no dialog (US-72 notes) |
| E13 Q11: US-72 Gherkin for rest, order, periodization, cardio (`07` §3) | 2026-10-29 | Resolved | Scenarios added to US-72; range of motion stays a technique note |
| E13 Q12: US-69 Gherkin for protein distribution (`07` §4) | 2026-10-29 | Resolved | Scenario added to US-69 (≈ 0.4 g/kg per meal over ≥ 4 meals, guidance, not a meal plan) |
| E13 Q13: edited activity level and history deletion scope | 2026-10-29 | Resolved | Edited level stays a coach value (profile unchanged); deleting the coach history keeps the derived values and the AI usage (US-68, US-74 amended) |
