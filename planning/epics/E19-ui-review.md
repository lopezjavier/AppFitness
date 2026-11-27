# E19 · UI review

> **Status: DEFINED (product-owner, updated 2026-11-11, ADR-103), not planned.** Stories US-131 and US-123. The `epic-planner` breaks it into tasks. It runs after E18 so that every screen, including the ones E18 adds, is reviewed once. US-120…US-122 moved to E18.

## Intent

**Problem.** Reviewing a UI change today means building and installing the APK each time, and only about 17 design-system components have images (Roborazzi goldens). Nobody has reviewed the whole app together, so the same style feedback gets repeated screen by screen.

**Outcome.**
- Every screen and state can be seen on the computer, with a dummy user and dummy data, without touching the phone.
- All of the app's UI has been reviewed with the user.
- Each decision is recorded once as a design-system change or UX rule and applied everywhere, so the user never has to say it twice.

**Non-goals.** Changing behaviour or adding features; a second UI-automation harness that needs debugging (`CLAUDE.md`, ADR-27); rebuilding screens from scratch: each change goes through the design system and is swept across every screen that uses the pattern.

**Approach chosen and why.** Tools first, then the review in rounds.
1. Build the tools: a screen gallery (Roborazzi, already in the repo, on the Robolectric host tests with fake data; no device, no backend) and a rich emulator dataset (local flavor, Docker Supabase, the seeded test user). The tool-choice spike was dropped: Roborazzi exists and Maestro conflicts with ADR-27. `@Preview` needs fake data per screen and is not used.
2. Review in rounds by area, with early feedback and no 👤 checkpoint gates (there is no new functionality to test; the user reviews images or the emulator by themselves).
3. Every accepted change is written to `05-design-system.md` §8-§10 first and applied to every screen with the pattern in the same pass.

## Rules
`05` §8-§10; `04`; ADR-27, ADR-80, ADR-103.

## US-131 · See every screen without installing the APK
**As** the user reviewing the app
**I want** to see every screen without building and installing it
**So that** a review is fast and complete.

```gherkin
Feature: Review tooling

  Scenario: One command renders every screen
    When I run the gallery command
    Then I get one image per screen and state, in all of the app's UI
    And no device, no backend, no real account and no secret is needed

  Scenario: No screen is missing
    Given a screen exists in the navigation
    When it has no gallery entry
    Then the gallery check fails and names it

  Scenario: States are shown, not only the happy path
    Then each screen appears with data, empty, loading, and error states where they exist, and its sheets and dialogs

  Scenario: Images are easy to browse
    Then the images are grouped by area and opened from one index page

  Scenario: A rich dummy user for real flows
    Given the local flavor on the emulator
    When I log in with the test login
    Then the user has weeks of diary and weight history, a training block with finished sessions, a trainer plan, and reports and notices to open
    And the dataset is local only and holds no personal data

  Scenario: One command starts the live flow
    Then one command starts the local backend with the dummy data and installs the app on the emulator
```

## US-137 · See each exercise's picture and pick a lift in Fuerza (T-301)
**As** the user
**I want** every exercise listed or headed by name to show its picture, and Fuerza to list the lifts as rows
**So that** I recognise an exercise at a glance.

```gherkin
Feature: Exercise pictures

  Scenario: The live session header shows the picture
    Given a catalogue exercise in the live session
    Then its header shows the exercise picture at 32 dp, not a dumbbell badge
    And a custom exercise shows the placeholder

  Scenario: A finished exercise is marked by a check and text
    Given every set of an exercise is done
    Then its header shows a check described "Ejercicio completado"

  Scenario: Estadísticas > Entreno and the muscle sheet show pictures
    Then each exercise row shows its picture

  Scenario: The workout form and the session detail show pictures
    Then each exercise card in the new, edit and past workout form, and in the session detail, shows its picture

  Scenario: Fuerza lists one row per exercise
    Then each lift is a row with its picture and the chosen one has a lime check
    When I select another row
    Then "Récord 1RM estimado" shows that lift
    And the selection survives leaving the app
```

## US-138 · Preferencias keeps the plan's calories (T-303)
**As** the user
**I want** saving a Preferencias section never to change my calories or macros, and numbers with a unit never to overlap it
**So that** the Trainer plan's target stays the one I accepted and every field is readable.

```gherkin
Feature: Preferencias and the plan's calories

  Scenario: Saving a section leaves the goals untouched
    Given an accepted plan with a calorie target
    When I save "Cocina" in Preferencias
    Then the stored calorie target and macros do not change
    And the plan is not marked as edited by me

  Scenario: Nutrición lists no calories or macros
    When I open Preferencias
    Then Nutrición lists "Comidas del día", "Comidas por defecto" and "Cocina" only

  Scenario: An old saved draft still opens
    Given a saved draft that has energy or macros keys
    When I come back to Preferencias
    Then it opens without error and the other values are restored

  Scenario: A unit never overlaps its number
    Given a field with a unit such as "kcal", "g" or "min"
    When the value is up to four digits with a decimal, at any font size
    Then the unit is drawn beside the number and never over it
```

## US-140 · The trainer owns the activity level (T-308)
**As** the user
**I want** my activity level measured from my steps or watch when they exist, and never to block my plan when it changes nothing
**So that** I only answer what the app cannot measure (ADR-109, `07` §2, `04` §7).

```gherkin
Feature: Activity level owned by the trainer

  Scenario: Measured level is read-only
    Given 7 or more days of steps in the last 28 days
    When I open "Tus datos"
    Then "Nivel de actividad" shows the level and "Según tus pasos: 8.200 al día" with no chip and no sheet

  Scenario: Watch calories as basis
    Given no steps but 7 or more days of active kcal
    Then the row says "Según tu reloj: N kcal activas al día" and is read-only

  Scenario: Without data it is my answer
    Given fewer than 7 days of steps and of active kcal
    Then the row is editable, has the "Provisional" chip and says "Según lo que nos contaste"

  Scenario: The level does not block Aceptar when calories come from maintenance or measured data
    Given a provisional level and the expenditure source is my stated maintenance or the adaptive one
    Then "Aceptar" is enabled and "Valores por confirmar" does not list "Nivel de actividad"
    And the activity row keeps its "Provisional" chip (it is still my answer; the chip does not block)
    And a "?" on the row (Plan and Antes de empezar) opens a sheet "Nivel de actividad" saying it does not change my calories

  Scenario: The level blocks on the formula path
    Given a provisional level and the expenditure is BMR x factor
    Then "Valores por confirmar" lists "Nivel de actividad" and "Aceptar" is disabled

  Scenario: Provisional ends with data
    Given the weight trend is used in the expenditure (20 diary days, 4 weekly averages, 8 weigh-in days)
    Then "Calorías de mantenimiento" has no chip and does not block; with a diary alone it keeps "Provisional"
    And Experiencia and Tiempo entrenando lose the chip together when my history reaches the class I reported

  Scenario: Preferencias explains the answer
    When I open the "Nivel de actividad" picker in Preferencias
    Then its header has a "?" that says it is used only without step or watch data

  Scenario: Leaving the app
    Given the activity sheet is open on a level I picked but not saved
    When I leave the app and come back, or data makes the level measured
    Then the sheet is restored, or closed silently with no stale draft if the level became measured
```

## US-141 · The weekly report shows my progress (T-313, T-314, T-315)
**As** the user
**I want** the Trainer's weekly report ("Resumen semanal", live and in "Informes") to show my progress, not compliance counts
**So that** each week I see what moved toward my goal (ADR-110, `07` §3 "Weekly report: progress first").

```gherkin
Feature: Progress-first weekly report

  Scenario: Coach card on top
    When I open a weekly report
    Then the first card shows one progress sentence and one action, the first that applies: a pending calorie proposal, then "¿Por qué no avanza tu peso?", then a stall, then missing data, then a deload, else "Sigue con el plan"
    And on a past week a decided proposal reads "Propuesta −150 kcal · aplicada"

  Scenario: Strength per muscle group
    Then each muscle group row shows one state icon per exercise and "2 de 3 suben"
    When I tap a group
    Then it lists each exercise as "80 kg × 8 → 82,5 kg × 6"
    And a context line reads "3 de 4 entrenos · 18 de 20 series"
    And in week 1 it reads "Semana 1: esta es tu base. Desde la semana 2 verás qué sube."

  Scenario: Paused exercise with a health note
    Given an unresolved level 4-5 discomfort whose note is due and not dismissed
    When I open the latest week's report
    Then the exercise's row shows the paused state and the health note text of 07 §7
    And the paused state replaces Igual or Baja but not Sube
    And a past report shows no note

  Scenario: Main lifts
    Then up to 6 main lifts show a sparkline
    And each reads "Sube +5 kg (+5 %)", "dentro del margen de un día a otro", "Estancado" or "provisional" (fewer than 3 weeks)
    And deload weeks are drawn hollow

  Scenario: Records
    Then at most 3 record badges show the amount, main lifts first
    And with no record there is no records card

  Scenario: Weight
    Then the weight card shows the plan-week average
    And the change against last week only with 3 or more weigh-ins in both weeks, reading "igual que la semana pasada" when it rounds to 0
    And the change since the phase started, a sparkline with the dashed plan line ending at "Meta {x} kg · {fecha}", and the "Tu plan" verdict
    And below the gates it reads "Faltan pesajes para ver el cambio" or "Pésate 3 días esta semana"

  Scenario: Waist
    Given a week with a new waist measurement
    Then the waist card shows the change, reading "dentro del margen de medida" under 3,0 cm
    And in a week without one there is no waist card

  Scenario: Order by goal
    Then with "Perder grasa" Weight comes first
    And with "Ganar fuerza" or "Ganar musculatura" Strength comes first
    And with "Mantenimiento" or "Recomposición" the order is Strength, Waist, Weight

  Scenario: No compliance cards
    Then there is no "Series por grupo", sessions ring, "Días" strip or nutrition card

  Scenario: Past weeks remade
    Given a report stored before the change
    When I open it in "Informes"
    Then it shows the new cards rebuilt as of that week's end, with its original decisions
    And a missing input hides its line and says what is missing

  Scenario: Late data
    Given a weigh-in synced after the week ended
    Then it counts in the latest finished week until the next week ends

  Scenario: Leaving the app
    Given the group sheet is open
    When I leave the app and come back
    Then the screen and the sheet are restored
```

## US-142 · No weekly check-in (T-311, T-312)
**As** the user
**I want** the weekly check-in gone from every place, and the measures reminder to work with Health Connect weigh-ins
**So that** nothing asks me for a check-in the app never showed, and no reminder depends on it (ADR-110, amends ADR-73).

```gherkin
Feature: No weekly check-in

  Scenario: No check-in anywhere
    When I open "Centro de avisos", Preferencias > Avisos or follow an old link
    Then there is no "Check-in semanal" item, switch, "Día del check-in" row or screen

  Scenario: Old saved state
    Given a saved Preferencias draft or navigation stack from before
    When I come back
    Then it opens without error (the check-in route falls back to the Trainer root or is discarded)

  Scenario: Circumference reminder without a manual weigh-in
    Given only Health Connect weigh-ins and no circumference
    Then 14 days after my first weigh-in "Llevas 2 semanas sin medirte" is listed
```

## US-143 · The block review shows my progress (T-317, T-318)
**As** the user
**I want** "Revisión del bloque" (live and in "Informes") to show what moved over the block
**So that** at the block end I see progress toward my goal, not attendance counts (ADR-111, `07` §3 "Other reports: progress first" (A)).

```gherkin
Feature: Progress-first block review

  Scenario: Coach card on top
    When I open a block review
    Then the first card shows one sentence such as "En este bloque: 5 ejercicios suben, press banca +5 kg (+6 %), peso −1,8 kg." and one action, the first that applies: a pending phase proposal, a pending calorie proposal, a stall, missing data, else "Crea el siguiente bloque"
    And on a stored review a decided proposal reads as text ("Fase aceptada: Mantenimiento"), never a button

  Scenario: Strength per muscle group over the block
    Then each muscle group shows one state icon per exercise and "12 de 15 suben"
    And a context line reads "14 de 16 entrenos · 170 de 180 series"
    And there is no "Entrenos" or "Series por grupo" card

  Scenario: Main lifts and records of the block
    Then main lifts show a sparkline with deload weeks hollow and read "Sube +5 kg (+5 %)" only from 4 %, else "dentro del margen de un día a otro"
    And there is no "Récord" badge for the best week of the block
    And at most 3 record badges show, with "N récords en el bloque" when there are more; with none there is no records card

  Scenario: Weight over the block
    Given 3 or more weigh-in days in the first and in the last week of the block
    Then the weight card shows the change, a sparkline with the dashed plan line to "Meta {x} kg · {fecha}" and the "Tu plan" verdict
    And otherwise it reads "Faltan pesajes para ver el cambio del bloque"
    And with no weigh-in at all there is no weight card and the coach card says "Pésate 3 días por semana"

  Scenario: Objectives show the gap, the chip only when true
    Then each objective reads "Peso: objetivo 78 kg · ahora 79,8 kg"
    And "Conseguido" shows when reached
    And "No conseguido" shows only after the objective's deadline

  Scenario: Order by goal
    Then with "Perder grasa" Weight comes first and with "Ganar fuerza" or "Ganar musculatura" Strength comes first
    And Objectives come after the progress cards and the stall, phase and calorie cards last

  Scenario: A stored review keeps its decisions
    Given a review stored at the block end
    When I open it later in "Informes"
    Then it shows the progress built at the block end and its original phase decision

  Scenario: Leaving the app
    Given the group sheet of the block review is open
    When I leave the app and come back
    Then the screen and the sheet are restored
```

## US-144 · "Informes" shows progress per report (T-319)
**As** the user
**I want** each row of "Informes" to say what moved
**So that** I scan my history without opening every report (`07` §3 (B)).

```gherkin
Feature: Informes list

  Scenario: Teaser per row
    When I open "Informes y resumen semanal" > "Informes"
    Then a week row reads "Semana 3" with its dates and "3 suben · −0,3 kg"
    And a block row reads "Revisión del bloque" with its dates and "12 de 15 suben · −1,8 kg"
    And a paused week reads "Semana en pausa"

  Scenario: Only gated figures
    Then a teaser shows a weight change only when the report's own weight gate passed
    And a report stored without progress figures shows only its dates

  Scenario: Grouped by block
    Then weeks sit under their block's heading, the block review first, newest block first

  Scenario: Leaving the app
    Given I scrolled the list
    When I leave the app and come back
    Then the list is at the same place
```

## US-145 · Estadísticas shows my progress over a period (T-320, T-321)
**As** the user
**I want** "Entrenos" in Estadísticas to start with what got stronger in the range, and the muscle-group view to show sets
**So that** the screen answers "am I improving?" (`07` §3 (D)).

```gherkin
Feature: Progress-first training statistics

  Scenario: Progress of the period on top
    When I open Estadísticas > Entrenos with range 3M
    Then the first card reads "8 de 11 ejercicios suben", main lifts first
    And each exercise with 2 or more sessions shows "Sube +6 %", "dentro del margen de un día a otro" or "Baja"
    And an exercise with one session reads "Sin comparar"
    When I tap an exercise
    Then "Progreso del ejercicio" opens

  Scenario: Records of the period
    Then at most 3 record badges show with the amount and "N récords" when there are more; with none there is no card

  Scenario: Effort as one context line
    Then one line reads "24 entrenos · 412 series"
    And there are no "Volumen" or "Duración" tiles

  Scenario: Principal exercises show their change
    Then each "Ejercicios principales" row reads "+5 kg · 6 entrenos"

  Scenario: Distribution kept below
    Then the sets chart, sets per muscle group, the radar and the body map follow the progress cards

  Scenario: Sets per muscle group by week
    When I open "Series por grupo muscular" from Entrenos (formerly "Kilos por grupo muscular")
    Then the last 8 weeks show weekly sets per group, not kilos

  Scenario: Leaving the app
    Given I chose range 6M and opened an exercise
    When I leave the app and come back
    Then the range and the screen are restored
```

## US-146 · Calorías, Fuerza and the Perfil highlights are honest (T-322)
**As** the user
**I want** averages that ignore forgotten days and today, and highlights that name what they show
**So that** the figures are not dragged down by a half-logged day (`07` §3 (C), (E), (G)).

```gherkin
Feature: Calorías, Fuerza and Perfil highlights

  Scenario: Complete days only
    Given 7 days of which 2 are under 40 % of the median of the logged days
    When I open Perfil > Estadísticas > Calorías > Semana
    Then the average reads "Media de 5 días completos"
    And with no complete day it reads "Sin días completos"

  Scenario: Today is not in the week
    Then the week is the 7 days before today
    And today's bar is drawn but not counted until the day ends

  Scenario: One bar colour
    Then every bar has one colour and the target line stays; no bar is amber

  Scenario: Fuerza change inside the margin
    Given an exercise whose 1RM changed less than 4 % in 30 days
    Then Fuerza reads "+1,5 kg este mes · dentro del margen de un día a otro"

  Scenario: Perfil highlights
    Then the strength highlight reads "Press banca · 92,5 kg"
    And the kcal highlight is the complete-day week average of Calorías

  Scenario: Leaving the app
    Given Calorías is open on Mes
    When I leave the app and come back
    Then the same panel is restored
```

## US-147 · "Entreno completado" shows what changed since last time (T-323)
**As** the user
**I want** the end-of-workout summary to compare each exercise with the last time
**So that** I see right away whether I progressed (`07` §3 (F)).

```gherkin
Feature: Workout summary against the last time

  Scenario: Frente a la última vez
    When I finish a workout
    Then each exercise reads "80 kg × 8 → 82,5 kg × 6" with its state: Sube, Igual ("Mantienes" in a cut), Baja or "Sin comparar"
    And an exercise with an open discomfort shows the paused state, with no note

  Scenario: Sin comparar
    Given the exercise changed its rep range or has no session in the previous 14 days
    Then it reads "Sin comparar"

  Scenario: Records and context
    Then the records badge and pills stay
    And duration and sets are one line
    And there is no per-exercise volume bar

  Scenario: Leaving the app
    Given the summary is open
    When I leave the app and come back
    Then the summary is shown again until I tap "Listo"
```

## US-148 · Circunferencias show my progress per site (T-324)
**As** the user
**I want** each body site on Peso to show its latest value, its change and a small chart
**So that** I see my progress without opening every site (`07` §5 "Circumferences on Peso").

```gherkin
Feature: Circumference progress per site

  Scenario: One entry per site
    Given measurements of Cintura, Cadera and Pecho on several days
    When I open Perfil > Peso
    Then "Circunferencias" shows one row per site, in site order, with its latest value ("84,5 cm"), a chart of its history and its change
    And no site repeats its name per date

  Scenario: Change over 28 days and the margin
    Given Cintura 86,5 cm on 22 sep and 84,5 cm today
    Then the Cintura row reads "−2,0 cm" "desde el 22 sep" "dentro del margen de medida"
    And a Cadera change of −2,0 cm shows no margin note (its margin is 2,0 cm)
    And a Pecho change shows the change with no margin note (no margin)

  Scenario: No comparable measurement
    Given a site with one measurement, or none at least 28 days older than the latest
    Then its row shows the date of the latest measurement and no change
    And with one measurement there is no chart

  Scenario: No measurement at all
    Then the section shows "Aún no hay circunferencias" and "Añadir circunferencia"

  Scenario: A site's full history
    When I tap the Cintura row ("Ver historial")
    Then the "Cintura" screen shows the latest value, the chart and "Historial" with every dated value, newest first
    When I tap a value, change it and tap "Guardar"
    Then the history, the chart and the change are recalculated
    When I delete a value (swipe, or "Eliminar" in its sheet)
    Then it is removed at once, with no confirmation and no "Deshacer" (ADR-45)
    And deleting the site's last value returns to Peso

  Scenario: Adding updates the entry
    When I add a Cintura value with "Añadir circunferencia"
    Then the Cintura row's value, change and chart update

  Scenario: Leaving the app
    Given the Cintura screen is open (also with its edit sheet half filled)
    When I leave the app and come back
    Then the same screen (and the same draft) is shown
```

## US-123 · Review of all the UI
**As** the user
**I want** all the UI reviewed
**So that** frictions are removed once and for all, through the design system.

```gherkin
Feature: Screen review

  Scenario: Reviewed in rounds
    Given the gallery exists
    When ux-reviewer and I review an area (Diario, Entreno, Trainer, Perfil, and every flow, sheet and dialog reachable from them)
    Then I receive a findings list: screen, problem, proposed change, and the component or rule it affects
    And I accept, reject or change each item in chat before any task is built

  Scenario: Every change goes through the design system
    Given I accept a finding
    Then the component or the visual decision is changed in 05-design-system.md first
    And the change is applied to every screen that uses the pattern in the same pass

  Scenario: A decision becomes a rule
    When a finding states how something should look or work everywhere
    Then it is written as a UX rule in 05 §8-§10 and, when it can be checked, as a check in check-ux-conventions.sh

  Scenario: Early feedback, no gates
    When a fix batch is done
    Then I see the updated images right away, and no 👤 checkpoint blocks the next round

  Scenario: Behaviour does not change
    Then no change alters what the app does, only how it looks or is arranged
    And the existing host tests stay green
```
