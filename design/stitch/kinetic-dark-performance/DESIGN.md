---
name: Kinetic Dark Performance
colors:
  surface: '#101419'
  surface-dim: '#101419'
  surface-bright: '#36393f'
  surface-container-lowest: '#0a0e13'
  surface-container-low: '#181c21'
  surface-container: '#1c2025'
  surface-container-high: '#262a30'
  surface-container-highest: '#31353b'
  on-surface: '#e0e2ea'
  on-surface-variant: '#c2cab0'
  inverse-surface: '#e0e2ea'
  inverse-on-surface: '#2d3136'
  outline: '#8c947c'
  outline-variant: '#424936'
  surface-tint: '#98da27'
  primary: '#ccff80'
  on-primary: '#213600'
  primary-container: '#a3e635'
  on-primary-container: '#416400'
  inverse-primary: '#446900'
  secondary: '#7bd0ff'
  on-secondary: '#00354a'
  secondary-container: '#00a6e0'
  on-secondary-container: '#00374d'
  tertiary: '#ffeae2'
  on-tertiary: '#552100'
  tertiary-container: '#ffc6aa'
  on-tertiary-container: '#974000'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#b2f746'
  primary-fixed-dim: '#98da27'
  on-primary-fixed: '#121f00'
  on-primary-fixed-variant: '#334f00'
  secondary-fixed: '#c4e7ff'
  secondary-fixed-dim: '#7bd0ff'
  on-secondary-fixed: '#001e2c'
  on-secondary-fixed-variant: '#004c69'
  tertiary-fixed: '#ffdbca'
  tertiary-fixed-dim: '#ffb690'
  on-tertiary-fixed: '#341100'
  on-tertiary-fixed-variant: '#783200'
  background: '#101419'
  on-background: '#e0e2ea'
  surface-variant: '#31353b'
  neon-lime: '#A3E635'
  electric-green: '#22C55E'
  athletic-cyan: '#38BDF8'
  training-blue: '#0284C7'
  energy-amber: '#F97316'
  macro-purple: '#A855F7'
  surface-base: '#0B0F14'
  surface-raised: '#121820'
  surface-overlay: '#1A222D'
  surface-border: rgba(255, 255, 255, 0.08)
  ghost-data: rgba(255, 255, 255, 0.36)
typography:
  display-hero:
    fontFamily: Plus Jakarta Sans
    fontSize: 48px
    fontWeight: '800'
    lineHeight: 52px
    letterSpacing: -0.03em
  display-hero-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 36px
    fontWeight: '800'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-metric:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-metric-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 30px
    letterSpacing: -0.01em
  title-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 26px
    letterSpacing: -0.01em
  title-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
    letterSpacing: 0em
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-numeric:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '700'
    lineHeight: 22px
    letterSpacing: 0.02em
  label-technical:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.06em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system targets athletes, strength trainees, and macro-tracking lifters who interact with their devices under harsh gym fluorescent lighting, with chalk-dusted hands, elevated heart rates, and limited attention spans. The aesthetic is built on high-precision performance engineering: deep, carbon-tinted dark surfaces paired with high-voltage chromatic accents that communicate state and metrics instantly.

The visual style synthesizes modern technical minimalism with athletic high-contrast utility:
- **True Dark Architecture:** Deep charcoal and pitch-black backgrounds minimize glare, save battery during prolonged screen-wake sessions, and make tactical metrics pop.
- **High-Voltage Data Accents:** Color is never purely decorative; each key hue signals a biological or athletic domain (neon lime for execution and success, athletic cyan for exertion and training volume, energetic amber for metabolic energy, and electric violet for glycogen/carbs).
- **One-Handed Ergonomics:** Core touch targets are weighted toward the lower half of the viewport, with generous hit targets (minimum 48x48dp) and non-blocking bottom sheets replacing modal popups.
- **Tactile Precision:** Crisp micro-borders, dense modular surfaces, and large tabular numerals convey the feel of precision telemetry instruments.

## Colors

The palette establishes immediate functional clarity through high-contrast visual hierarchy on dark foundations:

- **Primary (`#A3E635` / Neon Lime):** High-efficiency primary action color used for primary CTAs, completion checkmarks, active set completion, and progress milestones. Visible even at extreme screen angles.
- **Secondary (`#38BDF8` / Athletic Cyan):** Assigned to workout tracking, sets, reps, estimated 1RM curves, and strength telemetry.
- **Tertiary (`#F97316` / Energy Amber):** Reserved for caloric burn, active energy targets, and warning thresholds.
- **Named Functional Colors:**
  - `macro-purple` (`#A855F7`): Exclusively represents carbohydrates across food diaries and nutrition breakdowns.
  - `electric-green` (`#22C55E`): Used alongside lime for protein breakdown and confirmed nutritional targets.
  - `surface-base` (`#0B0F14`): The foundational canvas layer.
  - `surface-raised` (`#121820`): First card elevation tier.
  - `surface-overlay` (`#1A222D`): Highest interactive elevation tier (bottom sheets, active set cards, FABs).
  - `surface-border` (`rgba(255, 255, 255, 0.08)`): Micro-stroke separating stacked dark modules.
  - `ghost-data` (`rgba(255, 255, 255, 0.36)`): Secondary placeholder metric displaying historical workout records.

## Typography

The type system prioritizes swift legibility from an arm's-length distance. **Plus Jakarta Sans** provides geometric clarity with wide open counters, ensuring numerals and weights remain unmistakable when glancing at a phone mounted on a bench or barbell rack.

- **Tabular Numerics:** All numeric telemetry (kilograms, reps, calorie counts, timers) must use `font-feature-settings: "tnum" on, "lnum" on` to prevent layout shift during countdowns and progressive inputs.
- **Metric Scaling:** `display-hero` is dedicated to key daily metrics (e.g., remaining calories, current body weight). `headline-metric` handles card-level values.
- **Technical Subheads:** `label-technical` is rendered in uppercase with wide letter spacing for unit labels (KG, REPS, KCAL, PROT, CARBS, FATS).

## Layout & Spacing

The layout is built for fluid touch ergonomics, structured along an 8pt spatial grid with a 4pt sub-grid for tight data tables.

- **Mobile Canvas:** Default canvas padding is `margin: 1rem` (16px), allowing maximum horizontal data density for multi-column set tables and macro progress charts.
- **One-Handed Zone:** Primary controls, action triggers, and input fields inhabit the lower 60% of the screen. Critical destructive actions or deep settings sit out of reach in the top app bar.
- **Section Spacing:** Cards maintain `space-md` (12px) separation within groups, and `space-xl` (24px) between major functional domains (e.g., between the Macro Ring summary and meal logging sections).
- **Responsive Adaptability:** On tablet and wide views (768px+), the interface moves from a single vertical feed into a 2-column asymmetric layout (Telemetry & Ring on left, Exercise Table / Log feed on right) with a fixed 12-column grid and 24px gutters.

## Elevation & Depth

Rather than relying on heavy drop shadows that get washed out or create muddy borders on deep black screens, this design system creates depth through **tonal surface stacking** combined with **luminous micro-borders**:

- **Ground Layer (`#0B0F14`):** Canvas bedrock. Houses fixed headers and foundational negative space.
- **Tier 1 Container (`#121820`):** Modular exercise cards, meal groups, and trend summaries. Encapsulated with a 1px border of `rgba(255, 255, 255, 0.08)`.
- **Tier 2 Interactive (`#1A222D`):** Active sets being edited, bottom sheets, filter chips, and floating telemetry bars. Edges feature a 1px border of `rgba(255, 255, 255, 0.12)`.
- **Active State Glow:** When an element is actively focused or running (e.g., an active rest timer bar or an active live set), a subtle ambient glow is projected using the accent color: `0 0 16px -2px rgba(163, 230, 53, 0.15)`.
- **Floating Modals & Sheets:** Elevated bottom sheets cast a soft, deep ambient occlusion shadow: `0 12px 32px -4px rgba(0, 0, 0, 0.7)`.

## Shapes

The design system uses a controlled rounded corner scale (`roundedness: 2`) that balances modern friendliness with high-performance rigidity:

- **Standard Cards & Modals:** Use `rounded-lg` (16px) to create well-defined, friendly modules that hold structured metrics.
- **Bottom Sheets & Root Wrappers:** Use `rounded-xl` (24px) on top radii to establish distinct interactive overlays.
- **Inputs & Row Containers:** Use standard base rounding (8px) for quick visual scanning within repetitive tabular set lists.
- **Pills & Status Indicators:** Use full circular rounding (`9999px`) for badges, filter chips, quick macro pills, and circular action checkboxes.

## Components

### Buttons
- **Primary Action (FAB & Main CTA):** Pill-shaped or 14px rounded button filled with `neon-lime` (`#A3E635`) and high-contrast dark text (`#0B0F14`). Minimum touch height: 52px. Active press scale effect: 0.97.
- **Secondary Action:** Outlined with `surface-border`, background filled with `surface-overlay`, white text.
- **Ghost Action (+30s / Skip Rest):** Transparent background with `athletic-cyan` or white borders; quick haptic feedback on touch.

### Modular Cards
- Structural base in `surface-raised` with a 16px border radius and 1px `surface-border`.
- Padded with 16px internal padding. Multi-set exercise cards contain internal row dividers colored with `rgba(255, 255, 255, 0.04)`.

### Set Rows & Live Workout Table
- Row layout: Set index (tabular label), historical "ghost data" (previous weight/reps in `ghost-data`), current inputs, and completion trigger.
- **Set Completion Control:** Large circular checkbox (38x38px hit box). Default: 2px border of `rgba(255, 255, 255, 0.24)`. Checked: animated fill of `neon-lime` with a dark check icon and haptic confirmation.

### Chips & Filters
- Compact horizontal scroll elements (e.g., timeframes 1S, 1M, 3M, 1A). Height: 32px.
- Inactive: `surface-raised` background with muted text. Active: `surface-overlay` with a 1px solid `athletic-cyan` border and active cyan text.

### Inputs & Number Steppers
- Background of `surface-overlay` with tabular, centered numerals.
- Focus state: Outline switches from subtle border to 1.5px solid `athletic-cyan` or `neon-lime` without layout shifting.

### Bottom Sheets (Contextual Panels)
- Anchored to the viewport bottom with `rounded-xl` top corners and dark backdrop scrim (`rgba(0,0,0,0.65)` with backdrop-filter blur of 8px).
- Features a top drag handle pill (36x4px, `rgba(255,255,255,0.2)`).

### Rest Timer Bar
- Persistent bottom or top pinned bar. Height: 48px.
- Background: `surface-overlay` with an integrated lime/cyan countdown progress line along the bottom border and high-contrast tabular countdown text.