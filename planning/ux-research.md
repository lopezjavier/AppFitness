# UX research notes

Sources the `ux-critic` already opened, with the takeaway used. Read this before searching the web; search only for topics not covered here and append the new sources (the PM saves them from the critic's "Research notes"). Round 0 (T-288, 2026-11-12) is the first entry.

## Fetching tips
- `m3.material.io` pages return only the page title through WebFetch. Use the androidx token files or the Material Components Android docs.
- Token files: `https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/<Name>.kt`.
- `TopAppBarSmallTokens.kt` is a 404; the file is `AppBarSmallTokens.kt`.
- Material Components Android `Button.md` is a landing page with no values.

## Colour
- **col-1** [ColorDarkTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ColorDarkTokens.kt): M3 dark role-to-tone map. Surface N6; containers lowest N4, low N10, container N12, high N17, highest N22; OnSurface N90; OnSurfaceVariant NV80; Outline NV60; OutlineVariant NV30; SecondaryContainer S30.
- **col-2** [WCAG 1.4.3 Contrast (Minimum)](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html): text 4.5:1, large text 3:1; inactive components exempt; applies to placeholder text.
- **col-3** [WCAG 1.4.11 Non-text Contrast](https://www.w3.org/WAI/WCAG21/Understanding/non-text-contrast.html): 3:1 for what identifies a component and its states; a text input's indicator must meet 3:1; inactive components exempt.
- **col-4** [Carbon data-visualization colour palettes](https://carbondesignsystem.com/data-visualization/color-palettes/): monochromatic palettes for ordered data; alert colours reflect status.

## Typography
- **typ-1** [TypeScaleTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/TypeScaleTokens.kt): M3 scale size/line. Display 57/64, 45/52, 36/44; Headline 32/40, 28/36, 24/32; Title 22/28, 16/24, 14/20; Body 16/24, 14/20, 12/16; Label 14/20, 12/16, 11/16.
- **typ-2** [AppBarSmallTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/AppBarSmallTokens.kt): small app bar 64 dp, title TitleLarge.
- **typ-3** [NavigationBarTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/NavigationBarTokens.kt): bar 64 dp, label LabelMedium, active indicator SecondaryContainer, 4 dp indicator-to-label space.

## Spacing, shape and size
- **siz-1** [ShapeTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ShapeTokens.kt): corners 4, 8, 12, 16, 20, 28, 32, 48, full.
- **siz-2** [ButtonMediumTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ButtonMediumTokens.kt): Expressive medium button 56 dp, icon 24 dp, icon-label space 8 dp, square shape = large (16 dp).
- **siz-3** [Android touch target size](https://support.google.com/accessibility/android/answer/7101858?hl=en): at least 48 dp wide and tall.

## Components
- **cmp-1** [FilledButtonTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FilledButtonTokens.kt): container Primary; disabled container OnSurface 0.10, label 0.38.
- **cmp-2** [FilledTonalButtonTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FilledTonalButtonTokens.kt): container SecondaryContainer; disabled OnSurface 0.12 / 0.38.
- **cmp-3** [FilterChipTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FilterChipTokens.kt): height 32 dp, selected SecondaryContainer, unselected outline OutlineVariant 1 dp.
- **cmp-4** [OutlinedSegmentedButtonTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/OutlinedSegmentedButtonTokens.kt): height 40 dp, selected SecondaryContainer, outline Outline 1 dp.
- **cmp-5** [OutlinedTextFieldTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/OutlinedTextFieldTokens.kt): outline Outline 1 dp, focus Primary 2 dp; placeholder OnSurfaceVariant; height 56 dp.
- **cmp-6** [FilledTextFieldTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FilledTextFieldTokens.kt): container SurfaceContainerHighest.
- **cmp-7** [ListTokens.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ListTokens.kt): headline BodyLarge, supporting BodyMedium, trailing LabelSmall; item heights 56, 72, 88 dp.
- **cmp-8** [Material Components Tabs.md](https://raw.githubusercontent.com/material-components/material-components-android/master/docs/components/Tabs.md): fixed tabs have a fixed width; scrollable tabs exist; label TitleSmall; max tab width 264 dp. Nothing on long labels.
- **cmp-9** [Material Components ProgressIndicator.md](https://raw.githubusercontent.com/material-components/material-components-android/master/docs/components/ProgressIndicator.md): track 4 dp; 4 dp stop indicator and gap in the non-text contrast update.
- **cmp-10** [Material Components ToggleButtonGroup.md](https://raw.githubusercontent.com/material-components/material-components-android/master/docs/components/ToggleButtonGroup.md): a group shares a common container; no width or scroll rule.
- **cmp-11** [Carbon empty states pattern](https://carbondesignsystem.com/patterns/empty-states-pattern/): the image should relate to the situation; if space is limited, text only.

## Fitness-app patterns
- **fit-1** [Hevy: track workouts](https://www.hevyapp.com/features/track-workouts/): PREVIOUS column shows last performance at a glance; "+ Add Set" under each exercise; completing a set starts the rest timer.
- **fit-2** [Hevy: workout rest timer](https://www.hevyapp.com/features/workout-rest-timer/): timer near the top of the exercise with -15/+15; no size information.

## Contrast figures computed in round 0 (WCAG formula, by hand; re-check before use)
| Pair | Ratio |
|---|---|
| `surfaceRaised` on `surfaceBase` | 1.08:1 |
| `surfaceOverlay` on `surfaceRaised` | 1.11:1 |
| `textPrimary #E0E2EA` on base | 14.9:1 |
| `textSecondary #C2CAB0` on base / overlay | 11.3 / 9.4 |
| `ghostData` (white 36 %) on base, raised, overlay | about 3.3:1 |
| `neonLime` on base | 12.7:1 |
| Input border (white 12 %) on `surfaceOverlay` fill | 1.46:1 |
| Proposed `#A9B3C4` on base / raised / overlay | 9.1 / 8.4 / 7.6 |
| Proposed `#8C97A9` on base / raised / overlay | 6.5 / 6.0 / 5.4 |

## Round 1 (Diario), new sources

| URL | Takeaway | Supports |
|---|---|---|
| androidx `SearchBarTokens.kt` | Search bar 56 dp, leading/trailing icons, no label token | C-21 |
| androidx `DatePickerModalTokens.kt` | Unselected date has no container; today 1 dp outline | C-33 |
| androidx `OutlinedTextFieldTokens.kt` | Caret = Primary, error caret = Error | C-41 |
| androidx `PrimaryNavigationTabTokens.kt` | Active indicator 3 dp Primary | C-09 evidence |
| androidx `ListTokens.kt` | Leading icon 24 dp OnSurfaceVariant; 16 dp leading/trailing space | C-28, C-32 |
| unicode.org CLDR plural rules | Spanish: one, many, other; 0 falls in other | C-31 |
| nngroup.com/articles/skeleton-screens/ | Skeleton structure mimics the final page | R-31 violations |
| developer.android.com/develop/ui/compose/lists | Content padding applies to content, not the list | C-37 |
| carbondesignsystem.com/data-visualization/color-palettes/ | Orange = serious warning | C-25 |
| ssw.com.au/rules/destructive-button-ui-ux | Keep delete away from Save, less prominent | C-22 |
| material-components-android `Dialog.md` | Optional divider between content and buttons | C-27 |

### Batch 5 (Diario meal card polish), critic's notes

- C-60: reusing the plain rows' slots (headline plus trailing, `ListTokens` 16 dp edge space) keeps name and kcal on one text edge; the overlay box moves to 8 dp so box content (8 dp inner) lands on the same edge as plain rows.
- C-61 to C-65: a collapsed card is a header only, so 16 dp of bottom padding reads as dead space; 4 dp tight spacing plus a tertiary-coloured separator keeps the macros subordinate to the name. A hairline appears only where content groups need separating (expanded with entries).
- C-62: letter/figure weight and colour contrast (W700 primary letter, W600 macro-coloured figure) aids scanning; the dot is glued to the preceding item so line wraps never start with a separator.
- V-15 follow-up: a fit check against a fixed 132 dp is wrong when the status pill is wider; measure the real column.
- 360 dp risk: name + pill + two 48 dp targets need about 310 dp against 296 dp available; since mid-word breaks are forbidden, the pill drops to the macros line and the buttons stay beside the name (touch targets never shrink below 48 dp).
