package io.github.lopezjavier.appfitness.feature.meals.defaults

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-51 end to end with the real ViewModel over fakes: pick a comida, choose days, save. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class MealDefaultsFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val host by lazy { MealDefaultsTestHost(rule) }

    private fun start() {
        rule.setContent { host.Content(host.viewModel()) }
        rule.waitForIdle()
    }

    @Test
    fun assigningAComidaToMondayWednesdayFriday_savesThatPayloadAndCloses() {
        val a = host.comida("Desayuno A")
        start()

        host.tap("meal_defaults_add_breakfast")
        host.tap("default_picker_row_$a")
        listOf(2, 4, 6, 7).forEach { host.tap("assign_default_day_$it") }
        host.tap("assign_default_sheet_commit")
        host.tap("meal_defaults_save")

        assertEquals(listOf(listOf(MealDefaultAssignment("breakfast", a, setOf(1, 3, 5)))), host.defaults.saved)
        assertEquals(1, host.closed)
    }

    @Test
    fun aSecondComidaOnOtherDays_isBlockedOnTheDaysOfTheFirst() {
        val a = host.comida("Desayuno A")
        val b = host.comida("Desayuno B")
        host.defaults.defaults += MealDefault("breakfast", a, 1)
        start()

        host.tap("meal_defaults_add_breakfast")
        host.tap("default_picker_row_$b")

        rule.onNodeWithTag("assign_default_day_1").assertIsNotEnabled()
        rule.onNodeWithTag("assign_default_conflict_note").assertTextContains("Ya asignado a Desayuno A")
    }

    @Test
    fun removingTheOnlyDefault_andSaving_sendsAnEmptyList() {
        val a = host.comida("Desayuno A")
        host.defaults.defaults += listOf(1, 2).map { MealDefault("breakfast", a, it) }
        start()

        host.tap("meal_defaults_remove_breakfast_$a")
        host.tap("meal_defaults_save")

        assertEquals(listOf(emptyList<MealDefaultAssignment>()), host.defaults.saved)
    }

    @Test
    fun clearingEveryDayInTheSheet_blocksSavingTheSheet() {
        val a = host.comida("Desayuno A")
        start()
        host.tap("meal_defaults_add_breakfast")
        host.tap("default_picker_row_$a")

        for (day in 1..7) host.tap("assign_default_day_$day")

        rule.onNodeWithTag("assign_default_day_error").assertTextContains("Selecciona al menos un día")
        rule.onNodeWithTag("assign_default_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun aFailedSave_keepsTheDraftAndShowsTheError() {
        val a = host.comida("Desayuno A")
        host.defaults.saveFailure = io.github.lopezjavier.appfitness.domain.AppError.Unknown
        start()
        host.tap("meal_defaults_add_breakfast")
        host.tap("default_picker_row_$a")
        host.tap("assign_default_sheet_commit")

        host.tap("meal_defaults_save")

        assertEquals(listOf("No se pudo guardar. Inténtalo de nuevo."), host.messages.shown)
        rule.onNodeWithTag("meal_defaults_assignment_breakfast_$a").assertExists()
        assertTrue(host.closed == 0)
    }
}
