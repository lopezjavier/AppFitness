# E7 · Food photos, defaults and cooking → `v0.7.0`

## Goal
Cut the typing out of food logging: create a personal food from a photo, fill a new day automatically with saved comidas, and account for the olive oil used when cooking, so the diary stays accurate with less effort.

## Scope
- Includes:
  - **Photo food creation**: an "Añadir desde foto" action in the add-food screen with three modes (AI estimate of a dish, nutrition-label photo, barcode photo). A fixed prompt per mode, an optional comment for the dish estimate, editable AI values and portions, saved as a personal food and then logged through the normal add-to-meal sheet.
  - **Default comidas**: from Preferencias → Comidas, assign saved comidas (the user's "Comidas", not Open Food Facts foods) to a meal on selected weekdays; the first time a new day's Diary is opened, their foods are added automatically.
  - **Cooking methods**: when logging a solid food, choose Crudo (default), A la plancha, Air fryer or Frito. The energy and fat of the retained olive oil are added; the amounts are configurable in Preferencias. Saved comidas store an editable method per item; adding one or applying a default copies that method.
- Out of scope:
  - Estimating a whole meal (several foods) from one photo; Phase 1 handles one food per photo.
  - Storing, uploading or browsing food photos (no gallery, no Supabase Storage, no photo history).
  - The AI training assistant and adaptive calorie adjustment (Phase 2).
  - Micronutrient targets or totals (display only, ADR-43).
  - Midnight application, backfill of days the app was not opened, notifications and reminders.
  - A separate food source for AI: an AI-created food is a normal personal food (`source = 'user'`). Source describes ownership/cache, not how the values were entered; provenance, if ever needed, would be a separate field.

## Dependencies
E2 (Preferencias), E4 (food catalog, portions, diary and saved comidas) and E6 US-47 (the manual-food form that carries the full label nutrient list and its editing rules). The Edge Function infrastructure comes from E1.

## Tables and functions
`foods`, `food_portions`, `food_log_entries`, `meal_templates`, `meal_template_items`; new `food_preferences`, `meal_defaults` and `diary_default_applications`; RPCs `save_preferences`, `save_meal_defaults`, `apply_meal_defaults` and the existing `create_personal_food`; Edge Functions `food-photo` and `food-barcode` (`03-data-model.md` §3, §6 and §7).

## Reference screen
`design/stitch/diary/screen.png` for the add-food style only. There is no mockup for the photo flow or the default-comidas screen: both follow the design system, and their wording and layout are proposed to the user before building (feedback rule).

> Gherkin convention: steps in English; quoted UI texts in Spanish (the app UI language, with decimal commas). Weekdays are written in Spanish (Lunes…Domingo). Exercise and food names are catalog data.

---

## US-48 · Add a food from a photo with the AI estimate
**As** a user
**I want** to photograph a dish and have the AI estimate its nutrition
**So that** I can add a personal food without typing every value.

```gherkin
Feature: Food from a photo

  Scenario: Photo action and mode picker
    Given the user is in the add-food screen
    Then the photo icon is shown directly below the barcode icon, with the description "Añadir desde foto"
    When they tap it
    Then they see "Estimar con IA", "Etiqueta nutricional" and "Código de barras"
    And they can go back without taking a photo

  Scenario: Dish estimate
    Given the user chooses "Estimar con IA"
    When they take a photo of a "Tortilla de patatas"
    Then they see "Estimando…" while the AI responds
    And the manual-food form opens with the name "Tortilla de patatas", 180 kcal, 6 g protein, 15 g carbohydrates and 10 g fat per 100 g
    And the form includes the micronutrients the AI could estimate
    And a suggested portion "1 ración" of 250 g is prefilled

  Scenario: Optional comment
    When they write "Lleva poca cebolla y está hecha con aceite de oliva" before sending the photo
    Then the comment is sent with the fixed estimation prompt
    And leaving it empty is allowed

  Scenario: Edit before saving
    Given the AI-prefilled form
    When they change the name, the kcal, a macro, a micronutrient, the base unit or a portion
    Then the edited values are kept
    And "Guardar" is enabled only with a non-empty name and non-negative nutrition values

  Scenario: Save and log
    When they tap "Guardar"
    Then a personal food is created with source "user" and the current values
    And the normal add-to-meal sheet opens for that food
    And the food is available in later searches

  Scenario: AI failure
    Given the AI is unavailable, the quota is exhausted or the photo is unreadable
    Then "No hemos podido estimar este alimento" is shown with "Reintentar" and "Crear manualmente"
    And no food is created
    And "Crear manualmente" opens the empty manual-food form

  Scenario: No photo is kept
    When the estimate finishes or fails
    Then the photo is removed from the app cache and is not stored in the database or in Storage

  Scenario: Offline
    Given there is no connection
    When the user taps the photo action
    Then the offline message is shown and the photo cannot be sent
```

**Technical notes**
- The add-food screen adds an `AppIconButton` with the photo action directly below the scanner button; the plus ("Crear alimento") action stays where it is.
- `PhotoCapture` in the domain with an Android implementation using the system camera (`ActivityResultContracts.TakePicture` + `FileProvider`), so no camera permission or CameraX is introduced. Images are downscaled to at most 1600 px and compressed to JPEG (about 1.5 MB) before upload; the temporary file is deleted in all outcomes.
- The flow reuses `CreateFoodScreen`: an optional prefill (`NewFoodDraft`) opens it with the AI values instead of an empty form. Saving calls the existing `create_personal_food`; on success the current `AddFoodSheet` opens as after a manual creation.
- `food-photo` is a Supabase Edge Function (`verify_jwt = true`). Request: `POST` JSON `{ image_base64, mode: "dish" | "label" | "barcode", comment?: string }`; the comment is optional, dish-only and at most 200 characters, and is passed as data, never as instructions. The fixed prompts live in the function, not in the app.
- The function uses a **fixed response schema**: name, brand, base unit, nutrients per 100 base units, micronutrients keyed by OFF keys, optional estimated total grams and confidence. For a dish it converts the estimate to per-100 values and proposes `1 ración` with the estimated weight; for a label it extracts the printed per-100 values and does not invent missing ones; for a barcode it identifies the product and returns its per-100 values. Negatives are rejected or clamped to 0; a response without kcal and macros is an error.
- No photo, comment or provider response body is logged. The Gemini key lives only in the Edge Function secret (ADR-59, `08-environment-setup.md`).

---

## US-49 · AI nutrition-label and barcode photos
**As** a user
**I want** to photograph a label or a barcode and let the AI fill the food data
**So that** I don't type the product data when I have the package in front of me.

```gherkin
Feature: Label and barcode photos

  Scenario: Nutrition label
    Given the user chooses "Etiqueta nutricional"
    When they take a clear photo of the label of "Yogur griego natural"
    Then the manual-food form opens with the name and brand read from the label
    And the per-100 kcal, macros and micronutrients printed on the label are prefilled
    And values the label does not show stay empty

  Scenario: Barcode photo
    Given the user chooses "Código de barras"
    When they take a photo of the barcode "8410000810004"
    Then the AI reads the barcode and returns the product and its nutritional values
    And the manual-food form opens prefilled with that data

  Scenario: Product not identified
    Given the AI cannot read the label or identify the product from the barcode
    Then "No hemos podido identificar este producto" is shown with "Reintentar" and "Crear manualmente"
    And no food is created

  Scenario: The live scanner is unchanged
    When the user taps the scanner icon
    Then the existing Google code scanner opens and uses the existing lookup
```

**Technical notes**
- Label and barcode modes use the same `food-photo` function as the dish mode, with their own fixed prompt; the AI only transcribes the label values and, for a barcode, identifies the product and returns its per-100 values.
- The barcode result opens the same prefilled `CreateFoodScreen` as the other AI modes and is saved as a normal personal food. This flow never calls `food-barcode`, does not save the code and adds no still-image barcode dependency.
- The existing scanner icon, live Google code scanner and `food-barcode`/OFF lookup are unchanged.

---

## US-50 · Cooking methods with olive oil
**As** a user
**I want** to choose how a food was cooked
**So that** the oil I use is reflected in the calories and fat I log.

```gherkin
Feature: Cooking methods

  Scenario: Default is raw
    Given the add-to-meal sheet of a solid food
    Then "Crudo" is selected by default
    And "A la plancha", "Air fryer" and "Frito" are available
    And the totals equal the stored food values

  Scenario: Pan-seared impact
    Given the defaults of 5 g of olive oil used and 50 % retained
    When the user selects "A la plancha"
    Then the sheet shows "+22,5 kcal y +2,5 g de grasa"
    And the kcal and fat totals include that impact

  Scenario: Air fryer impact
    Given the defaults of 5 g of olive oil used and 100 % retained
    When the user selects "Air fryer"
    Then the sheet shows "+45 kcal y +5 g de grasa"

  Scenario: Fried impact
    Given the defaults of 50 g of olive oil used and 10 % retained
    When the user selects "Frito"
    Then the sheet shows "+45 kcal y +5 g de grasa"

  Scenario: Save
    When the user taps "Añadir"
    Then the entry stores the cooking method, the retained oil and the adjusted kcal and fat snapshots
    And the diary totals include them

  Scenario: Cooking in a saved comida
    Given a saved comida with "Pechuga de pollo 200 g" and "Patatas 150 g"
    When the user edits the comida and sets the chicken to "A la plancha" and the potatoes to "Frito"
    Then each item keeps its own method
    When they add the comida to a meal
    Then each entry keeps its saved method and includes the corresponding retained oil

  Scenario: Saving a meal with cooking
    Given today's Comida has "Pechuga de pollo 200 g" cooked "A la plancha"
    When the user saves that meal as a comida
    Then the comida stores "A la plancha" for that food

  Scenario: Drinks are not cooked
    Given a food whose base unit is "ml"
    Then the cooking methods are not offered

  Scenario: Changing the preferences
    Given the user opens "Perfil" → "Preferencias" → "Cocina"
    When they change the olive-oil grams used for "A la plancha", "Air fryer" or "Frito" and save
    Then the new amounts apply to future entries only
    And existing entries keep their snapshots

  Scenario: Invalid amount
    When they enter a negative amount or more than 500 g
    Then "Guardar" is disabled and the field shows the error
```

**Technical notes**
- Enum `food_cooking_method` (`raw`, `griddle`, `air_fryer`, `fried`); UI labels Crudo, A la plancha, Air fryer, Frito. Only foods with `base_unit = 'g'` offer it; drinks stay raw.
- Fixed retention factors: A la plancha 50 %, Air fryer 100 %, Frito 10 %. Defaults for the oil used: 5 g, 5 g and 50 g. The amounts are stored in `food_preferences` and edited from a new "Cocina" section in Preferencias; the factors are not configurable.
- Domain pure function: `retained_g = used_g × factor`; `added_fat_g = retained_g`; `added_kcal = 9 × retained_g`; protein, carbohydrates and micronutrients do not change. Entries store `cooking_method`, `cooking_oil_retained_g` and the adjusted kcal/fat snapshots; the food itself is unchanged.
- `meal_template_items.cooking_method` stores the method per item (default `raw`); it is editable when saving a meal as a comida and in "Editar comida", and `save_meal_template` accepts it per item. The template does not store oil: adding a comida from the "Comidas" tab, copying it or applying a default computes `cooking_oil_retained_g` with the current `food_preferences`. A saved comida's shown total kcal includes that cooking impact.
- `save_meal_template` and the client validate that only `g` foods carry a non-raw method; an `ml` item stays raw. Existing template items are backfilled to `raw`.
- The add-to-meal sheet shows the impact before saving. Editing an entry's amount keeps the stored method and retained oil; re-selecting the method on an existing entry is out of scope for this epic.
- `save_preferences` carries the three oil amounts so Preferencias keeps its single "Guardar"; a missing `food_preferences` row reads as the defaults.

---

## US-51 · Default comidas per meal and weekday
**As** a user with recurring meals
**I want** saved comidas to be added to a meal automatically on the days I choose
**So that** a new day starts with my usual breakfast, lunch or dinner already logged.

```gherkin
Feature: Default comidas

  Scenario: Opening the defaults screen
    Given the user opens "Perfil" → "Preferencias" → "Comidas"
    Then there is a "Comidas por defecto" button
    When they tap it
    Then they see their meals in their saved order
    And each meal shows its default comidas and the days assigned

  Scenario: Assigning a saved comida
    Given the saved comida "Desayuno A" with "Huevos revueltos 120 g" and "Batido de proteína 300 g"
    When the user adds "Desayuno A" to "Desayuno"
    Then Lunes to Domingo are selected by default
    When they keep only Lunes, Miércoles and Viernes and tap "Guardar"
    Then "Desayuno A" is the default comida of "Desayuno" on those three days

  Scenario: Different comidas on different weekdays
    Given "Desayuno A" is assigned to Lunes, Miércoles and Viernes
    When the user assigns "Desayuno B" to Martes and Jueves
    Then each weekday has exactly one default comida for "Desayuno"

  Scenario: A new day starts with the defaults
    Given today is Lunes and the Diary has no entries
    When the user opens today's Diary for the first time
    Then the two foods of "Desayuno A" are in "Desayuno"
    And the dashboard includes them

  Scenario: Another weekday without a default
    Given today is Martes and "Desayuno A" is only assigned to Lunes, Miércoles and Viernes
    When the user opens today's Diary
    Then "Desayuno" stays empty

  Scenario: No duplication
    When the user leaves the Diary and opens it again the same day
    Then the foods of the default comida are not added again

  Scenario: Deleted entries do not come back
    Given the default foods were added today
    When the user deletes them
    Then they stay deleted for the rest of the day

  Scenario: Past days are not filled
    When the user opens yesterday's Diary
    Then no default comida is added

  Scenario: A day that already has entries is not filled
    Given today's Diary already has an entry when the defaults are evaluated
    Then no default comida is added
    And the day is marked as processed

  Scenario: Changes apply to future days
    Given today's defaults were already applied
    When the user changes the days or the comida assigned to "Desayuno" and saves
    Then today's entries do not change
    And the next new day uses the new assignment

  Scenario: The saved comida changes later
    Given "Desayuno A" is edited after it was applied
    Then the entries already added keep their snapshots
    And a future application uses the current foods, amounts and cooking methods of "Desayuno A"

  Scenario: One comida per meal and day
    Given "Desayuno A" is assigned to Lunes
    When the user tries to assign "Desayuno B" to Lunes for "Desayuno"
    Then Lunes is shown as already assigned to "Desayuno A" and cannot be selected for "Desayuno B"

  Scenario: At least one day
    When the user deselects every day
    Then "Guardar" is disabled and "Selecciona al menos un día" is shown

  Scenario: Removing a default
    Given "Desayuno A" is the default comida of "Desayuno" on Lunes
    When the user removes it from "Desayuno" and taps "Guardar"
    Then "Desayuno" has no default comida on Lunes

  Scenario: Every day already assigned
    Given "Desayuno A" is assigned to all seven days for "Desayuno"
    When the user opens the picker to add another comida to "Desayuno"
    Then "Añadir comida" is disabled with "Todos los días ya tienen una comida"

  Scenario: No saved comidas
    Given the user has no saved comidas
    When they open "Comidas por defecto"
    Then "No tienes comidas guardadas" is shown
    And the hint explains how to create one from "Añadir comida" → "Comidas"

  Scenario: Deleting a meal or a comida
    Given a meal or a saved comida with a default assignment
    When it is deleted
    Then its default assignments disappear
    And the entries already logged are not changed

  Scenario: Saving defaults and going back
    When the user leaves "Comidas por defecto" with unsaved changes
    Then they are asked to discard them
    And the meals editor does not open the defaults screen while its own changes are unsaved
```

**Technical notes**
- Source of truth is the saved comidas: `meal_templates` + `meal_template_items`. The picker never lists `foods` or Open Food Facts results. Applying a default inserts one `food_log_entries` row per template item, with the same amount, portion label, saved cooking method, retained oil (from the current `food_preferences`) and nutrition snapshots as adding a comida from the "Comidas" tab (US-21).
- The assignment's meal slot is authoritative; the template's suggested `meal_slot_id` is ignored. One saved comida may be assigned to several meals and days.
- `meal_defaults` stores one row per `(user_id, meal_slot_id, weekday)`, unique, so a meal can have different comidas on different weekdays but never two on the same day. Weekday is ISO (Lunes = 1 … Domingo = 7).
- `save_meal_defaults(p_defaults jsonb)` replaces all assignments for the caller atomically (`[{meal_slot_id, template_id, weekdays:[1,3,5]}]`). It validates ownership, weekday range/duplicates and rejects empty saved comidas.
- `apply_meal_defaults(p_log_date date)` is idempotent through `diary_default_applications (user_id, log_date)`. It runs on the first load of **today's** Diary, before the entries are read. If the day already has entries, it only records the marker. It inserts the matching defaults in one transaction and returns the number of entries added.
- The client sends the device-local date; the server derives the ISO weekday. There is no midnight job and no backfill: if the app is not opened on a selected day, that day stays empty.
- If the apply call fails, the Diary still loads and shows a non-blocking message with "Reintentar"; the RPC can be retried safely. Configuring defaults after today's Diary was already opened applies from the next day.
- The defaults screen lives in the meals editor (`Perfil → Preferencias → Comidas`) as a secondary button. It has its own "Guardar" and the usual discard-on-back behaviour; it is disabled while the meal structure has unsaved changes. The day picker preselects every available day; days already assigned to another comida for that meal are disabled and name the current comida. Each assigned row has a remove icon; removing every assignment and saving clears the defaults (`[]`).
- Loading, empty, error and offline states follow the design system; the saved-comida picker shows name, number of foods and total kcal. If a meal has all seven days assigned, "Añadir comida" is disabled and explains why.

---

## Epic Definition of Done
- `food-photo` is covered by `deno test` with recorded Gemini fixtures (dish, label, barcode, unreadable, invalid input, provider error); CI never calls the network.
- pgTAP covers `food_preferences`, `meal_defaults`, `diary_default_applications`, RLS isolation, the unique meal/weekday constraint, cascades, `save_meal_template` cooking validation, `save_meal_defaults` validation and `apply_meal_defaults` idempotency (including no re-add after deleting entries).
- Domain tests cover the cooking calculations and the weekday/selection helpers; host (Robolectric) tests cover the photo modes, the prefilled manual form, the cooking selector (add-to-meal, save-as-comida and edit-comida) and the default-comidas screen.
- No food photo, comment or provider response is stored or logged; the Gemini key exists only in Supabase secrets.
- Every new screen has loading, empty, error and offline states; the photo and defaults flows survive leaving the app and coming back where a draft exists.
- `prodDebug` APK validated by the user. Tag `v0.7.0`.
