package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.usecase.BuildTemplateItems
import io.github.lopezjavier.appfitness.domain.usecase.ValidateTemplateName
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.food
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-50 "Saving a meal with cooking": the save-as-comida sheet shows a method chip per solid food (none for a drink),
 * opens the selector under one row at a time, and stores the chosen methods with the comida.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class SaveTemplateCookingTest {
    @get:Rule
    val rule = createComposeRule()

    private val day = LocalDate(2026, 10, 19)
    private val diary = FakeFoodDiaryRepository()
    private val templates = FakeMealTemplateRepository(
        mutableMapOf("chicken" to food("chicken", "Pollo"), "rice" to food("rice", "Arroz")),
    )
    private val connectivity = FakeConnectivityObserver()

    init {
        diary.server[day] = listOf(
            entry("e1", "chicken", "Pollo", "g", CookingMethod.Griddle),
            entry("e2", "rice", "Arroz", "g", CookingMethod.Raw),
            entry("e3", "juice", "Zumo", "ml", CookingMethod.Raw),
        )
        templates.foods["juice"] = food("juice", "Zumo", baseUnit = "ml")
    }

    private fun entry(id: String, foodId: String, name: String, unit: String, method: CookingMethod) = FoodLogEntry(
        id = id,
        logDate = day,
        mealSlotId = "lunch",
        foodId = foodId,
        foodName = name,
        amount = 150.0,
        portionLabel = null,
        baseUnit = unit,
        kcal = 100.0,
        proteinG = 10.0,
        carbsG = 10.0,
        fatG = 5.0,
        cookingMethod = method,
    )

    private fun model(saved: SavedStateHandle) = SaveTemplateViewModel(
        slotId = "lunch",
        date = day,
        foodDiary = diary,
        buildTemplateItems = BuildTemplateItems(),
        validateTemplateName = ValidateTemplateName(),
        mealTemplates = templates,
        connectivity = connectivity,
        savedState = saved,
    )

    @Composable
    private fun SheetHost(saved: SavedStateHandle) {
        val viewModel = remember { model(saved) }
        val state by viewModel.uiState.collectAsState()
        AppTheme { SaveTemplateSheet(state, viewModel::onEvent, onDismiss = {}) }
    }

    private fun show(saved: SavedStateHandle = SavedStateHandle()) {
        rule.setContent { SheetHost(saved) }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun eachSolidFood_hasAChipWithItsOwnMethod_andADrinkHasNone() {
        show()

        rule.onNodeWithTag("save_template_cooking_chip_e1").assertTextContains("A la plancha")
        rule.onNodeWithTag("save_template_cooking_chip_e1")
            .assertContentDescriptionEquals("Cocinado: A la plancha. Cambiar")
        rule.onNodeWithTag("save_template_cooking_chip_e2").assertTextContains("Crudo")
        rule.onNodeWithTag("save_template_cooking_chip_e3").assertDoesNotExist()
    }

    @Test
    fun theChip_hasATouchTargetOfAtLeast48dp() {
        show()

        rule.onNodeWithTag("save_template_cooking_chip_e1").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun tappingAChip_expandsTheSelectorUnderThatRow_andOnlyOneAtATime() {
        show()
        rule.onNodeWithTag("save_template_cooking_raw").assertDoesNotExist()

        tap("save_template_cooking_chip_e1")
        rule.onNodeWithTag("save_template_cooking_griddle").assertIsSelected()

        tap("save_template_cooking_chip_e2")
        rule.onNodeWithTag("save_template_cooking_raw").assertIsSelected()
        tap("save_template_cooking_chip_e2")
        rule.onNodeWithTag("save_template_cooking_raw").assertDoesNotExist()
    }

    @Test
    fun choosingMethods_andSaving_storesThemWithTheComida() {
        show()
        tap("save_template_cooking_chip_e2")
        tap("save_template_cooking_fried")
        rule.onNodeWithTag("save_template_name_input").performTextReplacement("Comida A")
        rule.waitForIdle()

        tap("save_template_name_commit")

        val saved = templates.templates.single()
        assertEquals(
            listOf(CookingMethod.Griddle, CookingMethod.Fried, CookingMethod.Raw),
            saved.items.map { it.cookingMethod },
        )
    }

    @Test
    fun chosenMethodAndExpandedRow_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(saved) }
        rule.waitForIdle()
        tap("save_template_cooking_chip_e2")
        tap("save_template_cooking_air_fryer")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("save_template_cooking_chip_e2").assertTextContains("Air fryer")
        rule.onNodeWithTag("save_template_cooking_air_fryer").assertIsSelected()
    }
}
