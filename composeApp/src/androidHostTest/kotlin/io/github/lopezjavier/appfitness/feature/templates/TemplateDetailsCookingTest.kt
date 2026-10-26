package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun aRawSolidFood_showsNoMethodInItsRow_andADrinkNeverDoes() {
        show()

        rule.assertRowText("template_edit_item_0", "120 g")
        rule.onNodeWithText("Crudo", substring = true).assertDoesNotExist()
        rule.assertRowText("template_edit_item_1", "200 ml")
    }

    @Test
    fun tappingASolidFood_opensTheFoodSheet_withTheSelectorOnRaw() {
        show()

        tap("template_edit_item_0")

        rule.onNodeWithTag("food_sheet_cooking_raw").assertIsSelected()
        rule.onNodeWithTag("food_sheet_cooking_griddle").assertIsNotSelected()
        rule.onNodeWithTag("food_sheet_cooking_impact").assertDoesNotExist()
    }

    @Test
    fun aDrinkSheet_hasNoSelector() {
        show()

        tap("template_edit_item_1")

        rule.onNodeWithTag("food_sheet_commit").assertIsDisplayed()
        rule.onNodeWithTag("food_sheet_cooking_raw").assertDoesNotExist()
    }

    @Test
    fun choosingGriddle_showsTheImpactLine_andGuardarAppliesItToTheRowAndTheTotals() {
        show()
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("220 kcal")

        tap("template_edit_item_0")
        tap("food_sheet_cooking_griddle")
        rule.onNodeWithTag("food_sheet_cooking_impact").assertTextEquals("+10,8 kcal y +1,2 g de grasa")
        tap("food_sheet_commit")

        rule.assertRowText("template_edit_item_0", "A la plancha")
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("231 kcal")
        rule.onNodeWithTag("template_edit_save").assertIsEnabled()
    }

    @Test
    fun closingTheSheet_discardsThePendingMethod() {
        show()

        tap("template_edit_item_0")
        tap("food_sheet_cooking_fried")
        tap("food_sheet_close")

        rule.onNodeWithText("Crudo", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("template_edit_kcal").assertTextContains("220 kcal")
        rule.onNodeWithTag("template_edit_save").assertIsNotEnabled()
        tap("template_edit_item_0")
        rule.onNodeWithTag("food_sheet_cooking_raw").assertIsSelected()
    }

    @Test
    fun guardar_storesTheMethods_andTheReadOnlyAnadirLogsThemWithTheAddedFat() {
        show()
        tap("template_edit_item_0")
        tap("food_sheet_cooking_griddle")
        tap("food_sheet_commit")

        tap("template_edit_save")

        assertEquals(
            listOf(CookingMethod.Griddle, CookingMethod.Raw),
            templates.templates.single().items.map { it.cookingMethod },
        )
        tap("template_details_primary")
        val batch = diary.addedBatches.single()
        assertEquals(listOf(CookingMethod.Griddle, CookingMethod.Raw), batch.map { it.cookingMethod })
        assertEquals(1.2, batch.first().cookingOilRetainedG, 0.0)
    }

    @Test
    @Config(qualifiers = "w360dp-h2400dp-xxhdpi", fontScale = 2.0f)
    fun atLargeFonts_theRowStillShowsAndOpensTheSheet() {
        show()

        rule.onNodeWithTag("template_edit_item_0").assertIsDisplayed()
        tap("template_edit_item_0")
        rule.onNodeWithTag("food_sheet_cooking_raw").assertIsDisplayed()
    }

    @Test
    fun rowMethods_surviveRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { DetailsHost(detailsModel(templates, connectivity, saved, diary = diary)) }
        rule.waitForIdle()
        tap("template_edit_item_0")
        tap("food_sheet_cooking_air_fryer")
        tap("food_sheet_commit")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.assertRowText("template_edit_item_0", "Air fryer")
        rule.onNodeWithTag("template_edit_save").assertIsEnabled()
    }

    @Test
    fun theOpenSheetsPendingMethod_survivesRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { DetailsHost(detailsModel(templates, connectivity, saved, diary = diary)) }
        rule.waitForIdle()
        tap("template_edit_item_0")
        tap("food_sheet_cooking_fried")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_cooking_fried").assertIsSelected()
        rule.onNodeWithTag("food_sheet_cooking_impact").assertTextEquals("+86,4 kcal y +9,6 g de grasa")
    }
}
