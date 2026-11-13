# UX critic ledger

One row per ux-critic finding. The PM has the dispositions recorded here as the user decides (rules mode of ux-reviewer). The critic reads it first and does not repeat a row in any status except `REWORK`, where it may only propose a different solution.

| ID | Pattern (keywords) | Round | Status | Link |
|---|---|---|---|---|
| C-01 | selected state: selectedContainer #354925 + 1dp lime border + lime W700 label (chips, toggle chips, weekday, portion, cooking, segmented, time range) | 0 | IN REVIEW | 05 §2.1a, §6.0, R-64 |
| C-02 | disabled PrimaryButton: lime 20 % container, lime 50 % label | 0 | IN REVIEW | 05 §2.1a, R-65 |
| C-03 | textTertiary #8C97A9 for previous-set cell, placeholders, wheel neighbours; ghostData only disabled | 0 | IN REVIEW | 05 §2.1a, R-65 |
| C-04 | SetRow header «ANTES», tracking 0.02 em, REPS weights, RPE variant inset 8 dp | 0 | IN REVIEW | 05 §3, §6.0, §10 |
| C-05 | borderControl white 36 % for input resting borders | 0 | IN REVIEW | 05 §2.1a, §6.0 |
| C-06 | surfaceControl #242E3B for inputs, steppers, pending set circle, SecondaryButton | 0 | IN REVIEW | 05 §2.1a, §6.0 |
| C-07 | textSecondary #A9B3C4 | 0 | IN REVIEW | 05 §2.1a |
| C-08 | GhostButton neutral, focus border lime 2 dp | 0 | IN REVIEW | 05 §6.0 |
| C-09 | tabs shared 12 sp label, scroll (min 72 dp) only if a word does not fit | 0 | REWORK (tried 12 sp; user found it too small in the search tabs: now 14 sp, 6/8 dp padding, full-bleed, indicator visible while scrolling) | 05 §9 (amended), §6.0 |
| C-10 | ring caption/status bodyMedium 14/20 W600 | 0 | IN REVIEW | 05 §3, §6.0 |
| C-11 | trackInactive white 16 % + 4 dp stop dot on linear bars | 0 | IN REVIEW | 05 §2.1a, §6.0 |
| C-12 | WeekdaySelector 7 weighted cells, no scroll, cells >= 40x48 dp | 0 | IN REVIEW | 05 §6.0, §7 exception |
| C-13 | titleSection 18/24 W600 for ListSectionHeader, top-bar title W700 | 0 | IN REVIEW | 05 §3, §6.0 |
| C-14 | AddItemButton leading ic_add 20 dp | 0 | IN REVIEW | 05 §6.0 |
| C-15 | WorkoutRow headline titleMedium, one bodyMedium supporting line, empty trailing | 0 | IN REVIEW | 05 §6.0 |
| C-16 | nav labels sentence case 12/16 W600, icons 24 dp | 0 | IN REVIEW | 05 §3, §6.0, §10 |
| C-17 | EmptyState 32 dp glyph in 72 dp circle, per-domain icon; error buttons min width 200 dp | 0 | IN REVIEW | 05 §6.0, R-66 |
| C-18 | sleep stages cyan ramp 100/75/50 % + amber Despierto | 0 | IN REVIEW | 05 §2.1a |
| C-19 | PrimaryMinHeight 56 dp, radius 16 dp | 0 | IN REVIEW | 05 §6.0 |
| V-1 | discomfort row: borderless text action, no mid-word break | 0 | IN REVIEW | 05 §6.0 (R-51) |
| V-2 | REPS field >= 48 dp | 0 | IN REVIEW | 05 §6.0 (R-58) |
| V-3 | legacy filled icons to Outlined (ic_info, ic_photo_camera, ic_photo_library, ic_label, ic_auto_awesome, ic_nav_trainer, ic_health_connect). ic_nav_trainer history: original filled `insights` (liked) > T-294 hand-drawn trend+sparkle (rejected, broken) > `insert_chart` bf5317ff (rejected, degradation) > decision 2026-11-13: Material Symbols Outlined `insights`, verbatim path | 0 | IN REVIEW | 05 §6.0 |
| V-4 | remove unused sheetHandle and glowLime/glowBlur tokens | 0 | IN REVIEW | 05 §2.1a |
| C-20 | R-64 extended to leftovers: SelectableCard (fill, 1 dp border, lime title), OptionPickerSheet check lime, calendar today = 1 dp cyan ring (selected+today = R-64 plus a 1.5 dp (`AppBorder.focus`) cyan outer ring with a 2 dp gap to the lime border), AppCard `selected` param for ActivitySummary, `highlighted` stays informational cyan, sleep swatch 12 dp (alphas kept) | 0 | ADOPTED | 05 R-64 (b)-(f), §6.0 |
| C-21 | add-food screen: search field full width, scan in trailing slot, "Añadir con IA" and "Crear alimento" as top-bar actions | 1 | IN REVIEW | 05 R-69 |
| C-22 | edit-entry sheet: "Eliminar" text-style danger action >= 16 dp below "Guardar", no dialog | 1 | IN REVIEW | 05 R-70 |
| C-23 | remaining kcal only in the ring status pill ("Te quedan N kcal" / "Objetivo cumplido" / "Quedaron N kcal"), removed from empty meal cards | 1 | IN REVIEW | 05 R-67 |
| C-24 | check-in: 3 MetricTiles in one row | 1 | IN REVIEW | 05 visual items |
| C-25 | token `macroFat` #A78BFA (orange is the over-target colour) | 1 | REWORK (tried #A78BFA violet, user disliked it; now #F472B6 pink) | 05 §2.3, visual items |
| C-26 | nested containers on sheets use `surfaceControl` | 1 | IN REVIEW (build waits for round 0 verdict) | 05 visual items |
| C-27 | pinned footer: hairline plus fade | 1 | IN REVIEW (build waits for round 0 verdict) | 05 visual items, §9 |
| C-28 | meal rows inside `AppCard` | 1 | REWORK (card kept; its header is redone by R-75: toggle row, save icon and lime "+Añadir", kcal pill, macros line) | 05 visual items |
| C-29 | compact hero: ring 132 dp with stacked macros, full ring at font scale >= 1.15 | 1 | IN REVIEW (build waits for round 0 verdict) | 05 R-68 |
| C-30 | entry typography | 1 | REWORK (entry kcal now 14 sp W600 in `FoodEntryRow` and `DiaryGroupRow`) | 05 visual items |
| C-31 | plural-aware reminders ("Toca pesarte", "Llevas N días sin pesarte"; measures likewise) | 1 | IN REVIEW | 05 R-72, guard G21 |
| C-32 | per-type notice icons, hide "Pendientes" at 0 | 1 | IN REVIEW | 05 R-74 |
| C-33 | calendar resting cells without fill | 1 | IN REVIEW (build waits for round 0 verdict) | 05 visual items |
| C-34 | energy notice icon and copy | 1 | IN REVIEW | 05 R-74 |
| C-35 | inline partial-failure notice | 1 | IN REVIEW | 05 R-74 |
| C-36 | nutrient table rows | 1 | IN REVIEW | 05 visual items |
| C-37 | chip row runs to the sheet edge | 1 | IN REVIEW (build waits for round 0 verdict) | 05 visual items |
| C-38 | meal chips labelled "Comida del día" | 1 | IN REVIEW | 05 visual items, §10 |
| C-39 | add button names its destination "Añadir a <comida>" (meal name as typed, no article) | 1 | REWORK (wording kept; the text button becomes the description of the compact "+" icon button) | 05 R-69 |
| C-40 | tablet navigator capped at 320 dp | 1 | IN REVIEW (build waits for round 0 verdict) | 05 visual items |
| C-41 | lime caret in text fields | 1 | IN REVIEW (build waits for round 0 verdict) | 05 visual items |
| C-42 | goals prompt is a NavigationRow with icon and chevron, no "?" | 1 | IN REVIEW | 05 R-73 |
| C-43 | photo hint | 1 | IN REVIEW | 05 visual items |
| V-5 | R-30 offline: only the shell says "Sin conexión" (rule violation fixed) | 1 | IN REVIEW | 05 R-30 |
| V-6 | R-37 message templates and punctuation | 1 | IN REVIEW | 05 R-37 |
| V-7 | R-27 validation under the field after the first edit | 1 | IN REVIEW | 05 R-27 |
| V-8 | R-03 navigation entries are `NavigationRow` | 1 | IN REVIEW | 05 R-03, R-73 |
| V-9 | R-31 loading, empty, error and progress states | 1 | IN REVIEW | 05 R-31 |
| V-10 | C-09 tab label evidence (swept Diario tab rows) | 1 | IN REVIEW | 05 §9 |
| V-11 | C-17 empty and error evidence (swept Diario states) | 1 | IN REVIEW | 05 R-66 |
| V-12 | R-35 glossary wording | 1 | IN REVIEW | 05 R-35, §10 |
| V-13 | R-44 dates and numbers; fix only if the sweep confirms a violation | 1 | PROPOSED | 05 R-44 |
| C-44 | meal card header redesign: name + chevron toggle row, buttons at the trailing edge | 1 | IN REVIEW (batch 3; adjusted: exactly two buttons, save icon (not the bookmark-plus) and lime text button "+Añadir", no ⋮) | 05 R-75 |
| C-57 | meal card header compact layout (batch 4 feedback + final decisions): kcal pill right of name + chevron, macros under the name, both buttons on the right, no top padding, two-line name wrap, same collapsed and expanded | 1 | IN REVIEW (batch 4, PENDING until the user approves in the emulator; supersedes the pill-under-the-name layout of C-44/C-47) | 05 R-75, §6.0, §9 |
| C-58 | both buttons always visible collapsed and expanded; only the save button (and kcal pill) hidden on an empty meal | 1 | IN REVIEW (batch 4; supersedes C-49) | 05 R-75, §10 |
| C-59 | save icon = outlined rounded bookmark with a plus inside in a circular tonal button (`ic_save_meal`); replaces the floppy `ic_save` | 1 | IN REVIEW (batch 4; supersedes the "no plus" wording of C-46/C-48) | 05 §6.0, §10 |
| C-45 | critic's original meal card layout variant | 1 | SUPERSEDED by C-55 as adjusted by the user | 05 R-75 |
| C-46 | compact tonal icon button (48 dp target, 36 dp circle) for the save icon only; the "+" becomes a lime compact text button "+Añadir" (48 dp target) | 1 | IN REVIEW (batch 3; final user decision 2026-11-13) | 05 §6.0, R-75 |
| C-47 | compact kcal pill under the meal name, no border; none for an empty meal | 1 | IN REVIEW (batch 3) | 05 §6.0, R-75 |
| C-48 | "Guardar como comida" icon button (new icon chosen at build, not the bookmark-plus) only with entries; empty meal has none | 1 | IN REVIEW (batch 3; final user decision: no ⋮ menu, no separate saved-comida icon) | 05 R-75 |
| C-49 | "+" shown only while the card is expanded | 1 | SUPERSEDED by C-58 (user, 2026-11-13, batch 4: both buttons always visible) | 05 R-75 |
| C-50 | no ⋮ on a food row (swipe and edit sheet carry delete) | 1 | IN REVIEW (batch 3) | 05 R-75 |
| C-51 | saved-comida icon in the meal card header | 1 | REJECTED (user, 2026-11-13): saved comidas stay in the "Guardadas" tab | 05 §8 Decided |
| C-52 | search tab row 14 sp, 6/8 dp padding, full-bleed; "Más comidos" -> "Habituales" | 1 | IN REVIEW (batch 3) | 05 R-77, §6.0, §10 |
| C-53 | restaurant icon on the meal card | 1 | REJECTED (user, 2026-11-13) | 05 §8 Decided |
| C-54 | macros line hidden when the card is collapsed | 1 | REJECTED (user, 2026-11-13): shown expanded and collapsed, as today | 05 §8 Decided |
| C-55 | meal card per the critic, with the user's corrections: exactly two buttons (save icon, not the bookmark-plus; lime text button "+Añadir"), no ⋮, macros line always visible, collapsed card about 100 dp | 1 | IN REVIEW (batch 3; as adjusted) | 05 R-75 |
| C-56 | critic's third header variant (extra header buttons) | 1 | SUPERSEDED / REJECTED: at most two buttons | 05 §8 Decided |
| V-14 | tab row shows no indicator while scrolling: indicator stays visible | 1 | IN REVIEW (batch 3) | 05 §6.0, R-77 |
| V-15 | compact hero macro labels break mid-word: never | 1 | REWORK (still failing on device after batch 3: "PRO/T.", "CAR/B.", "GRAS/A"; batch 4 states it testably: single-line labels, fall back to ring over three bars, host test at 360 dp and font scale 1.0/1.3/2.0; PENDING) | 05 R-76 |
| V-16 | search tab row full-bleed edge to edge | 1 | IN REVIEW (batch 3) | 05 R-77 |
| V-17 | ⋮ "Más acciones de %1$s" wording on the meal card | 1 | WITHDRAWN (user, 2026-11-13): the meal card has no ⋮; its bookmark-plus keeps "Guardar como comida" | 05 §10 |
| C-60 | `DiaryGroupRow` uses `FoodEntryRow`'s slots: name + 24 dp chevron as headline, kcal 14 sp W600 over `MacroText` as trailing; overlay box starts 8 dp from the card edge | 1 | PENDING (batch 5) | 05 R-78 |
| C-61 | collapsed or empty meal card bottom padding 4 dp (expanded keeps 16) | 1 | PENDING (batch 5) | 05 R-78 |
| C-62 | header macros: letter textPrimary W700, figure macro colour W600, bodySmall, " · " in textTertiary attached to the preceding item | 1 | PENDING (batch 5) | 05 R-78 |
| C-63 | 4 dp between the name row and the macros line | 1 | PENDING (batch 5) | 05 R-78 |
| C-64 | 1 dp borderSubtle hairline in the 8 dp gap, only expanded with entries | 1 | PENDING (batch 5) | 05 R-78 |
| C-65 | meal with no entries: whole macros line in textTertiary | 1 | PENDING (batch 5) | 05 R-78 |
| V-18 | retire `MealSummaryText` in favour of `MacroText` (R-39) | 1 | PENDING (batch 5) | 05 R-78 |
| V-19 | retire `MacroSummary` in favour of `MacroText` (R-39) | 1 | PENDING (batch 5) | 05 R-78 |
| V-20 | short forms Prot./Carb./Grasa and P/C/G added to the glossary | 1 | PENDING (batch 5) | 05 §10 |
| V-15b | V-15 follow-up: `compactMacrosFit` measures the real ring-column width; host test required; 360 dp header fallback (pill moves to the macros line) | 1 | PENDING (batch 5; part of V-15 and R-75) | 05 R-76, R-78 |
| U-1 | diary entry with ingredients: collapsed swipe removes the whole food (user request, explicit exception to "no behaviour change"; reconfirmed in batch 4, unchanged) | 1 | IN REVIEW | 05 R-71 |

Status values:
- `PROPOSED`: reported by the critic, not decided yet.
- `IN REVIEW`: accepted in chat, built and merged, not yet approved in the emulator. Its `05` entry is marked pending.
- `ADOPTED`: approved by the user in the emulator (link: R-## or the `05` section).
- `REWORK`: seen in the emulator and not approved; the row records what was tried and what the user disliked.
- `REJECTED`: refused by the user (also copied to `05` §8 "Decided — do not re-propose").
- `DEFERRED`: postponed (link: E9 row or round).

A round that changes tokens or components does not start while the previous such round has rows `IN REVIEW`.

Round 1 (T-281, Diario) state: all rows C-21…C-43, V-5…V-13 and U-1 were accepted by the user on 2026-11-13 and are written into `05` §8 as PENDING (R-67…R-74). Rows marked "build waits for round 0 verdict" touch tokens or components and are not built until the round 0 rows are `ADOPTED` or `REWORK`. Placement double-check (C-21, C-22, C-39, C-42) and calorie graphic (C-23, C-29): agreed with conditions recorded in R-68…R-70.

Round 0 build state (T-295): all rows C-01…C-19 and V-1…V-4 are built on `feat/ui-design-system` and the PENDING tags are removed from `05-design-system.md`. Every row stays `IN REVIEW` until the user's emulator verdict sets `ADOPTED` or `REWORK`.
