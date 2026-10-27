package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.ValidateMealSlots
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the meals draft (names, order, staged removals, open Name sheet) survives recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class MealSlotsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val slots = FakeMealSlotRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun viewModel(state: SavedStateHandle) = MealSlotsEditorViewModel(
        state,
        slots,
        ValidateMealSlots(),
        connectivity,
    )

    @Composable
    private fun Host(model: MealSlotsEditorViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { MealSlotsScreen(state, model::onEvent) }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun stagedRemoval_survivesRecreation_andIsSentOnSave() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        tap("meals_delete_snack")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("meals_row_snack").assertDoesNotExist()
        assertTrue("a removal is never applied silently", slots.saved.isEmpty())
        tap("meals_save")
        assertEquals(listOf(listOf("snack")), slots.savedRemoved)
    }

    @Test
    fun openNameSheetAndTypedText_surviveRecreation() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        tap("meals_add")
        rule.onNodeWithTag("meal_name_sheet_input").performTextInput("Pre-entreno")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("meal_name_sheet").assertExists()
        rule.onNodeWithTag("meal_name_sheet_input").assertTextContains("Pre-entreno")
        tap("meal_name_sheet_commit")
        rule.onNodeWithTag("meals_name_new_1").assertTextEquals("Pre-entreno")
    }

    @Test
    fun viewModelRebuiltFromTheSameSavedState_keepsDraftRemovalsBaselineAndSheet() {
        val savedState = SavedStateHandle()
        val first = viewModel(savedState)
        rule.setContent { Host(first) }
        rule.waitForIdle()
        tap("meals_name_lunch")
        tap("meal_name_sheet_close")
        tap("meals_delete_snack")
        tap("meals_name_dinner")

        val rebuilt = viewModel(savedState)
        rule.waitForIdle()

        val state = rebuilt.uiState.value as MealSlotsUiState.Editing
        assertEquals(listOf("breakfast", "lunch", "dinner"), state.rows.map { it.key })
        assertEquals(MealNameSheet.Rename("dinner", "Cena"), state.sheet)
        assertTrue(state.isDirty)
        assertTrue(!state.canNavigateToDefaults)
    }
}
