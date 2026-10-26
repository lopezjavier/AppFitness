# E7 · Food photos, defaults and cooking → `v0.7.0`

## Goal
Cut the typing out of food logging: create a personal food from a photo, fill a new day automatically with saved comidas, and account for the fat added when cooking, so the diary stays accurate with less effort.

## Scope
- Includes:
  - **Photo food creation**: an "Añadir desde foto" action in the add-food screen with three modes (AI estimate of a dish, nutrition-label photo, barcode photo), using the system camera or the Android photo picker. A fixed prompt per mode, an optional comment for the dish estimate, editable AI values and portions, saved as a personal food and then logged through the normal add-to-meal sheet.
  - **Default comidas**: from Preferencias → Comidas, assign saved comidas (the user's "Comidas", not Open Food Facts foods) to a meal on selected weekdays; the first time a new day's Diary is opened, their foods are added automatically.
  - **Cooking methods**: when logging a solid food, choose Crudo (default), A la plancha, Air fryer or Frito. The energy and fat added by cooking (a percentage of the raw weight) are counted; the percentages are configurable in Preferencias. Saved comidas store an editable method per item; adding one or applying a default copies that method.
  - **Checkpoint E6-C change requests (ADR-62)**: change the unit of a manual food while nothing references it (US-52); a saved comida opens straight into its editable details with "Guardar" and "Añadir" (US-53, ADR-63).
- Out of scope:
  - Estimating a whole meal (several foods) from one photo; Phase 1 handles one food per photo.
  - Storing, uploading or browsing food photos (no in-app gallery or photo history; the system photo picker only selects an image for inference, and no Supabase Storage is used).
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
    Then the photo icon is shown next to the barcode icon, in the same row as the search field, with the description "Añadir desde foto"
    When they tap it
    Then they see "Estimar con IA", "Etiqueta nutricional" and "Código de barras"
    And they can go back without choosing a photo

  Scenario: Photo source
    Given the user chooses a photo mode
    Then they see "Hacer foto" and "Elegir de la galería"
    When they choose "Elegir de la galería"
    Then the Android photo picker opens
    And selecting an image shows it with "Repetir" and "Usar foto"
    And no media permission is requested

  Scenario: Dish estimate
    Given the user chooses "Estimar con IA"
    When they take a photo of a "Tortilla de patatas"
    Then they see "Estimando…" while the AI responds
    And the manual-food form opens with the name "Tortilla de patatas", 180 kcal, 6 g protein, 15 g carbohydrates and 10 g fat per 100 g
    And the form includes the micronutrients the AI could estimate
    And a suggested portion "1 ración" of 250 g is prefilled

  Scenario: Estimate from a gallery image
    Given the user chooses "Estimar con IA"
    When they choose "Elegir de la galería" and select a dish photo
    Then the same estimate runs with that image

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
- The add-food screen adds an `AppIconButton` with the photo action next to the scanner button, in the same row as the search field; the plus ("Crear alimento") action stays where it is.
- `PhotoCapture` in the domain with an Android implementation using the system camera (`ActivityResultContracts.TakePicture` + `FileProvider`) or the Android photo picker (`ActivityResultContracts.PickVisualMedia`). Both sources feed the same downscale/compress path; no camera or media permission and no CameraX are introduced. Images are downscaled to at most 1600 px and compressed to JPEG (about 1.5 MB) before upload; the temporary file is deleted in all outcomes.
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

## US-50 · Cooking methods with added fat
**As** a user
**I want** to choose how a food was cooked
**So that** the fat added by cooking is reflected in the calories and fat I log.

```gherkin
Feature: Cooking methods

  Scenario: Default is raw
    Given the add-to-meal sheet of a solid food
    Then "Crudo" is selected by default
    And "A la plancha", "Air fryer" and "Frito" are available
    And the totals equal the stored food values

  Scenario: Pan-seared impact
    Given the default of 1 % added fat and 300 g of a solid food
    When the user selects "A la plancha"
    Then the sheet shows "+27 kcal y +3 g de grasa"
    And the kcal and fat totals include that impact

  Scenario: Air fryer impact
    Given the default of 2 % added fat and 300 g of a solid food
    When the user selects "Air fryer"
    Then the sheet shows "+54 kcal y +6 g de grasa"

  Scenario: Fried impact
    Given the default of 8 % added fat and 300 g of a solid food
    When the user selects "Frito"
    Then the sheet shows "+216 kcal y +24 g de grasa"

  Scenario: The impact follows the amount
    Given "Frito" is selected for 300 g of a solid food
    When the user changes the amount to 200 g
    Then the sheet shows "+144 kcal y +16 g de grasa"

  Scenario: Save
    When the user taps "Añadir"
    Then the entry stores the cooking method, the added fat and the adjusted kcal and fat snapshots
    And the diary totals include them

  Scenario: Cooking in a saved comida
    Given a saved comida with "Pechuga de pollo 200 g" and "Patatas 150 g"
    When the user edits the comida and sets the chicken to "A la plancha" and the potatoes to "Frito"
    Then each item keeps its own method
    When they add the comida to a meal
    Then each entry keeps its saved method and includes the added fat for its own amount and method

  Scenario: Default comidas use the percentage
    Given a default comida with "Pechuga de pollo 300 g" cooked "Frito" and the default of 8 %
    When a new day is applied
    Then the entry stores +24 g of added fat and +216 kcal on top of the food's values

  Scenario: Saving a meal with cooking
    Given today's Comida has "Pechuga de pollo 200 g" cooked "A la plancha"
    When the user saves that meal as a comida
    Then the comida stores "A la plancha" for that food

  Scenario: Drinks are not cooked
    Given a food whose base unit is "ml"
    Then the cooking methods are not offered

  Scenario: Changing the preferences
    Given the user opens "Perfil" → "Preferencias" → "Cocina"
    Then three percentage fields are shown, for "A la plancha" (1 %), "Air fryer" (2 %) and "Frito" (8 %)
    When they change a percentage and save
    Then the new percentage applies to future entries only
    And existing entries keep their snapshots

  Scenario: Invalid percentage
    When they enter a percentage below 0 or above 20
    Then "Guardar" is disabled and the field shows the error
```

**Technical notes**
- Enum `food_cooking_method` (`raw`, `griddle`, `air_fryer`, `fried`); UI labels Crudo, A la plancha, Air fryer, Frito. Only foods with `base_unit = 'g'` offer it; drinks stay raw.
- ADR-65: added fat is a percentage of the raw weight. Defaults: A la plancha 1 %, Air fryer 2 %, Frito 8 %, editable 0-20 %, stored in `food_preferences` (`fat_pct_*`) and edited from the "Cocina" section in Preferencias. There are no retention factors.
- Domain pure function: `added_fat_g = round(raw_g × pct / 100, 1)`; `added_kcal = round(9 × added_fat_g, 1)`; protein, carbohydrates and micronutrients do not change. Entries store `cooking_method`, `cooking_oil_retained_g` (the column name is kept; it holds the added fat in grams) and the adjusted kcal/fat snapshots; the food itself is unchanged.
- `meal_template_items.cooking_method` stores the method per item (default `raw`); it is editable when saving a meal as a comida and in the comida details, and `save_meal_template` accepts it per item. The template does not store the fat: adding a comida from the "Comidas" tab, copying it or applying a default computes the added fat (`cooking_oil_retained_g`) from the item's amount and the current `food_preferences`. A saved comida's shown total kcal includes that cooking impact.
- `save_meal_template` and the client validate that only `g` foods carry a non-raw method; an `ml` item stays raw. Existing template items are backfilled to `raw`.
- The add-to-meal sheet shows the impact before saving. Editing an entry's amount keeps the stored method and added fat; re-selecting the method on an existing entry is out of scope for this epic.
- `save_preferences` carries the three percentages so Preferencias keeps its single "Guardar"; a missing `food_preferences` row reads as the defaults.

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
- Source of truth is the saved comidas: `meal_templates` + `meal_template_items`. The picker never lists `foods` or Open Food Facts results. Applying a default inserts one `food_log_entries` row per template item, with the same amount, portion label, saved cooking method, added fat (from the item's amount and the current `food_preferences`) and nutrition snapshots as adding a comida from the "Comidas" tab (US-21).
- The assignment's meal slot is authoritative; the template's suggested `meal_slot_id` is ignored. One saved comida may be assigned to several meals and days.
- `meal_defaults` stores one row per `(user_id, meal_slot_id, weekday)`, unique, so a meal can have different comidas on different weekdays but never two on the same day. Weekday is ISO (Lunes = 1 … Domingo = 7).
- `save_meal_defaults(p_defaults jsonb)` replaces all assignments for the caller atomically (`[{meal_slot_id, template_id, weekdays:[1,3,5]}]`). It validates ownership, weekday range/duplicates and rejects empty saved comidas.
- `apply_meal_defaults(p_log_date date)` is idempotent through `diary_default_applications (user_id, log_date)`. It runs on the first load of **today's** Diary, before the entries are read. If the day already has entries, it only records the marker. It inserts the matching defaults in one transaction and returns the number of entries added.
- The client sends the device-local date; the server derives the ISO weekday. There is no midnight job and no backfill: if the app is not opened on a selected day, that day stays empty.
- If the apply call fails, the Diary still loads and shows a non-blocking message with "Reintentar"; the RPC can be retried safely. Configuring defaults after today's Diary was already opened applies from the next day.
- The defaults screen lives in the meals editor (`Perfil → Preferencias → Comidas`) as a secondary button. It has its own "Guardar" and the usual discard-on-back behaviour; it is disabled while the meal structure has unsaved changes. The day picker preselects every available day; days already assigned to another comida for that meal are disabled and name the current comida. Each assigned row has a remove icon; removing every assignment and saving clears the defaults (`[]`).
- Loading, empty, error and offline states follow the design system; the saved-comida picker shows name, number of foods and total kcal. If a meal has all seven days assigned, "Añadir comida" is disabled and explains why.

---

## US-52 · Change the unit of an unused manual food
**As** a user who picked the wrong unit when creating a food
**I want** to change `g`/`ml` while the food is not used anywhere
**So that** I can fix the mistake in the app instead of the database.

```gherkin
Feature: Unit of a manual food (ADR-62)

  Scenario: Changing the unit of an unused food
    Given the manual food "Patata" with unit "ml" and no diary entry or saved comida using it
    When the user opens "Editar", changes the unit to "g" and taps "Guardar"
    Then "Patata" is per 100 g, with the same nutrition values and portion amounts

  Scenario: A used food keeps its unit
    Given the manual food "Patata" is in a diary entry or in a saved comida
    When the user opens "Editar"
    Then the unit control is disabled with a hint explaining why

  Scenario: Favourites do not block the change
    Given the manual food "Patata" is only a favourite
    When the user opens "Editar"
    Then the unit can be changed

  Scenario: The server refuses a stale change
    Given the food was added to a diary entry from another device after the form opened
    When the user taps "Guardar" with a different unit
    Then the change is refused, nothing is saved and the user sees why

  Scenario: Open Food Facts foods
    Given an Open Food Facts food
    Then it has no "Editar" and its unit cannot be changed
```

**Technical notes:** new migration extending `update_personal_food` with the unit and the "unreferenced" check (entries, comida items), with pgTAP; the draft survives leaving the app (ADR-45).

---

## US-53 · A comida opens straight into its editable details
**As** a user adding a saved comida
**I want** tapping it to open its details, where I can change it right there and add it
**So that** I never add one by accident and I do not need a separate "Editar comida" screen.

```gherkin
Feature: Comida details (ADR-62, ADR-63)

  Scenario: Tapping a comida opens its details
    Given the user is in "Añadir comida" → "Comidas"
    When they tap the row "Desayuno A"
    Then they see its details: the name, each food with its amount, and the totals
    And nothing has been added to the diary
    And there is no "Editar" button

  Scenario: Editing in place
    Given the details of "Desayuno A" are open
    When the user changes the name, changes an amount, removes a food or adds a food
    Then the totals follow the change
    And nothing is stored until they tap "Guardar"

  Scenario: Adding from the details
    Given the details of "Desayuno A" are open
    When the user taps "Añadir"
    Then its foods, as shown on the screen, are added to the diary in the selected meal and day
    And the saved comida does not change unless they tapped "Guardar"

  Scenario: Saving
    Given the user edited the details of "Desayuno A"
    When they tap "Guardar"
    Then the changes are stored in a single request (US-47) and the screen closes

  Scenario: No pencil on the row
    Then a row of "Comidas" has no pencil and no trailing add button

  Scenario: State survives leaving the app
    Given the user edited the details of a comida without saving
    When the user leaves the app and comes back
    Then the same details are shown, with the unsaved edits
```

**Technical notes:** replaces the T-102b row behaviour; removes the US-47 exception in `05-design-system.md` §8; there is no separate "Editar comida" screen, the details are the editor (US-21, US-47 reused). "Añadir" logs the draft without storing it (ADR-63); the offline rule for adding and saving stays as today.

---

## US-54 · Estimate the amount of a chosen food from a photo
**As** a user
**I want** to photograph the portion of a food I already chose and get its estimated weight
**So that** I don't have to weigh it or guess the grams.

```gherkin
Feature: Amount estimate from a photo

  Scenario: Photo icon next to the amount
    Given the user opens "Añadir comida" for "Pechuga de pollo" (base unit g)
    Then a photo icon with the description "Estimar cantidad con foto" is shown next to the amount field

  Scenario: The estimate prefills only the amount
    When they tap the icon, choose "Hacer foto" or "Elegir de la galería" and tap "Usar foto"
    Then they see "Estimando…" while the AI responds
    And the amount field shows the estimated weight, for example "180"
    And the food, the meal and the day are unchanged
    And the nutrition preview updates for 180 g
    And nothing has been added to the diary yet

  Scenario: The estimate is editable
    Given the amount was prefilled with "180"
    When the user changes it to "150"
    Then "150" is kept and used by "Añadir"

  Scenario: A named portion was selected
    Given the portion "1 ración" is selected
    When the estimate arrives
    Then the selector switches to the base unit and the amount shows the estimate

  Scenario: Drinks use millilitres
    Given the food is "Leche" (base unit ml)
    When the estimate arrives
    Then the amount is in ml

  Scenario: Cooking is chosen separately
    Given the estimate is the weight of the food as shown
    Then the cooking method is not changed by the estimate and the user chooses it afterwards

  Scenario: Failure keeps the amount
    Given the amount is "100" and the AI is unavailable, over quota or the photo is unreadable
    Then "No hemos podido estimar la cantidad" is shown with "Reintentar"
    And the amount stays "100"

  Scenario: No photo is kept
    When the estimate finishes, fails or is cancelled
    Then the photo is removed from the app cache and nothing is stored in the database or in Storage

  Scenario: Offline
    Given there is no connection
    Then the photo icon is disabled and the offline message is shown

  Scenario: Leaving the app
    Given the amount was prefilled
    When the user leaves the app and comes back
    Then the sheet shows the same amount and a photo not yet sent is not restored
```

**Technical notes (ADR-64)**
- Same `food-photo` function, new mode `weight`: the request adds `food {name, brand?, base_unit}` (sent as data; kcal and macros dropped by ADR-72); the response is `{estimate:{estimated_amount (always a number > 0), base_unit, confidence (integer 1-10)}}` (ADR-72, T-131). No comment is allowed. The icon appears only in "Añadir comida" (`AddFoodSheet`), not in the diary entry editor or the comida item sheet.
- Dish mode (US-48) now returns `0` for a missing/`null` macro or micronutrient; label and barcode keep `null` ("the rest stays empty").

---

## Epic Definition of Done
- `food-photo` is covered by `deno test` with recorded Gemini fixtures (dish, label, barcode, unreadable, invalid input, provider error); CI never calls the network.
- pgTAP covers `food_preferences`, `meal_defaults`, `diary_default_applications`, RLS isolation, the unique meal/weekday constraint, cascades, `save_meal_template` cooking validation, `save_meal_defaults` validation and `apply_meal_defaults` idempotency (including no re-add after deleting entries).
- Domain tests cover the cooking calculations and the weekday/selection helpers; host (Robolectric) tests cover the camera/gallery sources, the photo modes, the prefilled manual form, the cooking selector (add-to-meal, save-as-comida and edit-comida) and the default-comidas screen.
- No food photo, comment or provider response is stored or logged; the Gemini key exists only in Supabase secrets.
- Every new screen has loading, empty, error and offline states; the photo and defaults flows survive leaving the app and coming back where a draft exists.
- Unit change of unused manual foods (US-52) is covered by pgTAP (referenced vs unreferenced) and host tests; the comida details (US-53) by host tests.
- `prodDebug` APK validated by the user. Tag `v0.7.0`.
