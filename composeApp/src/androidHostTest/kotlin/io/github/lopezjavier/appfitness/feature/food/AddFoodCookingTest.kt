package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-50 on the add-to-meal sheet: the cooking selector, its impact line and the cooked entry that gets logged. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class AddFoodCookingTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val diary = FakeFoodDiaryRepository()
    private val preferences = FakeFoodPreferencesRepository()
    private val chicken = food("chicken", "Pechuga de pollo", kcal = 110.0)
    private val milk = food("milk", "Leche", kcal = 45.0, baseUnit = "ml")

    private fun model(food: Food, state: SavedStateHandle = SavedStateHandle()): AddFoodViewModel {
        foods.known[food.id] = food
        return AddFoodViewModel(
            foodId = food.id,
            initialSlotId = "breakfast",
            date = LocalDate(2026, 10, 19),
            foodRepository = foods,
            foodDiary = diary,
            mealSlots = FakeMealSlotRepository(),
            foodPreferencesRepository = preferences,
            resolvePortion = ResolvePortion(),
            scaleNutrition = ScaleNutrition(),
            validateQuantity = ValidateQuantity(),
            defaultMealSlot = DefaultMealSlot(),
            clock = MutableClock(),
            connectivity = FakeConnectivityObserver(),
            savedState = state,
        )
    }

    @Composable
    private fun SheetHost(model: AddFoodViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { AddFoodSheet(state, model::onEvent, {}) }
    }

    private fun show(food: Food = chicken) {
        val model = model(food)
        rule.setContent { SheetHost(model) }
        rule.waitForIdle()
    }

    private fun typeAmount(text: String) {
        rule.onNodeWithTag("food_sheet_amount").performScrollTo().performTextClearance()
        rule.onNodeWithTag("food_sheet_amount").performTextInput(text)
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        // The pinned "Añadir" sits outside the scrolling body: it is always reachable.
        val node = rule.onNodeWithTag(tag)
        if (tag != "food_sheet_commit") node.performScrollTo()
        node.performClick()
        rule.waitForIdle()
    }

    @Test
    fun sheetStartsOnCrudo_withTheFourOptions_andNoImpactLine() {
        show()

        rule.onNodeWithTag("food_sheet_cooking_raw").assertTextEquals("Crudo").assertIsSelected()
        rule.onNodeWithTag("food_sheet_cooking_griddle").assertTextEquals("A la plancha")
        rule.onNodeWithTag("food_sheet_cooking_air_fryer").assertTextEquals("Air fryer")
        rule.onNodeWithTag("food_sheet_cooking_fried").assertTextEquals("Frito")
        rule.onNodeWithTag("food_sheet_cooking_impact").assertDoesNotExist()
    }

    @Test
    fun griddle_showsTheFatImpact_andTheTotalsInclude() {
        show()
        typeAmount("300")

        tap("food_sheet_cooking_griddle")

        rule.onNodeWithTag("food_sheet_cooking_impact").assertTextEquals("+27 kcal y +3 g de grasa")
        rule.onNodeWithTag("food_sheet_totals_kcal", useUnmergedTree = true).assertTextEquals("357 kcal")
    }

    @Test
    fun airFryer_showsItsImpact() {
        show()
        typeAmount("300")

        tap("food_sheet_cooking_air_fryer")

        rule.onNodeWithTag("food_sheet_cooking_impact").assertTextEquals("+54 kcal y +6 g de grasa")
    }

    @Test
    fun fried_showsItsImpact() {
        show()
        typeAmount("300")

        tap("food_sheet_cooking_fried")

        rule.onNodeWithTag("food_sheet_cooking_impact").assertTextEquals("+216 kcal y +24 g de grasa")
    }

    @Test
    fun changingTheAmount_updatesTheImpactLine() {
        show()
        typeAmount("300")
        tap("food_sheet_cooking_fried")

        typeAmount("200")

        rule.onNodeWithTag("food_sheet_cooking_impact").assertTextEquals("+144 kcal y +16 g de grasa")
    }

    @Test
    fun backToCrudo_removesTheImpactLine() {
        show()
        tap("food_sheet_cooking_fried")

        tap("food_sheet_cooking_raw")

        rule.onNodeWithTag("food_sheet_cooking_impact").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_totals_kcal", useUnmergedTree = true).assertTextEquals("110 kcal")
    }

    @Test
    fun customPercentage_changesTheImpact() {
        preferences.result = Result.success(FoodPreferences(5.0, 2.0, 8.0))
        show()
        typeAmount("300")

        tap("food_sheet_cooking_griddle")

        rule.onNodeWithTag("food_sheet_cooking_impact").assertTextEquals("+135 kcal y +15 g de grasa")
    }

    @Test
    fun drink_hasNoCookingSelector() {
        show(milk)

        rule.onNodeWithTag("food_sheet_cooking_raw").assertDoesNotExist()
        rule.onNodeWithTag("food_sheet_cooking_impact").assertDoesNotExist()
    }

    @Test
    fun preferencesReadFailure_hidesTheSelector_butStillLogsRaw() {
        preferences.result = Result.failure(AppException(AppError.Network))
        show()

        rule.onNodeWithTag("food_sheet_cooking_raw").assertDoesNotExist()
        tap("food_sheet_commit")

        assertEquals(CookingMethod.Raw, diary.added.single().cookingMethod)
    }

    @Test
    fun anadir_logsTheCookedEntry_withMethodAddedFatAndAdjustedSnapshot() {
        show()
        typeAmount("300")
        tap("food_sheet_cooking_griddle")

        tap("food_sheet_commit")

        val entry = diary.added.single()
        assertEquals(CookingMethod.Griddle, entry.cookingMethod)
        assertEquals(3.0, entry.cookingOilRetainedG, 0.0)
        assertEquals(357.0, entry.snapshot.kcal, 0.001)
        assertEquals(18.0, entry.snapshot.fatG, 0.001)
    }

    @Test
    fun chosenMethod_survivesRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(model(chicken, saved)) }
        rule.waitForIdle()
        typeAmount("300")
        tap("food_sheet_cooking_air_fryer")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_cooking_air_fryer").assertIsSelected()
        rule.onNodeWithTag("food_sheet_cooking_impact").assertTextEquals("+54 kcal y +6 g de grasa")
    }
}
