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
| C-09 | tabs shared 12 sp label, scroll (min 72 dp) only if a word does not fit | 0 | IN REVIEW | 05 §9 (amended), §6.0 |
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

Status values:
- `PROPOSED`: reported by the critic, not decided yet.
- `IN REVIEW`: accepted in chat, built and merged, not yet approved in the emulator. Its `05` entry is marked pending.
- `ADOPTED`: approved by the user in the emulator (link: R-## or the `05` section).
- `REWORK`: seen in the emulator and not approved; the row records what was tried and what the user disliked.
- `REJECTED`: refused by the user (also copied to `05` §8 "Decided — do not re-propose").
- `DEFERRED`: postponed (link: E9 row or round).

A round that changes tokens or components does not start while the previous such round has rows `IN REVIEW`.

Round 0 build state (T-295): all rows C-01…C-19 and V-1…V-4 are built on `feat/ui-design-system` and the PENDING tags are removed from `05-design-system.md`. Every row stays `IN REVIEW` until the user's emulator verdict sets `ADOPTED` or `REWORK`.
