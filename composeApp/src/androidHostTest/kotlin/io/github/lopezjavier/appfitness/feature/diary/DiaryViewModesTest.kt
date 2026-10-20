package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import io.github.lopezjavier.appfitness.domain.model.DiaryViewMode
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 19)

/** US-19 "Switching the view" and "Summary view": Detalle / Lista / Resumen with the real ViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class DiaryViewModesTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository().apply {
        server[Today] = listOf(
            FoodLogEntry(
                "e1", Today, "lunch", "chicken", "Pechuga de pollo", 220.0, null, "g", 242.0, 22.0, 44.0, 11.0,
            ),
        )
    }

    private fun start(preferences: FakeDiaryPreferences) {
        val model = DiaryViewModel(
            FakeMealSlotRepository(),
            diary,
            FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
            BuildDiaryDay(),
            MutableClock(),
            preferences,
            FakeConnectivityObserver(),
            SavedStateHandle(),
        )
        rule.setContent { Host(model) }
        rule.waitForIdle()
    }

    @Composable
    private fun Host(model: DiaryViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { DiaryScreen(state, model::onEvent) }
    }

    private fun choose(mode: String) {
        rule.onNodeWithTag("diary_view_menu").performClick()
        rule.onNodeWithTag("diary_view_$mode").performClick()
        rule.waitForIdle()
    }

    private fun inEntry(text: String) = hasText(text) and hasAnyAncestor(hasTestTag("diary_entry_e1"))

    @Test
    fun detalle_isTheDefault_andShowsTheMacrosOfEachEntry() {
        start(FakeDiaryPreferences())

        rule.onNode(inEntry("Pechuga de pollo")).assertExists()
        rule.onNode(inEntry("220 g")).assertExists()
        rule.onNode(inEntry("242 kcal")).assertExists()
        rule.onNode(inEntry("P 22 g")).assertExists()
        rule.onNode(inEntry("C 44 g")).assertExists()
        rule.onNode(inEntry("G 11 g")).assertExists()
    }

    @Test
    fun choosingLista_showsOneLinePerEntryWithoutMacros_andSavesTheChoice() {
        val preferences = FakeDiaryPreferences()
        start(preferences)

        choose("list")

        rule.onNode(inEntry("Pechuga de pollo")).assertExists()
        rule.onNode(inEntry("220 g")).assertExists()
        rule.onNode(inEntry("242 kcal")).assertExists()
        rule.onNode(inEntry("P 22 g")).assertDoesNotExist()
        assertEquals(listOf(DiaryViewMode.List), preferences.modes)
        rule.onNodeWithTag("diary_view_menu").performClick()
        rule.onNodeWithTag("diary_view_list").assertIsSelected()
    }

    @Test
    fun choosingResumen_hidesTheEntries_andKeepsEachMealWithItsKcalAndMacros() {
        val preferences = FakeDiaryPreferences()
        start(preferences)

        choose("summary")

        rule.onNodeWithTag("diary_entry_e1").assertDoesNotExist()
        rule.onNode(hasText("242 kcal") and hasAnyAncestor(hasTestTag("diary_section_lunch"))).assertExists()
        rule.onNode(hasText("P 22 g") and hasAnyAncestor(hasTestTag("diary_section_lunch"))).assertExists()
        rule.onNodeWithTag("diary_section_lunch_add").assertDoesNotExist()
        assertEquals(listOf(DiaryViewMode.Summary), preferences.modes)
    }

    @Test
    fun theSavedMode_isReadWhenTheDiaryOpens() {
        start(FakeDiaryPreferences(DiaryViewMode.Summary))

        rule.onNodeWithTag("diary_section_lunch").assertExists()
        rule.onNodeWithTag("diary_entry_e1").assertDoesNotExist()
    }

    @Test
    fun goingBackToDetalle_showsTheEntriesAgain() {
        start(FakeDiaryPreferences(DiaryViewMode.Summary))

        choose("detail")

        rule.onNode(inEntry("P 22 g")).assertExists()
    }
}
