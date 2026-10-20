package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
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

/** ADR-40: the search box and the open add-food sheet survive recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class FoodRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()
    private val chicken = food("chicken", "Pechuga de pollo", kcal = 110.0)
    private val egg = food("egg", "Huevo", kcal = 200.0, portions = listOf(FoodPortion("p1", "huevo", 55.0)))

    private fun searchModel(state: SavedStateHandle) = FoodSearchViewModel(foods, connectivity, state)

    private fun addModel(state: SavedStateHandle) = AddFoodViewModel(
        foodId = "egg",
        initialSlotId = null,
        date = LocalDate(2026, 10, 19),
        foodRepository = foods,
        foodDiary = diary,
        mealSlots = FakeMealSlotRepository(),
        resolvePortion = ResolvePortion(),
        scaleNutrition = ScaleNutrition(),
        validateQuantity = ValidateQuantity(),
        defaultMealSlot = DefaultMealSlot(),
        clock = MutableClock(),
        connectivity = connectivity,
        savedState = state,
    )

    @Composable
    private fun SearchHost(model: FoodSearchViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { FoodSearchScreen(state, model::onEvent) }
    }

    @Composable
    private fun SheetHost(model: AddFoodViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { AddFoodSheet(state, model::onEvent, {}) }
    }

    @Test
    fun searchQueryAndTab_surviveRecreation_withoutRepeatingTheSearch() {
        foods.recentFoods = Result.success(listOf(chicken))
        foods.favouriteFoods = Result.success(listOf(chicken))
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SearchHost(searchModel(saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_search_tab_favourites").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_tab_favourites").assertIsSelected()
        rule.onNodeWithTag("food_search_result_chicken").assertExists()
        assertEquals(emptyList<String>(), foods.searches)
    }

    @Test
    fun typedQuery_isRestoredFromTheSameSavedState() {
        foods.searchResults["pollo"] = Result.success(listOf(chicken))
        val saved = SavedStateHandle(mapOf("food_search_query" to "pollo"))
        rule.setContent { SearchHost(searchModel(saved)) }
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_input").assertTextContains("pollo")
        rule.onNodeWithTag("food_search_result_chicken").assertExists()
        assertEquals(listOf("pollo"), foods.searches)
    }

    @Test
    fun openSheet_keepsAmountPortionAndMeal_afterRecreation() {
        foods.known["egg"] = egg
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(addModel(saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("add_food_portion_option_p1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("add_food_amount").performTextReplacement("3")
        rule.onNodeWithTag("add_food_meal_snack").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_sheet").assertExists()
        rule.onNodeWithTag("add_food_portion_option_p1").assertIsSelected()
        rule.onNodeWithTag("add_food_amount").assertTextContains("3")
        rule.onNodeWithTag("add_food_meal_snack").assertIsSelected()
    }
}
