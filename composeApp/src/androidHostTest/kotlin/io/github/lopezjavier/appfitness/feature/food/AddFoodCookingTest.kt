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

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun sheetStartsOnCrudo_withTheFourOptions_andNoImpactLine() {
        show()

        rule.onNodeWithTag("add_food_cooking_raw").assertTextEquals("Crudo").assertIsSelected()
        rule.onNodeWithTag("add_food_cooking_griddle").assertTextEquals("A la plancha")
        rule.onNodeWithTag("add_food_cooking_air_fryer").assertTextEquals("Air fryer")
        rule.onNodeWithTag("add_food_cooking_fried").assertTextEquals("Frito")
        rule.onNodeWithTag("add_food_cooking_impact").assertDoesNotExist()
    }

    @Test
    fun griddle_showsTheOilImpact_andTheTotalsInclude() {
        show()

        tap("add_food_cooking_griddle")

        rule.onNodeWithTag("add_food_cooking_impact").assertTextEquals("+22,5 kcal y +2,5 g de grasa")
        rule.onNodeWithTag("add_food_preview_kcal", useUnmergedTree = true).assertTextEquals("133 kcal")
    }

    @Test
    fun airFryer_showsItsImpact() {
        show()

        tap("add_food_cooking_air_fryer")

        rule.onNodeWithTag("add_food_cooking_impact").assertTextEquals("+45 kcal y +5 g de grasa")
    }

    @Test
    fun fried_showsItsImpact() {
        show()

        tap("add_food_cooking_fried")

        rule.onNodeWithTag("add_food_cooking_impact").assertTextEquals("+45 kcal y +5 g de grasa")
    }

    @Test
    fun backToCrudo_removesTheImpactLine() {
        show()
        tap("add_food_cooking_fried")

        tap("add_food_cooking_raw")

        rule.onNodeWithTag("add_food_cooking_impact").assertDoesNotExist()
        rule.onNodeWithTag("add_food_preview_kcal", useUnmergedTree = true).assertTextEquals("110 kcal")
    }

    @Test
    fun customOilAmount_changesTheImpact() {
        preferences.result = Result.success(FoodPreferences(10.0, 5.0, 50.0))
        show()

        tap("add_food_cooking_griddle")

        rule.onNodeWithTag("add_food_cooking_impact").assertTextEquals("+45 kcal y +5 g de grasa")
    }

    @Test
    fun drink_hasNoCookingSelector() {
        show(milk)

        rule.onNodeWithTag("add_food_cooking_raw").assertDoesNotExist()
        rule.onNodeWithTag("add_food_cooking_impact").assertDoesNotExist()
    }

    @Test
    fun preferencesReadFailure_hidesTheSelector_butStillLogsRaw() {
        preferences.result = Result.failure(AppException(AppError.Network))
        show()

        rule.onNodeWithTag("add_food_cooking_raw").assertDoesNotExist()
        tap("add_food_submit")

        assertEquals(CookingMethod.Raw, diary.added.single().cookingMethod)
    }

    @Test
    fun anadir_logsTheCookedEntry_withMethodRetainedOilAndAdjustedSnapshot() {
        show()
        tap("add_food_cooking_griddle")

        tap("add_food_submit")

        val entry = diary.added.single()
        assertEquals(CookingMethod.Griddle, entry.cookingMethod)
        assertEquals(2.5, entry.cookingOilRetainedG, 0.0)
        assertEquals(132.5, entry.snapshot.kcal, 0.001)
        assertEquals(7.5, entry.snapshot.fatG, 0.001)
    }

    @Test
    fun chosenMethod_survivesRecreation() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(model(chicken, saved)) }
        rule.waitForIdle()
        tap("add_food_cooking_air_fryer")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_cooking_air_fryer").assertIsSelected()
        rule.onNodeWithTag("add_food_cooking_impact").assertTextEquals("+45 kcal y +5 g de grasa")
    }
}
