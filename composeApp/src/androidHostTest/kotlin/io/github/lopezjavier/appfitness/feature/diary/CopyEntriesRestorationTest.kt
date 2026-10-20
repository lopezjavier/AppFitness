package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 19)

/** ADR-40: the selection and the open copy sheet (meal, day) survive recreating the screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class CopyEntriesRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository().apply {
        server[Today] = listOf(
            FoodLogEntry("a", Today, "lunch", "fa", "Arroz", 100.0, null, "g", 100.0, 10.0, 10.0, 5.0),
            FoodLogEntry("b", Today, "lunch", "fb", "Pollo", 100.0, null, "g", 100.0, 10.0, 10.0, 5.0),
        )
    }

    private fun model(state: SavedStateHandle) = DiaryViewModel(
        FakeMealSlotRepository(),
        diary,
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
        BuildDiaryDay(),
        MutableClock(),
        FakeDiaryPreferences(),
        FakeConnectivityObserver(),
        state,
    )

    @Composable
    @Suppress("ViewModelForwarding") // A test host wires the real ViewModel.
    private fun Host(model: DiaryViewModel) {
        AppTheme {
            DiaryRoot(
                onOpenPreferences = {},
                onAddToMeal = { _, _ -> },
                onAddFood = {},
                onOpenMealSettings = {},
                editHost = DiaryEditHost({ _, _ -> }, null, {}),
                viewModel = model,
            )
        }
    }

    @Test
    fun theOpenCopySheet_keepsTheChosenMealAndDay_afterRecreation() {
        val saved = SavedStateHandle()
        val viewModel = model(saved)
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel) }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_entry_a").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_selection_copy").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("copy_entries_slot_dinner").performClick()
        rule.onNodeWithTag("copy_entries_next_day").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("copy_entries_sheet").assertExists()
        rule.onNodeWithTag("copy_entries_slot_dinner").assertIsSelected()
        rule.onNodeWithTag("copy_entries_day").assertTextContains("Mañana")
    }

    @Test
    fun aViewModelRecreatedFromTheSameSavedState_keepsTheSelectedEntries() {
        val saved = SavedStateHandle()
        val current = mutableStateOf(model(saved))
        rule.setContent { Host(current.value) }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_entry_a").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_entry_b").performClick()
        rule.waitForIdle()

        current.value = model(saved)
        rule.waitForIdle()

        rule.onNodeWithTag("diary_selection_count").assertTextContains("2 seleccionados")
        rule.onNodeWithTag("diary_entry_select_a").assertExists()
    }
}
