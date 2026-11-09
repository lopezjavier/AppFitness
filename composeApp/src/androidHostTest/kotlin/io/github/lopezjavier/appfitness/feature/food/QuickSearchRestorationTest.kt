package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
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

private const val SEARCH_TIMEOUT_MS = 5_000L

/** ADR-40 for the quick search (T-263): the typed text and the view-only details survive recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class QuickSearchRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()
    private val chicken = food("chicken", "Pechuga de pollo", kcal = 110.0)

    init {
        foods.known["chicken"] = chicken
        foods.searchResults["pollo"] = Result.success(listOf(chicken))
    }

    private fun searchModel(state: SavedStateHandle) =
        foodSearchModel(foods, connectivity, FakeBarcodeScanner(), state, viewOnly = true)

    private fun addModel(state: SavedStateHandle) = AddFoodViewModel(
        foodId = "chicken",
        initialSlotId = null,
        date = LocalDate(2026, 10, 19),
        foodRepository = foods,
        foodDiary = FakeFoodDiaryRepository(),
        mealSlots = FakeMealSlotRepository(),
        foodPreferencesRepository = FakeFoodPreferencesRepository(),
        resolvePortion = ResolvePortion(),
        scaleNutrition = ScaleNutrition(),
        validateQuantity = ValidateQuantity(),
        defaultMealSlot = DefaultMealSlot(),
        clock = MutableClock(),
        connectivity = connectivity,
        savedState = state,
        viewOnly = true,
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

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun quickSearchWithATypedQuery_survivesRecreation_stillViewOnly() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SearchHost(searchModel(saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_search_field").performTextInput("pollo")
        rule.waitUntil(SEARCH_TIMEOUT_MS) { exists("food_search_row_chicken") }

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_field").assertTextContains("pollo")
        rule.onNodeWithTag("food_search_title").assertTextEquals("Buscar alimento")
        assertTrue(!exists("food_search_action_scan"))
        assertTrue(!exists("food_search_action_create"))
    }

    @Test
    fun aViewModelRebuiltFromTheSameSavedState_keepsTheQueryAndViewOnlyComesFromTheRoute() {
        val saved = SavedStateHandle(mapOf("food_search_query" to "pollo"))

        val model = searchModel(saved)

        assertEquals("pollo", model.uiState.value.query)
        assertTrue(model.uiState.value.viewOnly)
        assertTrue(!foodSearchModel(foods, connectivity, FakeBarcodeScanner(), saved).uiState.value.viewOnly)
    }

    @Test
    fun theViewOnlyDetailsSheet_survivesRecreationWithoutAddOrAmount() {
        val saved = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { SheetHost(addModel(saved)) }
        rule.waitForIdle()
        rule.onNodeWithTag("food_sheet_title").assertTextEquals("Pechuga de pollo")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_sheet_title").assertTextEquals("Pechuga de pollo")
        assertTrue(!exists("food_sheet_commit"))
        assertTrue(!exists("food_sheet_amount"))
    }
}
