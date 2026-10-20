package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.ValidateMealSlots
import io.github.lopezjavier.appfitness.feature.diary.FakeFoodDiaryRepository
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the meals draft (names, switches, order, added rows) survives recreation from the same saved state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class MealSlotsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val slots = FakeMealSlotRepository()
    private val diary = FakeFoodDiaryRepository()
    private val connectivity = FakeConnectivityObserver()

    private fun viewModel(state: SavedStateHandle) = MealSlotsEditorViewModel(
        LocalDate(2026, 10, 19),
        state,
        slots,
        diary,
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

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test
    fun editedDraft_survivesRecreationFromTheSameSavedState() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("meals_label_lunch").performTextReplacement("Almuerzo")
        tap("meals_switch_snack")
        tap("meals_up_dinner")
        tap("meals_add")
        rule.onNodeWithTag("meals_label_new_1").performTextReplacement("Cena tardía")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("meals_label_lunch").assertTextContains("Almuerzo")
        rule.onNodeWithTag("meals_label_new_1").assertTextContains("Cena tardía")
        assertTrue(top("meals_row_dinner") < top("meals_row_snack"))
        assertTrue(top("meals_row_new_1") > top("meals_row_snack"))
        // What gets saved shows that the names, the switch and the order were all restored.
        tap("meals_save")
        val saved = slots.saved.single().first
        assertEquals(listOf("Desayuno", "Almuerzo", "Cena", "Merienda", "Cena tardía"), saved.map { it.label })
        assertEquals(listOf(true, true, true, false, true), saved.map { it.enabled })
    }
}
