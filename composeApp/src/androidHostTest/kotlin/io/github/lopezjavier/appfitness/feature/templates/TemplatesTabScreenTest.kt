package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.MealTemplateItem
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.diary.DiaryEvent
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryViewModel
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import io.github.lopezjavier.appfitness.feature.food.FakeBarcodeScanner
import io.github.lopezjavier.appfitness.feature.food.FakeFoodRepository
import io.github.lopezjavier.appfitness.feature.food.FoodSearchEffect
import io.github.lopezjavier.appfitness.feature.food.FoodSearchScreen
import io.github.lopezjavier.appfitness.feature.food.FoodSearchViewModel
import io.github.lopezjavier.appfitness.feature.food.food
import io.github.lopezjavier.appfitness.feature.food.foodSearchModel
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Eggs = food("eggs", "Huevos revueltos", kcal = 100.0)
private val Shake = food("shake", "Batido de proteína", kcal = 50.0)
private val DesayunoA = MealTemplate(
    id = "t1",
    name = "Desayuno A",
    mealSlotId = "breakfast",
    items = listOf(MealTemplateItem(Eggs, 120.0, null), MealTemplateItem(Shake, 300.0, "1 vaso")),
)

/**
 * US-21 "Adding a comida to a meal" and the Comidas tab states: the tab lists the saved meals with their totals, adds
 * one to the chosen meal (the Diary totals update at once and the search closes), and shows the empty and error states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TemplatesTabScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val templates = FakeMealTemplateRepository(mutableMapOf("eggs" to Eggs, "shake" to Shake))
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun search(slotId: String? = "lunch") = foodSearchModel(
        repository = foods,
        connectivity = connectivity,
        scanner = FakeBarcodeScanner(),
        state = SavedStateHandle(),
        slotId = slotId,
        templates = templates,
        diary = diary,
    )

    @Composable
    private fun SearchHost(model: FoodSearchViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { FoodSearchScreen(state, model::onEvent) }
    }

    private fun openComidasTab() {
        rule.onNodeWithTag("food_search_tab_templates").performClick()
        rule.waitForIdle()
    }

    @Test
    fun comidasTab_listsTheComidaWithItsFoodCountAndTotalKcal() {
        templates.templates += DesayunoA
        val model = search()
        rule.setContent { SearchHost(model) }
        rule.waitForIdle()

        openComidasTab()

        rule.onNodeWithTag("food_search_tab_templates").assertIsSelected()
        rule.onNodeWithTag("food_search_template_t1").assertIsDisplayed()
        rule.onNode(hasText("Desayuno A") and hasAnyAncestor(hasTestTag("food_search_template_t1")))
            .assertIsDisplayed()
        rule.onNode(hasText("2 alimentos · 270 kcal") and hasAnyAncestor(hasTestTag("food_search_template_t1")))
            .assertIsDisplayed()
    }

    @Test
    fun comidasTab_withNoComidas_showsTheEmptyState() {
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()

        openComidasTab()

        rule.onNodeWithTag("food_search_templates_empty").assertIsDisplayed()
    }

    @Test
    fun comidasTab_whenTheListFails_showsTheErrorState_andRetryLoadsIt() {
        templates.listFailure = AppError.Network
        templates.templates += DesayunoA
        rule.setContent { SearchHost(search()) }
        rule.waitForIdle()
        openComidasTab()

        rule.onNodeWithTag("food_search_templates_retry_action").assertIsDisplayed()
        templates.listFailure = null
        rule.onNodeWithTag("food_search_templates_retry_action").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("food_search_template_t1").assertIsDisplayed()
    }

    @Test
    fun addingAComida_logsItsFoods_toTheChosenMeal_andTheDiaryTotalsUpdate() {
        templates.templates += DesayunoA
        val searchModel = search(slotId = "lunch")
        rule.setContent { Flow(diaryModel(), searchModel) }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_section_lunch_add").performClick()
        rule.waitForIdle()
        openComidasTab()

        rule.onNodeWithTag("food_search_template_t1_add").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("diary_screen").assertIsDisplayed()
        rule.onNode(hasText("270 kcal") and hasAnyAncestor(hasTestTag("diary_section_lunch")))
            .assertIsDisplayed()
        val batch = diary.addedBatches.single()
        assertEquals(listOf("eggs" to 120.0, "shake" to 300.0), batch.map { it.foodId to it.amount })
        assertEquals(setOf("lunch"), batch.map { it.mealSlotId }.toSet())
    }

    @Composable
    private fun Flow(diaryModel: DiaryViewModel, searchModel: FoodSearchViewModel) {
        var searching by remember { mutableStateOf(false) }
        val diaryState by diaryModel.uiState.collectAsState()
        val searchState by searchModel.uiState.collectAsState()
        LaunchedEffect(searchModel) {
            searchModel.effects.collect { if (it == FoodSearchEffect.TemplateAdded) searching = false }
        }
        AppTheme {
            if (searching) {
                FoodSearchScreen(searchState, searchModel::onEvent)
            } else {
                DiaryScreen(diaryState, { event ->
                    val opensSearch = event is DiaryEvent.AddToMeal || event == DiaryEvent.AddFood
                    if (opensSearch) searching = true else diaryModel.onEvent(event)
                })
            }
        }
    }

    private fun diaryModel() = DiaryViewModel(
        FakeMealSlotRepository(),
        diary,
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
        BuildDiaryDay(),
        MutableClock(),
        FakeConnectivityObserver(),
        SavedStateHandle(),
    )
}
