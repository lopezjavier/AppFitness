package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-47: editing a manual food from its details sheet, with the real view models and fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class FoodEditScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val saved = mutableListOf<String>()
    private val tarrina = FoodPortion("p1", "1 tarrina", 125.0)

    private fun manualYogurt(portions: List<FoodPortion> = listOf(tarrina)) {
        foods.known["yogurt"] = food("yogurt", "Yogur casero", kcal = 60.0, portions = portions, isManual = true)
    }

    private fun editModel() = CreateFoodViewModel(null, null, foods, connectivity, SavedStateHandle(), "yogurt")

    private fun sheetModel(id: String = "yogurt") = AddFoodViewModel(
        foodId = id,
        initialSlotId = null,
        date = LocalDate(2026, 10, 19),
        foodRepository = foods,
        foodDiary = FakeFoodDiaryRepository(),
        mealSlots = FakeMealSlotRepository(),
        resolvePortion = ResolvePortion(),
        scaleNutrition = ScaleNutrition(),
        validateQuantity = ValidateQuantity(),
        defaultMealSlot = DefaultMealSlot(),
        clock = MutableClock(),
        connectivity = connectivity,
        savedState = SavedStateHandle(),
    )

    @Composable
    private fun Form(model: CreateFoodViewModel, onSave: () -> Unit = {}) {
        val state by model.uiState.collectAsState()
        val currentOnSave by rememberUpdatedState(onSave)
        LaunchedEffect(model) {
            model.effects.collect {
                if (it is CreateFoodEffect.Saved) {
                    saved += it.foodId
                    currentOnSave()
                }
            }
        }
        AppTheme { CreateFoodScreen(state, model::onEvent) }
    }

    private fun showForm(model: CreateFoodViewModel = editModel()) {
        rule.setContent { Form(model) }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun replace(tag: String, text: String) {
        rule.onNodeWithTag(tag).performTextReplacement(text)
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test
    fun editForm_isPrefilledFromTheStoredFood_andTheUnitCannotChange() {
        manualYogurt()
        foods.storedMicronutrients["yogurt"] = mapOf(MicronutrientKind.Sugars to 4.7)
        showForm()

        rule.onNodeWithTag("create_food_name_field").assertTextContains("Yogur casero")
        rule.onNodeWithTag("create_food_kcal_field").assertTextContains("60")
        rule.onNodeWithTag("create_food_nutrient_sugars").assertTextContains("4,7")
        rule.onNodeWithTag("create_food_portion_label_0").assertTextContains("1 tarrina")
        rule.onNodeWithTag("create_food_portion_amount_0").assertTextContains("125")
        rule.onNodeWithTag("create_food_unit_locked").assertTextContains("La unidad no se puede cambiar")
        rule.onNodeWithTag("create_food_unit_ml").assertIsNotEnabled()
    }

    @Test
    fun editingAManualFood_savesTheNewValues_andReportsTheFood() {
        manualYogurt()
        showForm()

        replace("create_food_kcal_field", "70")
        tap("create_food_save_button")

        assertEquals(70.0, foods.updates.single().nutrition.kcal, 0.0)
        assertEquals(listOf("yogurt"), saved)
    }

    @Test
    fun addingAPortion_sendsItWithTheExistingOnes() {
        manualYogurt(portions = emptyList())
        showForm()

        tap("create_food_add_portion_button")
        rule.onNodeWithTag("create_food_portion_label_0").performTextInput("1 tarrina")
        rule.onNodeWithTag("create_food_portion_amount_0").performTextInput("125")
        tap("create_food_save_button")

        assertEquals(listOf(NewPortion("1 tarrina", 125.0)), foods.updates.single().portions)
    }

    @Test
    fun editingAPortion_sendsTheNewAmount() {
        manualYogurt()
        showForm()

        replace("create_food_portion_amount_0", "120")
        tap("create_food_save_button")

        assertEquals(listOf(NewPortion("1 tarrina", 120.0)), foods.updates.single().portions)
    }

    @Test
    fun removingAPortion_sendsNoPortions() {
        manualYogurt()
        showForm()

        tap("create_food_portion_remove_0")
        tap("create_food_save_button")

        assertEquals(emptyList<NewPortion>(), foods.updates.single().portions)
    }

    @Test
    fun fullLabel_fieldsFollowTheLabelOrder_andTheSectionsAreCollapsedUntilOpened() {
        manualYogurt()
        showForm()

        val order = listOf(
            "create_food_kcal_field",
            "create_food_protein_field",
            "create_food_carbs_field",
            "create_food_fat_field",
            "create_food_nutrient_saturated_fat",
            "create_food_nutrient_sugars",
            "create_food_nutrient_fiber",
            "create_food_nutrient_salt",
        )
        val tops = order.map(::top)
        assertTrue("macros then the usual label rows, top to bottom: $tops", tops == tops.sorted())
        rule.onNodeWithTag("create_food_nutrient_vitamin_c").assertDoesNotExist()
        rule.onNodeWithTag("create_food_nutrient_calcium").assertDoesNotExist()
        rule.onNodeWithTag("create_food_nutrient_polyols").assertDoesNotExist()
    }

    @Test
    fun fullLabel_vitaminsAndMineralsAreReachableAndSavedInGrams() {
        manualYogurt()
        showForm()

        tap("create_food_section_vitamins_toggle")
        rule.onNodeWithTag("create_food_nutrient_vitamin_c").performTextInput("12")
        tap("create_food_section_minerals_toggle")
        rule.onNodeWithTag("create_food_nutrient_selenium").performTextInput("55")
        tap("create_food_section_fats_toggle")
        rule.onNodeWithTag("create_food_nutrient_polyols").performTextInput("1,5")
        rule.waitForIdle()
        tap("create_food_save_button")

        val values = foods.updates.single().micronutrients.values
        assertEquals(12.0, values.getValue(MicronutrientKind.VitaminC), 0.0)
        assertEquals(55.0, values.getValue(MicronutrientKind.Selenium), 0.0)
        assertEquals(1.5, values.getValue(MicronutrientKind.Polyols), 0.0)
        rule.onNodeWithTag("create_food_section_vitamins_filled", useUnmergedTree = true)
            .assertTextContains("Rellenos: 1")
    }

    @Test
    fun leavingWithoutGuardar_writesNothing() {
        manualYogurt()
        showForm()

        replace("create_food_kcal_field", "99")

        assertTrue(foods.updates.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun loadFailure_showsRetry_andRetryFillsTheForm() {
        manualYogurt()
        foods.editableFailures = 1
        showForm()
        rule.onNodeWithTag("create_food_retry").assertExists()
        rule.onNodeWithTag("create_food_name_field").assertDoesNotExist()

        tap("create_food_retry_action")

        rule.onNodeWithTag("create_food_name_field").assertTextContains("Yogur casero")
    }

    @Test
    fun offline_disablesGuardar() {
        manualYogurt()
        connectivity.state.value = false
        showForm()

        rule.onNodeWithTag("create_food_save_button").assertIsNotEnabled()
        rule.onNodeWithTag("create_food_offline_hint").assertExists()
    }

    @Test
    fun saveButton_isEnabledForAnUnchangedLoadedFood() {
        manualYogurt()
        showForm()

        rule.onNodeWithTag("create_food_save_button").assertIsEnabled()
    }

    @Test
    fun manualFood_offersEditInTheSheet_butAnOffFoodDoesNot() {
        manualYogurt()
        foods.known["off"] = food("off", "Yogur OFF")
        val manual = sheetModel()
        rule.setContent {
            val state by manual.uiState.collectAsState()
            AppTheme { AddFoodSheet(state, manual::onEvent, {}) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("add_food_edit").assertIsEnabled()
    }

    @Test
    fun offFood_hasNoEditAction() {
        foods.known["off"] = food("off", "Yogur OFF")
        val model = sheetModel("off")
        rule.setContent {
            val state by model.uiState.collectAsState()
            AppTheme { AddFoodSheet(state, model::onEvent, {}) }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_name").assertExists()
        rule.onNodeWithTag("add_food_edit").assertDoesNotExist()
    }

    @Test
    fun offline_keepsEditVisibleButDisabled() {
        manualYogurt()
        connectivity.state.value = false
        val model = sheetModel()
        rule.setContent {
            val state by model.uiState.collectAsState()
            AppTheme { AddFoodSheet(state, model::onEvent, {}) }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_edit").assertIsNotEnabled()
    }

    @Composable
    private fun SheetAndForm(sheet: AddFoodViewModel, form: () -> CreateFoodViewModel) {
        var editing by remember { mutableStateOf(false) }
        if (editing) {
            val model = remember { form() }
            Form(model) {
                editing = false
                sheet.onEvent(AddFoodEvent.Refresh)
            }
        } else {
            val state by sheet.uiState.collectAsState()
            AppTheme { AddFoodSheet(state, sheet::onEvent, {}, onEdit = { editing = true }) }
        }
    }

    @Test
    fun editFromTheSheet_andGuardar_showsTheNewValuesInTheSheet() {
        manualYogurt()
        rule.setContent { SheetAndForm(sheetModel(), ::editModel) }
        rule.waitForIdle()
        rule.onNodeWithTag("add_food_label_row_energy").assertTextContains("60 kcal", substring = true)

        tap("add_food_edit")
        replace("create_food_kcal_field", "70")
        tap("create_food_save_button")

        rule.onNodeWithTag("create_food_screen").assertDoesNotExist()
        rule.onNodeWithTag("add_food_label_row_energy").assertTextContains("70 kcal", substring = true)
    }

    @Test
    fun editFromTheSheet_andLeavingWithoutGuardar_keepsTheValues() {
        manualYogurt()
        rule.setContent { SheetAndForm(sheetModel(), ::editModel) }
        rule.waitForIdle()

        tap("add_food_edit")
        replace("create_food_kcal_field", "99")

        assertTrue(foods.updates.isEmpty())
        assertEquals(60.0, foods.known.getValue("yogurt").nutrition.kcal, 0.0)
    }
}
