package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.food.food
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-50 "Cooking in a saved comida" on the comida details: a method chip per solid food (none for a drink), the
 * selector in the food sheet applied only by its "Guardar", totals with the oil, and "Añadir" logging the methods.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TemplateDetailsCookingTest {
    @get:Rule
    val rule = createComposeRule()

    private val eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
    private val juice = food("juice", "Zumo", kcal = 50.0, baseUnit = "ml")
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to eggs, "juice" to juice))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()

    init {
        templates.templates += MealTemplate(
            "t1",
            "Desayuno A",
            "breakfast",
            listOf(MealTemplateItem(eggs, 120.0, null), MealTemplateItem(juice, 200.0, null)),
        )
    }

    private fun show(saved: SavedStateHandle = SavedStateHandle()) {
        rule.setContent { DetailsHost(detailsModel(templates, connectivity, saved, diary = diary)) }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun aSolidFood_hasARawChip_andADrinkHasNone() {
        show()

        rule.onNodeWithTag("template_details_cooking_chip_0").assertTextContains("Crudo")
        rule.onNodeWithTag("template_details_cooking_chip_0").assertContentDescriptionEquals("Cocinado: Crudo. Cambiar")
        rule.onNodeWithTag("template_details_cooking_chip_1").assertDoesNotExist()
    }

    @Test
    fun theChip_hasATouchTargetOfAtLeast48dp() {
        show()

        rule.onNodeWithTag("template_details_cooking_chip_0").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun theChip_opensTheFoodSheet_withTheSelectorOnRaw() {
        show()

        tap("template_details_cooking_chip_0")

        rule.onNodeWithTag("template_details_food_cooking_raw").assertIsSelected()
        rule.onNodeWithTag("template_details_food_cooking_griddle").assertIsNotSelected()
        rule.onNodeWithTag("template_details_food_cooking_impact").assertDoesNotExist()
    }

    @Test
    fun aDrinkSheet_hasNoSelector() {
        show()

        tap("template_details_item_1")

        rule.onNodeWithTag("template_food_sheet_save").assertIsDisplayed()
        rule.onNodeWithTag("template_details_food_cooking_raw").assertDoesNotExist()
    }

    @Test
    fun choosingGriddle_showsTheImpactLine_andGuardarAppliesItToTheRowAndTheTotals() {
        show()
        rule.onNodeWithTag("template_details_kcal").assertTextContains("220 kcal")

        tap("template_details_cooking_chip_0")
        tap("template_details_food_cooking_griddle")
        rule.onNodeWithTag("template_details_food_cooking_impact").assertTextEquals("+22,5 kcal y +2,5 g de grasa")
        tap("template_food_sheet_save")

        rule.onNodeWithTag("template_details_cooking_chip_0").assertTextContains("A la plancha")
        rule.onNodeWithTag("template_details_kcal").assertTextContains("243 kcal")
        rule.onNodeWithTag("template_details_unsaved_hint").assertIsDisplayed()
    }

    @Test
    fun closingTheSheet_discardsThePendingMethod() {
        show()

        tap("template_details_cooking_chip_0")
        tap("template_details_food_cooking_fried")
        tap("template_food_sheet_close")

        rule.onNodeWithTag("template_details_cooking_chip_0").assertTextContains("Crudo")
        rule.onNodeWithTag("template_details_kcal").assertTextContains("220 kcal")
        rule.onNodeWithTag("template_details_unsaved_hint").assertDoesNotExist()
        tap("template_details_cooking_chip_0")
        rule.onNodeWithTag("template_details_food_cooking_raw").assertIsSelected()
    }

    @Test
    fun anadir_logsTheFoodsWithTheirMethods_andTheOil() {
        show()
        tap("template_details_cooking_chip_0")
        tap("template_details_food_cooking_griddle")
        tap("template_food_sheet_save")

        tap("template_details_add")

        val batch = diary.addedBatches.single()
        assertEquals(listOf(CookingMethod.Griddle, CookingMethod.Raw), batch.map { it.cookingMethod })
        assertEquals(2.5, batch.first().cookingOilRetainedG, 0.0)
    }

    @Test
    @Config(qualifiers = "w360dp-h2400dp-xxhdpi", fontScale = 2.0f)
    fun atLargeFonts_theChipStillShowsAndOpensTheSheet() {
        show()

        rule.onNodeWithTag("template_details_cooking_chip_0").assertIsDisplayed()
        tap("template_details_cooking_chip_0")
        rule.onNodeWithTag("template_details_food_cooking_raw").assertIsDisplayed()
    }

    @Test
    fun rowMethods_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { DetailsHost(detailsModel(templates, connectivity, saved, diary = diary)) }
        rule.waitForIdle()
        tap("template_details_cooking_chip_0")
        tap("template_details_food_cooking_air_fryer")
        tap("template_food_sheet_save")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_cooking_chip_0").assertTextContains("Air fryer")
        rule.onNodeWithTag("template_details_unsaved_hint").assertIsDisplayed()
    }

    @Test
    fun theOpenSheetsPendingMethod_survivesRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { DetailsHost(detailsModel(templates, connectivity, saved, diary = diary)) }
        rule.waitForIdle()
        tap("template_details_cooking_chip_0")
        tap("template_details_food_cooking_fried")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("template_details_food_cooking_fried").assertIsSelected()
        rule.onNodeWithTag("template_details_food_cooking_impact").assertTextEquals("+45 kcal y +5 g de grasa")
    }
}
