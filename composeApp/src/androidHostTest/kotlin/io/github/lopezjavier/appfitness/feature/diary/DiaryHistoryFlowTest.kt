package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.app.AppScaffold
import io.github.lopezjavier.appfitness.app.DiaryHistorySlot
import io.github.lopezjavier.appfitness.app.DiarySlot
import io.github.lopezjavier.appfitness.app.EditEntrySlot
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.app.FoodSearchSlot
import io.github.lopezjavier.appfitness.app.ProfileSlot
import io.github.lopezjavier.appfitness.app.TemplateDetailsSlot
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.meals.defaults.FakeMealDefaultsRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Marked = LocalDate(2026, 10, 15)

/** US-24 "Opening the history": Perfil > Estadísticas -> Historial -> a day -> its Diary -> back to the history. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class DiaryHistoryFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository().apply {
        summaryDays += DiaryDaySummary(Marked, MealTotals(1840.0, 142.0, 210.0, 48.0))
    }
    private val goals =
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25)))
    private val clock = MutableClock()

    private val diarySlot: DiarySlot = { modifier, _, onAddFood, _, edit, onBack ->
        val model: DiaryViewModel = viewModel(
            factory = viewModelFactory {
                initializer {
                    DiaryViewModel(
                        FakeMealSlotRepository(),
                        diary,
                        goals,
                        BuildDiaryDay(),
                        clock,
                        FakeConnectivityObserver(),
                        FakeMealDefaultsRepository(),
                        createSavedStateHandle(),
                    )
                }
            },
        )
        Box(modifier.fillMaxSize()) {
            DiaryRoot(
                onOpenPreferences = {},
                onAddToMeal = { _, _ -> },
                onAddFood = { onAddFood(null, it) },
                onSaveTemplate = { _, _ -> },
                editHost = edit,
                onBack = onBack,
                viewModel = model,
            )
            // Reach the callbacks the real screen does not expose with a fixed day.
            Column(Modifier.align(Alignment.BottomStart)) {
                Box(Modifier.size(48.dp).testTag("test_open_edit").clickable { edit.onEditEntry("e1", Marked) })
                Box(Modifier.size(48.dp).testTag("test_add_food").clickable { onAddFood(null, Marked) })
            }
        }
    }

    private val historySlot: DiaryHistorySlot = { modifier, onOpenDay, onBack ->
        val model: DiaryHistoryViewModel = viewModel(
            factory = viewModelFactory {
                initializer { DiaryHistoryViewModel(diary, goals, clock, createSavedStateHandle()) }
            },
        )
        DiaryHistoryRoot(onOpenDay, onBack, modifier, model)
    }

    private val profileSlot: ProfileSlot = { modifier, _, _, _, onOpenDiaryHistory, _, _ ->
        Box(modifier.fillMaxSize().testTag("profile_screen")) {
            Box(Modifier.size(48.dp).testTag("profile_diary_history_row").clickable(onClick = onOpenDiaryHistory))
        }
    }

    private val searchSlot: FoodSearchSlot = { modifier, _, _, _, _, onOpenTemplate, _ ->
        Box(modifier.fillMaxSize().testTag("food_search_screen")) {
            Box(Modifier.size(48.dp).testTag("test_search_open").clickable { onOpenTemplate("t1") })
        }
    }

    private val detailsSlot: TemplateDetailsSlot = { modifier, _, _, onAdded, _, _, _, _ ->
        Box(modifier.fillMaxSize().testTag("template_details_screen")) {
            Box(Modifier.size(48.dp).testTag("test_search_add").clickable(onClick = onAdded))
        }
    }

    private val editSlot: EditEntrySlot = { _, _, _, _, onDismiss ->
        Box(Modifier.size(48.dp).testTag("edit_sheet").clickable(onClick = onDismiss))
    }

    @Composable
    private fun Shell() {
        AppTheme {
            AppScaffold(
                navController = rememberNavController(),
                profile = profileSlot,
                diary = diarySlot,
                diaryHistory = historySlot,
                foodSearch = searchSlot,
                templateDetails = detailsSlot,
                editEntry = editSlot,
            )
        }
    }

    private val tester = StateRestorationTester(rule)

    private fun start() {
        tester.setContent { Shell() }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun openHistory() {
        tap("nav_tab_profile")
        tap("profile_diary_history_row")
    }

    private fun openDayFromHistory() {
        openHistory()
        tap("month_calendar_day_$Marked")
        tap("diary_history_row_open")
    }

    @Test
    fun profileRow_opensTheHistoryInAFullScreenChrome() {
        start()

        openHistory()

        rule.onNodeWithTag("diary_history_screen").assertExists()
        rule.onNodeWithTag("diary_history_title").assertTextEquals("Calorías")
        rule.onNodeWithTag("nav_tab_diary").assertDoesNotExist()
        rule.onNodeWithTag("month_calendar_day_$Marked").assertExists()
    }

    @Test
    fun diaryTab_hasNoHistoryEntryOfItsOwn() {
        start()

        rule.onNodeWithTag("diary_history_open").assertDoesNotExist()
        rule.onNodeWithTag("diary_day_picker_open").assertExists()
    }

    @Test
    fun openingADayFromTheHistory_showsThatDaysDiaryWithBackAndTheCalendarButton() {
        start()

        openDayFromHistory()

        rule.onNodeWithTag("diary_date_label").assertTextEquals("jue, 15 oct")
        rule.onNodeWithTag("diary_back").assertExists()
        rule.onNodeWithTag("diary_day_picker_open").assertExists()
        rule.onNodeWithTag("diary_history_open").assertDoesNotExist()
        rule.onNodeWithTag("nav_tab_diary").assertDoesNotExist()
    }

    @Test
    fun backFromTheHistory_returnsToPerfil() {
        start()
        openHistory()

        tap("diary_history_back")

        rule.onNodeWithTag("profile_screen").assertExists()
    }

    @Test
    fun recreationOnAPastDayOpenedFromTheHistory_keepsThatDayAndItsCalendarButton() {
        start()
        openDayFromHistory()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("diary_date_label").assertTextEquals("jue, 15 oct")
        rule.onNodeWithTag("diary_day_picker_open").assertExists()
        rule.onNodeWithTag("diary_back").assertExists()
    }

    @Test
    fun backFromTheDay_returnsToTheHistoryWithTheDayStillSelected() {
        start()
        openDayFromHistory()

        tap("diary_back")

        rule.onNodeWithTag("diary_history_screen").assertExists()
        rule.onNodeWithTag("month_calendar_day_$Marked").assertIsSelected()
    }

    @Test
    fun editSheetOverAPastDay_keepsTheFullScreenBar() {
        start()
        openDayFromHistory()

        tap("test_open_edit")

        rule.onNodeWithTag("edit_sheet").assertExists()
        rule.onNodeWithTag("diary_back").assertExists()
        rule.onNodeWithTag("nav_tab_diary").assertDoesNotExist()
    }

    @Test
    fun editSheetOverTheDiaryTab_keepsTheTabBars() {
        start()

        tap("test_open_edit")

        rule.onNodeWithTag("edit_sheet").assertExists()
        rule.onNodeWithTag("nav_tab_diary").assertExists()
        rule.onNodeWithTag("diary_back").assertDoesNotExist()
    }

    @Test
    fun addingFoodToAPastDay_returnsToThatDayNotTheTab() {
        start()
        openDayFromHistory()
        tap("test_add_food")

        tap("test_search_open")
        tap("test_search_add")

        rule.onNodeWithTag("diary_back").assertExists()
        rule.onNodeWithTag("diary_date_label").assertTextEquals("jue, 15 oct")
    }
}
