package io.github.lopezjavier.appfitness.feature.meals.defaults

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the default comidas draft, the open picker and the open sheet survive recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class MealDefaultsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val host by lazy { MealDefaultsTestHost(rule) }

    @Test
    fun draftAndPartialSheetSelection_surviveRecreationFromTheSameSavedState() {
        val a = host.comida("Desayuno A")
        val b = host.comida("Desayuno B")
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { host.Content(host.viewModel(savedState)) }
        rule.waitForIdle()
        host.tap("meal_defaults_add_breakfast")
        host.tap("default_picker_row_$a")
        for (day in 3..7) host.tap("assign_default_day_$day")
        host.tap("assign_default_sheet_commit")
        host.tap("meal_defaults_add_breakfast")
        host.tap("default_picker_row_$b")
        host.tap("assign_default_day_3")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("assign_default_sheet").assertExists()
        rule.onNodeWithTag("assign_default_day_3").assertIsOff()
        rule.onNodeWithTag("assign_default_day_4").assertIsOn()
        host.tap("assign_default_sheet_commit")
        rule.onNodeWithTag("meal_defaults_assignment_breakfast_$b").assertExists()
        rule.onNodeWithTag("meal_defaults_assignment_breakfast_$a").assertExists()
    }

    @Test
    fun openPicker_survivesRecreation() {
        host.comida("Desayuno A")
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { host.Content(host.viewModel(savedState)) }
        rule.waitForIdle()
        host.tap("meal_defaults_add_lunch")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("default_picker_sheet").assertExists()
    }

    @Test
    fun viewModelRebuiltFromTheSameSavedState_keepsTheDraftAndTheSheet() {
        val a = host.comida("Desayuno A")
        val savedState = SavedStateHandle()
        rule.setContent { host.Content(host.viewModel(savedState)) }
        rule.waitForIdle()
        host.tap("meal_defaults_add_breakfast")
        host.tap("default_picker_row_$a")
        host.tap("assign_default_day_2")
        host.tap("assign_default_sheet_commit")
        host.tap("meal_defaults_add_lunch")
        host.tap("default_picker_row_$a")
        host.tap("assign_default_day_1")

        val rebuilt = host.viewModel(savedState)
        rule.waitForIdle()

        val content = rebuilt.uiState.value as MealDefaultsUiState.Content
        assertEquals(
            setOf(1, 3, 4, 5, 6, 7),
            content.meals.first {
                it.slotId == "breakfast"
            }.assignments.single().days,
        )
        assertEquals(setOf(2, 3, 4, 5, 6, 7), content.sheet?.selectedDays)
        assertTrue(content.isDirty)
    }
}
