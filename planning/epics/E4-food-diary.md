# E4 · Food diary → `v0.4.0`

## Goal
Log what you eat in fewer than 3 taps (search, scanner or saved meal) and see the calories and macros consumed against the target at all times.

## Scope
- Includes: food catalog with an Open Food Facts cache (base unit g or ml, named portions), text search, scanner, manual creation, favourites, daily diary with configurable meals, dashboard, meal templates, and diary history/summaries.
- Micronutrients are display-only from T-068 (ADR-43); no targets or totals.
- Out of scope: user-uploaded food images, micronutrient targets or totals, composite recipes, meal planning, data export.

## Dependencies
E2 (authenticated user and `nutrition_goals`). Inside the epic, the foundations come first: US-22 (portions/measures) and US-23 (meal structure) are consumed by US-17 and US-19; US-24 reuses the diary data of US-19.

## Tables and functions
`foods`, `food_portions`, `food_log_entries`, `meal_slots`, `meal_templates`, `meal_template_items`, `favorite_foods`; RPCs `most_eaten_foods` and `food_diary_summary`; Edge Functions `food-search` and `food-barcode` (`03-data-model.md` §3, §6 and §7).

## Reference screen
`design/stitch/diary/screen.png` (without the water card).

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language). Meals in the UI: Desayuno (breakfast), Comida (lunch), Merienda (snack), Cena (dinner).

---

## US-16 · Food catalog and Open Food Facts proxy
**As** the development team
**I want** our own food catalog filled on demand from Open Food Facts
**So that** searches are fast without exceeding OFF limits or depending on its format.

```gherkin
Feature: Food catalog with an Open Food Facts cache

  Scenario: Search with cached results
    Given the foods table contains "Yogur griego natural" from OFF
    When an authenticated user calls food-search with q="yogur griego"
    Then the response includes that food with kcal and macros per 100 g
    And no request is made to Open Food Facts if there are 10 or more local results

  Scenario: Search that queries Open Food Facts
    Given the cache has fewer than 10 results for q="avena"
    When food-search is called
    Then the function queries OFF with the configured User-Agent
    And it stores the valid products in foods with source "off"
    And it returns at most 20 normalized results

  Scenario: Barcode lookup
    When food-barcode is called with code="8410000810004"
    Then it returns the normalized food (from the cache or OFF)

  Scenario: Base unit per food
    Given OFF reports the nutrition of "Coca-Cola Zero" per 100 ml
    When the product is cached
    Then the food is stored with base unit "ml" (not "g")
    And its kcal and macros are per 100 ml

  Scenario: Unknown code or missing nutrition data
    When food-barcode is called with a code OFF does not have, or without kcal
    Then the response is 404 with error.code "FOOD_NOT_FOUND"

  Scenario: Invalid input
    When food-search is called with a 1-character q or food-barcode with code="abc"
    Then the response is 400 with error.code "INVALID_INPUT"

  Scenario: Unauthenticated call
    When food-search is called without a JWT
    Then the response is 401

  Scenario: Personal food isolation
    Given user A created the personal food "Tortilla de mi madre"
    When user B searches for "tortilla"
    Then that food does not appear in their results
```

**Technical notes**
- Migration: `foods` (with `base_unit`, `search_vector` and GIN), `food_portions`, `food_log_entries`, RLS and pgTAP.
- `functions/_shared/off-client.ts` with normalization (kJ → kcal, base unit from `nutrition_data_per`: `100 g` vs `100 ml`) and `deno test` tests using recorded OFF responses (fixtures), no network in CI.
- Backend flow.

---

## US-17 · Food text search and logging into a meal
**As** a user
**I want** to search for a food by name and add it with its quantity to a meal
**So that** I can log what I eat without knowing its barcode.

```gherkin
Feature: Text search and logging

  Background:
    Given the user is on today's Diary

  Scenario: Debounced search
    When they add food and type "pechuga pollo" in the search box
    Then the search is triggered 400 ms after the last keystroke
    And skeletons are shown while loading
    And the results are listed with name, brand and kcal per 100 g

  Scenario: Adding a food with a serving
    Given the results for "pechuga pollo"
    When the user chooses "Pechuga de pollo" (110 kcal/100 g)
    Then a Bottom Sheet opens with the nutrition card and the quantity prefilled with the suggested serving or 100 g
    When they change the quantity to 220 g, choose the meal "Comida" and tap "Añadir"
    Then a 242 kcal entry is logged in today's "Comida"
    And the user returns to the Diary with updated totals and haptic feedback

  Scenario: Choosing a named portion
    Given "Huevo entero" has the portion "1 huevo" = 55 g
    When the user picks "1 huevo" and chooses the meal "Desayuno"
    Then the entry is logged with 55 g and the matching kcal snapshot

  Scenario: Preselected meal
    Given the user taps "Añadir a la cena" in the Cena section
    When they add a food from the search
    Then the entry is logged in "Cena" without having to choose the meal

  Scenario: No results
    When they search for "xyzxyz"
    Then "No hemos encontrado alimentos" is shown with the "Crear alimento" option

  Scenario: Recent foods
    Given the user has logged foods in the last 14 days
    When they open the search without typing
    Then they see their most recent foods to add them directly

  Scenario: Most eaten foods
    When they open the "Más comidos" tab
    Then the foods they log most often appear first
    And the ranking is derived from their own history, not a stored copy

  Scenario: Marking a favourite
    Given a food result
    When the user taps the star
    Then the food is saved to their favourites
    And it appears in the "Favoritos" tab
    When they tap the star again
    Then it is removed from favourites
```

**Technical notes**
- Default meal when none is preselected: the first enabled meal slot of the day (see US-23); with the default slots, before 11:00 breakfast, 11–16 lunch, 16–20 snack, later dinner.
- Quantity with `NumberInput` (g or ml, 1 decimal) or a named portion (US-22); validation > 0 and ≤ 5000.
- kcal/macro snapshot calculated in the domain (`04-business-rules.md` §2) and stored in the entry.
- "Más comidos" from the `most_eaten_foods` RPC (derived counts); "Favoritos" from the `favorite_foods` table (`03-data-model.md` §3).

---

## US-18 · Barcode scanning and manual food creation
**As** a user in a hurry
**I want** to scan a product's barcode
**So that** I can log it without typing its name.

```gherkin
Feature: Barcode scanning

  Scenario: Product found
    Given the user is in the food search
    When they tap the scanner icon
    Then the Google scanner opens
    When they scan a code that exists in the catalog or OFF
    Then the nutrition card Bottom Sheet opens with an editable quantity
    And tapping "Añadir" logs it in the selected meal

  Scenario: Drink scanned
    When they scan a drink whose nutrition is per 100 ml
    Then the quantity field uses millilitres

  Scenario: Product not found
    When they scan a code that does not exist
    Then "Producto no encontrado" is shown with the "Crear alimento" button
    And tapping it opens the creation form with the barcode already filled in

  Scenario: Scan cancelled
    When the user closes the scanner without reading any code
    Then they return to the search without error messages

  Scenario: Manual food creation
    Given the "Crear alimento" form
    When they enter the name "Tortilla de patatas casera", 190 kcal, 7 g protein, 15 g carbs and 11 g fat per 100 g
    And they tap "Guardar"
    Then a personal food is created (source "user") with base unit "g"
    And its card opens so it can be added to a meal

  Scenario: Adding a portion to a manual food
    Given the "Crear alimento" form
    When the user adds the portion "1 ración" = 250 g
    Then the food keeps that portion and offers it when it is logged

  Scenario: Manual creation validation
    When they leave the name empty or enter negative kcal
    Then the "Guardar" button is disabled and the field shows the error

  Scenario: Creating a food without scanning
    Given the user is in the food search (with or without a typed text)
    When they tap the "Crear alimento" icon button next to the scanner
    Then the creation form opens with the typed text as the name (empty if none) and no barcode
    And the scanner is never required first
```

**Technical notes**
- `BarcodeScanner` (domain) + ML Kit `GmsBarcodeScanning` implementation (EAN-13, EAN-8, UPC-A, UPC-E formats, auto zoom enabled) in androidMain.
- The search always offers "Crear alimento" (icon button next to the scanner); the empty-result button stays.
- The manual form lets the user pick the base unit (g or ml) and define optional named portions (`food_portions`, US-22).

---

## US-19 · Daily diary by meal: view, edit and delete
**As** a user
**I want** to see what I ate each day organized by meal and be able to correct it
**So that** I keep an accurate record of my intake.

```gherkin
Feature: Daily diary by meal

  Scenario: Day structure
    Given the user opens the "Diario" tab
    Then they see the date "Hoy" with arrows to change the day
    And the sections of their configured meals (US-23)
    And each section shows its foods, its total kcal and its grams of P, C and F

  Scenario: Empty section
    Given the user has not logged anything for Cena
    Then the Cena section shows "Te quedan 560 kcal" and the "Añadir a la cena" button

  Scenario: Changing the day
    When the user taps the left arrow
    Then the "Ayer" diary is shown with its entries
    And the right arrow does not allow going beyond today

  Scenario: Editing an entry's quantity
    Given an entry "Pechuga de pollo 220 g · 242 kcal" in Comida
    When the user taps it, changes the quantity to 150 g and taps "Guardar"
    Then the entry becomes "150 g · 165 kcal" and the totals are recalculated

  Scenario: Deleting an entry
    When the user swipes an entry to the left and taps "Eliminar"
    Then the entry disappears immediately and the totals are recalculated

  Scenario: Moving an entry to another meal
    When in the edit Bottom Sheet they change the meal from "Merienda" to "Cena"
    Then the entry moves to the Cena section
```

**Technical notes**
- `log_date` = the device's local date. Deleting is immediate, with no undo.
- The list is grouped by meal slot (`meal_slots`, US-23); collapsible sections (local state).
- Q2/Q3 withdrawn 2026-10-20 (user): view modes, copy and repeat-yesterday removed (T-066); the reusable meal is US-21.

---

## US-20 · Calorie and macro dashboard
**As** a user
**I want** to see at a glance how many calories and macros I have consumed against my target
**So that** I know how much I have left before the next meal.

```gherkin
Feature: Daily dashboard

  Background:
    Given the user's target is 2,400 kcal with 180 g protein, 270 g carbs and 67 g fat

  Scenario: Day progress
    Given today they have logged 1,840 kcal with 142 g protein, 210 g carbs and 48 g fat
    When they open the "Diario" tab
    Then the ring shows "1.840" and "de 2.400 kcal" with "560 restantes"
    And the Proteínas bar shows "142 g / 180 g · 79 %"
    And the Carbohidratos bar shows "210 g / 270 g · 78 %"
    And the Grasas bar shows "48 g / 67 g · 72 %"

  Scenario: Calorie excess
    Given today they have logged 2,550 kcal
    Then the ring is shown full in the alert color (energyAmber)
    And the secondary text shows "+150 kcal en exceso"

  Scenario: Immediate update
    When the user adds, edits or deletes an entry
    Then the ring and bars update without reloading the screen

  Scenario: User without goals
    Given a user without nutrition_goals
    Then the dashboard shows "Configura tu objetivo" with a link to Profile
```

**Technical notes**
- `ProgressRing` and `MacroProgressBar` from the design system. Totals derived from the same `StateFlow` as the list (no second query).
- On tablet: dashboard in the left column, meals on the right.

---

## US-21 · Saved meals (templates)
**As** a user with recurring meals
**I want** to save a meal as a named "comida" and add it to any meal from the search
**So that** I don't log the same foods one by one every day.

```gherkin
Feature: Saved meals

  Scenario: Saving a section as a comida
    Given today's Desayuno contains "Huevos revueltos 120 g" and "Batido de proteína 300 g"
    When the user taps the "Guardar como comida" icon of Desayuno
    And names it "Desayuno A"
    Then the comida "Desayuno A" is created with those two foods and quantities

  Scenario: Duplicate name
    Given the comida "Desayuno A" already exists
    When they try to save another one with the same name
    Then "Ya existe una comida con ese nombre" is shown

  Scenario: Adding a comida to a meal
    When from "Añadir a la comida" they choose the "Comidas" tab and select "Desayuno A"
    Then its foods are added to the chosen meal
    And the dashboard updates immediately

  Scenario: Editing a comida
    Given the comida "Desayuno A" in the "Comidas" tab
    When the user edits it (change quantities, remove foods) and taps "Guardar"
    Then they choose "Sobrescribir" or "Guardar como nueva"
    And "Sobrescribir" replaces "Desayuno A"
    And "Guardar como nueva" asks for a name and creates another comida, leaving "Desayuno A" unchanged
    And diary entries already created from it do not change

  Scenario: Renaming and deleting a comida
    When the user renames or deletes "Desayuno A" from the "Comidas" tab
    Then the change is saved
    And diary entries already created from it do not change
```

**Technical notes**
- Backend and code keep the name `meal_templates` / `meal_template_items` (only the UI wording is "comida", ADR-42).
- No quick-add chips on the Diary (user decision 2026-10-21, ADR-42): the "Comidas" tab of the search is the only way to add a saved meal.
- Adding a comida is a batch insert of `food_log_entries` (one request) with snapshots from current food values.
- E7 extends saved comidas with a cooking method per item (US-50): the method is captured when saving and editable in the comida details; adding the comida copies it and computes the added fat with the current Preferencias.

---

## US-22 · Food portions and measures
**As** a user
**I want** to log foods by a named portion or by millilitres, not only grams
**So that** logging matches how I actually eat and drink ("1 huevo", "250 ml").

```gherkin
Feature: Portions and measures

  Background:
    Given every food has a base unit: "g" for solids and "ml" for drinks

  Scenario: Logging a drink by millilitres
    Given "Zumo de naranja" has base unit "ml" (45 kcal/100 ml)
    When the user logs 250 ml
    Then the entry stores 250 (base units) and 113 kcal

  Scenario: Creating a named portion
    Given the personal food "Tortilla de patatas casera"
    When the user adds the portion "1 ración" = 250 g
    Then that portion is offered the next time the food is logged

  Scenario: Logging with a named portion
    Given "Huevo entero" has the portion "1 huevo" = 55 g
    When the user logs "2 huevos"
    Then the entry stores 110 g and the scaled nutrition snapshot

  Scenario: Logging a custom amount
    Given "Arroz cocido" (130 kcal/100 g)
    When the user types 180 g
    Then the entry stores 180 g and 234 kcal

  Scenario: Scaling per-100 nutrition
    Given a food with kcal_100 = 110
    When the logged amount is 220 (base units)
    Then the entry kcal is 110 × 220 / 100 = 242

  Scenario: Portion from OFF
    Given OFF offers a serving "1 vaso (200 ml)" for a drink
    When the product is cached
    Then the serving is stored as a portion of the food
```

**Technical notes**
- Model (`03-data-model.md` §3): `foods.base_unit` (`g`|`ml`) + nutrient columns per 100 base units; `food_portions` (named portions, amount in base units); `food_log_entries.amount` in base units plus a `portion_label` snapshot.
- Conversion (`04-business-rules.md` §2): `kcal = kcal_100 × amount / 100`.
- Domain: a pure function resolving a portion or a raw amount to base units, with unit tests.
- No cross-unit conversion (there is no g↔ml density): a food is either solid (g) or liquid (ml).

---

## US-23 · Custom meal structure
**As** a user
**I want** to rename, add, delete and reorder meals
**So that** the Diary matches my routine.

```gherkin
Feature: Configurable meals

  Scenario: Default meals
    Given a new user
    Then their Diary has Desayuno, Comida, Merienda and Cena, in that order

  Scenario: Meals are edited from Perfil
    When the user opens Perfil -> Preferencias -> "Comidas"
    Then they see a compact list with one row per meal
    And the Diary has no "Comidas" button

  Scenario: Renaming a meal
    When the user taps the pencil icon of "Merienda" and renames it to "Post-entreno"
    Then the Diary shows "Post-entreno"
    And the existing entries keep their totals

  Scenario: Adding a custom meal
    When the user taps "Añadir comida" at the end of the list and names it "Segundo desayuno"
    Then the new row is appended
    And the user moves it after "Desayuno" with the arrows or the drag handle
    And it appears in the Diary in that position and can receive foods like any other meal

  Scenario: Deleting an unused meal
    Given "Merienda" has no logged entries on any day
    When the user taps its trash button
    Then it disappears at once (no dialog, no message, no undo)
    And it disappears from the "add to" targets

  Scenario: A meal with entries cannot be deleted
    Given "Comida" has logged entries
    When the user taps its trash button
    Then it stays
    And the row shows "Esta comida tiene alimentos registrados y no se puede eliminar"

  Scenario: At least one meal remains
    Given only one meal is left
    Then its trash button is disabled
    And the list shows "Debe quedar al menos una comida"

  Scenario: Reordering meals
    When the user moves "Cena" above "Merienda"
    Then the Diary shows the new order
```

**Technical notes**
- Model (`03-data-model.md` §3): `meal_slots` per user (`code` from the `meal_type` enum for the defaults, `label`, `position`, `enabled`); seeded with the four defaults when onboarding completes.
- `food_log_entries` references `meal_slot_id` instead of a fixed `meal_type`.
- Meals are deleted, never disabled (ADR-41): the `enabled` column is kept but always true. Delete rules (not the only meal, no entries on any day) are enforced by the `delete_meal_slot` RPC; the editor lives in Perfil -> Preferencias -> "Comidas".
- Custom meals have `code` null; the defaults keep their code so time-of-day suggestions still work.

---

## US-24 · Diary history and summaries
**As** a user
**I want** to browse past days and see totals and averages
**So that** I can review my intake over time.

> Placement (ADR-44, T-071): the history is a full-screen destination reached from "Estadísticas" on the Perfil root, not from the Diario; the Diario always has its calendar button to jump to a day.

```gherkin
Feature: Diary history

  Scenario: Opening the history
    When the user opens "Historial" from Perfil > Estadísticas
    Then they see a calendar with the days that have entries marked
    And tapping a day opens that day's Diary

  Scenario: Daily summary
    Given 18 October has 1,840 kcal logged
    When the user selects that day
    Then the summary shows the day's kcal and P/C/F totals against the target

  Scenario: Weekly average
    Given the last 7 days have entries
    When the user opens the week summary
    Then the average daily kcal and macros for those days are shown

  Scenario: Monthly total
    When the user opens the month summary
    Then the total kcal and the average per logged day for that month are shown

  Scenario: No data
    Given a day with no entries
    Then its summary shows "Sin registros"
```

**Technical notes**
- RPC `food_diary_summary(p_from date, p_to date)` returns per-day kcal and P/C/F totals (`03-data-model.md` §6); averages computed in the domain (`04-business-rules.md` §2).
- Calendar built with the design system's month grid; days with entries marked; navigation reuses the day Diary.
- Read-only: this story adds no write path.

---

## US-16b · Full Open Food Facts data and micronutrients in the food details
**As** a user
**I want** the foods from Open Food Facts to keep all their data and show their micronutrients
**So that** I can see what a food contains beyond calories and macros, and the data is there for future use.

```gherkin
Feature: Full OFF product data (ADR-43)

  Scenario: A product is cached with everything OFF sent
    When food-search or food-barcode stores a product from OFF
    Then the complete OFF product object is stored in foods.off_data
    And the typed columns (kcal, protein, carbs, fat, serving) are filled as before

  Scenario: Micronutrients in the food details
    Given a food from OFF whose data has fiber, sugars, salt and vitamin C
    When the user opens its details
    Then those values appear per 100 g (per 100 ml for drinks)
    And nutrients OFF does not provide are not listed

  Scenario: Food without extra data
    Given a personal food created without micronutrients, or an OFF food with none
    When the user opens its details
    Then no micronutrients section is shown

  Scenario: Micronutrients typed in a manual food
    Given the user creates a food and fills saturated fat, sugars, fiber and salt from the label
    When the food is saved
    Then its details show those values per 100 g (or ml)
    And leaving them empty is allowed, but a negative value disables "Guardar"

  Scenario: Search results stay light
    When food-search returns results
    Then the responses do not include off_data

  Scenario: Foods cached before this change
    Given a cached OFF food with null off_data that no diary entry, favourite or comida references
    When the migration runs
    Then it is deleted and refilled with full data on the next search or scan
    And a referenced food with null off_data is refreshed the next time it is fetched, keeping its id
    And diary entries, favourites, comidas, training data and preferences are unchanged
```

**Technical notes**
- Migration: `foods.off_data jsonb null` + cleanup of unreferenced OFF rows; pgTAP: referenced rows survive with the same id, unreferenced are gone, entries/favourites/templates untouched, RLS unchanged (`off_data` readable by anyone who can read the food).
- `off-client.ts` returns the raw product with the normalized food; `food-store.ts` writes `off_data`; `food-barcode` refreshes when `fetched_at` > 30 days **or** `off_data` is null. `deno test` with a full-product fixture.
- Domain: a pure mapper from OFF `nutriments` (`*_100g` / `*_100ml`, with units) to an ordered list of labelled micronutrients with Spanish labels; unit tests.
- Manual foods: optional fields for saturated fat, sugars, fiber and salt in the creation form (US-18 form), stored in `foods.micronutrients`; `create_personal_food` takes `p_micronutrients`; pgTAP covers invalid keys/negatives.
- UI wording and layout of the section: proposed to the user before building (feedback rule), design-system tokens only.

---

## Epic Definition of Done
- A full breakfast logged from a saved comida in a few taps from the search, and a new food via search in ≤ 3 taps after opening the search.
- A drink logged in ml and a solid logged by a named portion.
- The Diary can be renamed/reordered and its entries copied to another meal or day.
- `deno test` tests for the functions with fixtures; pgTAP for the 7 tables.
- Host (Robolectric) tests cover the Gherkin scenarios.
- `prodDebug` APK validated with real foods scanned by the user. Tag `v0.4.0`.
