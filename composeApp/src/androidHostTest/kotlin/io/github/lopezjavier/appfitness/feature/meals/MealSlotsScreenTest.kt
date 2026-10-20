package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.MealSlotError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun rowUi(
    key: String,
    label: String,
    enabled: Boolean = true,
    error: MealSlotError? = null,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
    canToggle: Boolean = true,
    hasEntries: Boolean = false,
) = MealSlotRowUi(key, label, enabled, error, canMoveUp, canMoveDown, canToggle, hasEntries, isNew = false)

private val Rows = listOf(
    rowUi("breakfast", "Desayuno", canMoveUp = false),
    rowUi("lunch", "Comida"),
    rowUi("dinner", "Cena", canMoveDown = false),
)

private fun editing(
    rows: List<MealSlotRowUi> = Rows,
    isOnline: Boolean = true,
    isSaving: Boolean = false,
    showDiscardConfirm: Boolean = false,
    saveError: MealSlotsSaveError? = null,
    noneEnabled: Boolean = false,
    canSave: Boolean = true,
) = MealSlotsUiState.Editing(
    rows = rows,
    noneEnabled = noneEnabled,
    isOnline = isOnline,
    isSaving = isSaving,
    isDirty = true,
    showDiscardConfirm = showDiscardConfirm,
    saveError = saveError,
    canSave = canSave,
)

/** US-23 meals editor rendered from a given state: rows, controls, states and the events they send. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per rule or state.
class MealSlotsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<MealSlotsEvent>()

    private fun show(state: MealSlotsUiState, viewedDayIsToday: Boolean = true) {
        rule.setContent {
            AppTheme { MealSlotsScreen(state, { events += it }, viewedDayIsToday = viewedDayIsToday) }
        }
        rule.waitForIdle()
    }

    @Test
    fun rows_showEveryMealWithItsNameAndSwitchState() {
        show(editing(Rows + rowUi("extra", "Extra", enabled = false)))

        rule.onNodeWithTag("meals_label_breakfast").assertTextContains("Desayuno")
        rule.onNodeWithTag("meals_label_extra").assertTextContains("Extra")
        rule.onNodeWithTag("meals_switch_extra").assertIsEnabled()
        rule.onNodeWithTag("meals_row_extra").assertExists()
    }

    @Test
    fun typingInAName_sendsSetLabel() {
        show(editing())

        rule.onNodeWithTag("meals_label_lunch").performTextReplacement("Almuerzo")

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.SetLabel("lunch", "Almuerzo")), events)
    }

    @Test
    fun switch_sendsSetEnabledWithTheNewValue() {
        show(editing())

        rule.onNodeWithTag("meals_switch_lunch").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.SetEnabled("lunch", false)), events)
    }

    @Test
    fun moveButtons_sendTheRowIndex_andTheEdgesAreDisabled() {
        show(editing())

        rule.onNodeWithTag("meals_up_breakfast").assertIsNotEnabled()
        rule.onNodeWithTag("meals_down_dinner").assertIsNotEnabled()
        rule.onNodeWithTag("meals_up_dinner").performClick()
        rule.onNodeWithTag("meals_down_breakfast").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.MoveUp(2), MealSlotsEvent.MoveDown(0)), events)
    }

    @Test
    fun addButtons_addAtTheEnd_orAfterTheTappedRow() {
        show(editing())

        rule.onNodeWithTag("meals_add").performClick()
        rule.onNodeWithTag("meals_add_after_breakfast").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Add(), MealSlotsEvent.Add(afterIndex = 0)), events)
    }

    @Test
    fun mealWithEntriesOnTheViewedDay_hasALockedSwitchAndTheTodayHint() {
        show(editing(listOf(rowUi("lunch", "Comida", canToggle = false, hasEntries = true))))

        rule.onNodeWithTag("meals_switch_lunch").assertIsNotEnabled()
        rule.onNodeWithTag("meals_hint_lunch").assertTextEquals("Tiene alimentos hoy; no se puede desactivar")
    }

    @Test
    fun mealWithEntriesOnAnotherDay_usesTheDayHint() {
        show(editing(listOf(rowUi("lunch", "Comida", canToggle = false, hasEntries = true))), viewedDayIsToday = false)

        rule.onNodeWithTag("meals_hint_lunch")
            .assertTextEquals("Tiene alimentos en este día; no se puede desactivar")
    }

    @Test
    fun lockedSwitchWhenEntriesCouldNotBeRead_explainsIt() {
        show(editing(listOf(rowUi("lunch", "Comida", canToggle = false, hasEntries = false))))

        rule.onNodeWithTag("meals_hint_lunch").assertExists()
    }

    @Test
    fun mealThatCanBeSwitchedOff_hasNoHint() {
        show(editing())

        rule.onNodeWithTag("meals_hint_lunch").assertDoesNotExist()
    }

    @Test
    fun blankName_showsTheFieldError() {
        show(editing(listOf(rowUi("x", "", error = MealSlotError.Blank)), canSave = false))

        rule.onNodeWithTag("meals_label_x_error").assertTextEquals("Escribe un nombre")
        rule.onNodeWithTag("meals_save").assertIsNotEnabled()
    }

    @Test
    fun tooLongName_showsTheLengthError() {
        show(editing(listOf(rowUi("x", "n".repeat(51), error = MealSlotError.TooLong)), canSave = false))

        rule.onNodeWithTag("meals_label_x_error").assertTextEquals("Máximo 50 caracteres")
    }

    @Test
    fun save_isEnabledWhenSavable_andSendsSave() {
        show(editing())

        rule.onNodeWithTag("meals_save").assertIsEnabled().performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Save), events)
    }

    @Test
    fun noEnabledMeal_explainsWhySaveIsDisabled() {
        show(editing(noneEnabled = true, canSave = false))

        rule.onNodeWithTag("meals_none_enabled").assertTextEquals("Activa al menos una comida.")
        rule.onNodeWithTag("meals_save").assertIsNotEnabled()
    }

    @Test
    fun offline_showsTheBannerAndBlocksSaving() {
        show(editing(isOnline = false, canSave = false))

        rule.onNodeWithTag("meals_offline_banner").assertExists()
        rule.onNodeWithTag("meals_save").assertIsNotEnabled()
    }

    @Test
    fun saving_makesTheRowsInert() {
        show(editing(isSaving = true, canSave = false))

        rule.onNodeWithTag("meals_switch_lunch").assertIsNotEnabled()
        rule.onNodeWithTag("meals_up_lunch").assertIsNotEnabled()
        rule.onNodeWithTag("meals_add").assertIsNotEnabled()
        rule.onNodeWithTag("meals_save").assertIsNotEnabled()
    }

    @Test
    fun saveError_isShownAboveTheSaveButton_withTheDraftKept() {
        show(editing(saveError = MealSlotsSaveError.Failed))

        rule.onNodeWithTag("meals_save_error")
            .assertTextEquals("No se pudieron guardar las comidas. Inténtalo de nuevo.")
        rule.onNodeWithTag("meals_label_lunch").assertTextContains("Comida")
    }

    @Test
    fun rejectedSave_explainsAMealGainedEntries() {
        show(editing(saveError = MealSlotsSaveError.Rejected))

        rule.onNodeWithTag("meals_save_error").assertExists()
    }

    @Test
    fun loading_showsSkeletonRows() {
        show(MealSlotsUiState.Loading)

        rule.onNodeWithTag("meals_loading").assertExists()
    }

    @Test
    fun loadError_offersRetry() {
        show(MealSlotsUiState.LoadError)

        rule.onNodeWithTag("meals_error_action").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Retry), events)
    }

    @Test
    fun discardDialog_confirmsOrKeepsEditing() {
        show(editing(showDiscardConfirm = true))

        rule.onNodeWithTag("meals_discard_cancel").performClick()
        rule.onNodeWithTag("meals_discard_confirm").performClick()

        assertEquals(listOf(MealSlotsEvent.DismissDiscard, MealSlotsEvent.ConfirmDiscard), events)
    }
}
