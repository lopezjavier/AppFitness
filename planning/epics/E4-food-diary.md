# E4 · Food diary → `v0.4.0`

## Goal
Log what you eat in fewer than 3 taps (search, scanner or template) and see the calories and macros consumed against the target at all times.

## Scope
- Includes: food catalog with an Open Food Facts cache, text search, scanner, manual creation, daily diary with 4 meals, dashboard, templates.
- Out of scope: user-uploaded images, micronutrient values, composite recipes, "repeat yesterday's meal" (can be added as a corrective task if the user asks for it).

## Dependencies
E2 (authenticated user and `nutrition_goals`).

## Tables and functions
`foods`, `food_log_entries`, `meal_templates`, `meal_template_items`; Edge Functions `food-search` and `food-barcode` (`03-data-model.md` §3 and §7).

## Reference screen
`design/stitch/diary/screen.png` (without the water card or the "Repetir comida de ayer" chip).

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
- Migration: `foods` (with `search_vector` and GIN), `food_log_entries`, RLS and pgTAP.
- `functions/_shared/off-client.ts` with normalization (kJ → kcal) and `deno test` tests using recorded OFF responses (fixtures), no network in CI.
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
    When they tap "+ Registro" and type "pechuga pollo" in the search box
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
```

**Technical notes**
- Default meal when none is preselected: based on the time of day (before 11:00 breakfast, 11–16 lunch, 16–20 snack, later dinner).
- Quantity with `NumberInput` (g, 1 decimal); validation > 0 and ≤ 5000.
- kcal/macro snapshot calculated in the domain (`04-business-rules.md` §2) and stored in the entry.

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
    Then a personal food is created (source "user")
    And its card opens so it can be added to a meal

  Scenario: Manual creation validation
    When they leave the name empty or enter negative kcal
    Then the "Guardar" button is disabled and the field shows the error
```

**Technical notes**
- `BarcodeScanner` (domain) + ML Kit `GmsBarcodeScanning` implementation (EAN-13, EAN-8, UPC-A, UPC-E formats) in androidMain.

---

## US-19 · Daily diary with 4 meals: view, edit and delete
**As** a user
**I want** to see what I ate each day organized by meal and be able to correct it
**So that** I keep an accurate record of my intake.

```gherkin
Feature: Daily diary by meal

  Scenario: Day structure
    Given the user opens the "Diario" tab
    Then they see the date "Hoy" with arrows to change the day
    And the sections Desayuno, Comida, Merienda and Cena
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
    Then the entry disappears and a snackbar with "Deshacer" is shown

  Scenario: Moving an entry to another meal
    When in the edit Bottom Sheet they change the meal from "Merienda" to "Cena"
    Then the entry moves to the Cena section
```

**Technical notes**
- `log_date` = the device's local date. "Deshacer" (undo) = re-insert the deleted entry.
- The list is grouped by `meal_type`; collapsible sections (local state).

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

## US-21 · Meal templates
**As** a user with recurring meals
**I want** to save a meal as a template and add it with one tap
**So that** I don't log the same foods one by one every day.

```gherkin
Feature: Meal templates

  Scenario: Saving a section as a template
    Given today's Desayuno contains "Huevos revueltos 120 g" and "Batido de proteína 300 g"
    When the user taps the "Guardar como plantilla" icon of Desayuno
    And names it "Desayuno A"
    Then the template "Desayuno A" is created with those two foods and quantities

  Scenario: Duplicate name
    Given the template "Desayuno A" already exists
    When they try to save another one with the same name
    Then "Ya existe una plantilla con ese nombre" is shown

  Scenario: Quick add
    Given the 580 kcal template "Desayuno A"
    When the user taps the "Añadir rápido Desayuno A" chip on the Diary
    Then its foods are added to Desayuno of the visible day
    And the dashboard adds 580 kcal immediately
    And a confirmation with "Deshacer" and haptic feedback is shown

  Scenario: Adding a template to another meal
    When from "Añadir a la comida" they choose the "Plantillas" tab and select "Desayuno A"
    Then its foods are added to Comida

  Scenario: Managing templates
    Given the template list in the search
    When the user edits "Desayuno A" (rename, change quantities, remove foods) or deletes it
    Then the changes are saved
    And diary entries already created from that template do not change
```

**Technical notes**
- `meal_templates` + `meal_template_items` migration + RLS (items via parent) + pgTAP.
- "Quick add": batch insert of `food_log_entries` (one request). Chips: templates whose `meal_type` matches the current time slot, at most 3.

---

## Epic Definition of Done
- A full breakfast logged from a template in 1 tap, and a new food via search in ≤ 3 taps after opening the search.
- `deno test` tests for the functions with fixtures; pgTAP for the 4 tables.
- Host (Robolectric) tests cover the Gherkin scenarios.
- `prodDebug` APK validated with real foods scanned by the user. Tag `v0.4.0`.
