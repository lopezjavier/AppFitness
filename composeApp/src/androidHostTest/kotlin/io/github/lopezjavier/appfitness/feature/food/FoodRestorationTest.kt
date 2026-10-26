package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.usecase.DefaultMealSlot
import io.github.lopezjavier.appfitness.domain.usecase.ResolvePortion
import io.github.lopezjavier.appfitness.domain.usecase.ScaleNutrition
import io.github.lopezjavier.appfitness.domain.usecase.ValidateQuantity
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.templates.FakeMealTemplateRepository
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

    private val templates = FakeMealTemplateRepository(mutableMapOf("chicken" to chicken))

    private fun searchModel(state: SavedStateHandle) =
        foodSearchModel(foods, connectivity, FakeBarcodeScanner(), state, templates = templates)

    private fun addModel(state: SavedStateHandle) = AddFoodViewModel(
        foodId = "egg",
        initialSlotId = null,
        date = LocalDate(2026, 10, 19),
        foodRepository = foods,
        foodDiary = diary,
        mealSlots = FakeMealSlotRepository(),
        foodPreferencesRepository = FakeFoodPreferencesRepository(),
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
        AppTheme { FoodSearchScreen(state, model::onEvent, {}) }
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
        rule.onNodeWithTag("food_search_row_chicken").assertExists()
        assertEquals(emptyList<String>(), foods.searches)
    }

    @Test
    fun comidasTab_isRestoredAfterRecreation_andItsListLoads() {
        templates.templates += MealTemplate("t1", "Desayuno A", null, listOf(MealTemplateItem(chicken, 100.0, null)))
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SearchHost(searchModel(saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_search_tab_templates").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_tab_templates").assertIsSelected()
        rule.onNodeWithTag("food_search_template_t1").assertExists()
    }

    @Test
    fun typedQuery_isRestoredFromTheSameSavedState() {
        foods.searchResults["pollo"] = Result.success(listOf(chicken))
        val saved = SavedStateHandle(mapOf("food_search_query" to "pollo"))
        rule.setContent { SearchHost(searchModel(saved)) }
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_field").assertTextContains("pollo")
        rule.onNodeWithTag("food_search_row_chicken").assertExists()
        assertEquals(listOf("pollo"), foods.searches)
    }

    @Test
    fun micronutrientsSection_reappearsAfterRecreation_andTheExpandedStateIsKept() {
        foods.known["egg"] = egg
        val kinds = MicronutrientKind.entries.take(10)
        foods.micronutrientResults["egg"] = Result.success(
            Micronutrients(kinds.map { Micronutrient(it, 1.0, MicronutrientUnit.MG) }),
        )
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(addModel(SavedStateHandle())) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_details_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("add_food_label_nutrients").assertExists()
        rule.onNodeWithTag("add_food_label_row_${micronutrientTag(kinds.last())}").assertDoesNotExist()
        rule.onNodeWithTag("add_food_label_toggle").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("add_food_label_nutrients").assertExists()
        rule.onNodeWithTag("add_food_label_row_${micronutrientTag(kinds.last())}").assertExists()
        rule.onNodeWithTag("add_food_label_toggle").assertTextEquals("Ver menos")
    }

    @Test
    fun typedAmount_andMicronutrients_surviveRecreation_whenAViewModelIsRebuiltFromTheSameState() {
        foods.known["egg"] = egg
        foods.micronutrientResults["egg"] = Result.success(
            Micronutrients(listOf(Micronutrient(MicronutrientKind.Fiber, 2.5, MicronutrientUnit.G))),
        )
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(addModel(saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement("150")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_amount").assertTextContains("150")
        rule.onNodeWithTag("food_sheet_details_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("add_food_label_row_fiber").assertExists()
    }

    @Test
    fun openSheet_keepsAmountPortionAndMeal_afterRecreation() {
        foods.known["egg"] = egg
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(addModel(saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_portion_option_p1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_amount").performTextReplacement("3")
        rule.onNodeWithTag("food_sheet_meal_snack").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet").assertExists()
        rule.onNodeWithTag("food_sheet_portion_option_p1").assertIsSelected()
        rule.onNodeWithTag("food_sheet_amount").assertTextContains("3")
        rule.onNodeWithTag("food_sheet_meal_snack").assertIsSelected()
    }
}
