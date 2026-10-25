package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
    error: MealSlotError? = null,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
    isEditing: Boolean = false,
    deleteBlocked: Boolean = false,
    canDelete: Boolean = true,
) = MealSlotRowUi(
    key, label, error, canMoveUp, canMoveDown,
    isNew = false, isEditing = isEditing, deleteBlocked = deleteBlocked, canDelete = canDelete,
)

private val Rows = listOf(
    rowUi("breakfast", "Desayuno", canMoveUp = false),
    rowUi("lunch", "Comida"),
    rowUi("dinner", "Cena", canMoveDown = false),
)

private fun editing(
    rows: List<MealSlotRowUi> = Rows,
    onlyOneMeal: Boolean = false,
    isOnline: Boolean = true,
    isSaving: Boolean = false,
    showDiscardConfirm: Boolean = false,
    saveError: MealSlotsSaveError? = null,
    canSave: Boolean = true,
    isDirty: Boolean = true,
) = MealSlotsUiState.Editing(
    rows = rows,
    onlyOneMeal = onlyOneMeal,
    isOnline = isOnline,
    isSaving = isSaving,
    isDirty = isDirty,
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

    private fun show(state: MealSlotsUiState) {
        rule.setContent {
            AppTheme { MealSlotsScreen(state, { events += it }) }
        }
        rule.waitForIdle()
    }

    @Test
    fun rows_showEveryMealWithItsName() {
        show(editing(Rows + rowUi("extra", "Extra")))

        rule.onNodeWithTag("meals_name_breakfast").assertTextContains("Desayuno")
        rule.onNodeWithTag("meals_name_extra").assertTextContains("Extra")
        rule.onNodeWithTag("meals_row_extra").assertExists()
    }

    @Test
    fun compactRow_hasTheFourIconActions_andNoSwitch() {
        show(editing())

        listOf("edit", "up", "down", "delete").forEach { rule.onNodeWithTag("meals_${it}_lunch").assertExists() }
        rule.onNodeWithTag("meals_handle_lunch").assertExists()
        rule.onNodeWithTag("meals_switch_lunch").assertDoesNotExist()
        rule.onNodeWithTag("meals_add_after_lunch").assertDoesNotExist()
        rule.onNodeWithTag("meals_label_lunch").assertDoesNotExist()
    }

    @Test
    fun rowActions_describeTheMeal() {
        show(editing())

        rule.onNodeWithContentDescription("Renombrar Comida").assertExists()
        rule.onNodeWithContentDescription("Eliminar Comida").assertExists()
        rule.onNodeWithContentDescription("Subir Comida").assertExists()
        rule.onNodeWithContentDescription("Bajar Comida").assertExists()
    }

    @Test
    fun renameIcon_sendsEdit() {
        show(editing())

        rule.onNodeWithTag("meals_edit_lunch").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Edit("lunch")), events)
    }

    @Test
    fun editingRow_showsTheNameField_andHidesTheIcons() {
        show(editing(listOf(rowUi("lunch", "Comida", isEditing = true))))

        rule.onNodeWithTag("meals_label_lunch").assertTextContains("Comida")
        rule.onNodeWithTag("meals_delete_lunch").assertDoesNotExist()
        rule.onNodeWithTag("meals_edit_lunch").assertDoesNotExist()
    }

    @Test
    fun deleteIcon_sendsDelete() {
        show(editing())

        rule.onNodeWithTag("meals_delete_lunch").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Delete("lunch")), events)
    }

    @Test
    fun deleteBlocked_showsTheHasEntriesHint_andKeepsTheRow() {
        show(editing(listOf(rowUi("lunch", "Comida", deleteBlocked = true))))

        rule.onNodeWithTag("meals_hint_lunch")
            .assertTextEquals("Esta comida tiene alimentos registrados y no se puede eliminar")
        rule.onNodeWithTag("meals_row_lunch").assertExists()
    }

    @Test
    fun singleMeal_disablesTrash_andShowsTheLastMealHint() {
        show(editing(listOf(rowUi("lunch", "Comida", canDelete = false)), onlyOneMeal = true))

        rule.onNodeWithTag("meals_delete_lunch").assertIsNotEnabled()
        rule.onNodeWithTag("meals_last_meal_hint").assertTextEquals("Debe quedar al menos una comida")
    }

    @Test
    fun severalMeals_showNoLastMealHint() {
        show(editing())

        rule.onNodeWithTag("meals_last_meal_hint").assertDoesNotExist()
    }

    @Test
    fun offline_disablesTheTrashAndAdd() {
        val rows = listOf(rowUi("lunch", "Comida", canDelete = false), rowUi("dinner", "Cena", canDelete = false))
        show(editing(rows, isOnline = false, canSave = false))

        rule.onNodeWithTag("meals_delete_lunch").assertIsNotEnabled()
        rule.onNodeWithTag("meals_add").assertIsNotEnabled()
    }

    @Test
    fun narrowWindow_keepsEveryIconOnScreen_withALongName() {
        show(editing(listOf(rowUi("x", "Una comida con un nombre muy largo que no cabe en una línea"))))

        rule.onNodeWithTag("meals_delete_x").assertIsDisplayed()
        rule.onNodeWithTag("meals_edit_x").assertIsDisplayed()
    }

    @Test
    fun typingInAName_sendsSetLabel() {
        show(editing(listOf(rowUi("lunch", "Comida", isEditing = true))))

        rule.onNodeWithTag("meals_label_lunch").performTextReplacement("Almuerzo")

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.SetLabel("lunch", "Almuerzo")), events)
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
    fun addButton_appendsAMeal() {
        show(editing())

        rule.onNodeWithTag("meals_add").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Add), events)
    }

    @Test
    fun blankName_showsTheFieldError() {
        show(editing(listOf(rowUi("x", "", error = MealSlotError.Blank, isEditing = true)), canSave = false))

        rule.onNodeWithTag("meals_label_x_error").assertTextEquals("Escribe un nombre")
        rule.onNodeWithTag("meals_save").assertIsNotEnabled()
    }

    @Test
    fun tooLongName_showsTheLengthError() {
        val row = rowUi("x", "n".repeat(51), error = MealSlotError.TooLong, isEditing = true)
        show(editing(listOf(row), canSave = false))

        rule.onNodeWithTag("meals_label_x_error").assertTextEquals("Máximo 50 caracteres")
    }

    @Test
    fun save_isEnabledWhenSavable_andSendsSave() {
        show(editing())

        rule.onNodeWithTag("meals_save").assertIsEnabled().performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Save), events)
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

        rule.onNodeWithTag("meals_up_lunch").assertIsNotEnabled()
        rule.onNodeWithTag("meals_add").assertIsNotEnabled()
        rule.onNodeWithTag("meals_save").assertIsNotEnabled()
    }

    @Test
    fun saveError_isShownAboveTheSaveButton_withTheDraftKept() {
        show(editing(saveError = MealSlotsSaveError.Failed))

        rule.onNodeWithTag("meals_save_error")
            .assertTextEquals("No se pudieron guardar las comidas. Inténtalo de nuevo.")
        rule.onNodeWithTag("meals_name_lunch").assertTextContains("Comida")
    }

    @Test
    fun rejectedSave_showsTheError() {
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

    @Test
    fun defaultsButton_isDisabledWhileDirty_andSaysWhy() {
        show(editing(isDirty = true))

        rule.onNodeWithTag("meal_slots_defaults_button").assertIsNotEnabled()
        rule.onNodeWithTag("meal_slots_defaults_hint")
            .assertTextEquals("Guarda los cambios de las comidas para continuar")
    }

    @Test
    fun defaultsButton_isEnabledWhenClean_andOpensTheEditor() {
        var opened = 0
        rule.setContent {
            AppTheme { MealSlotsScreen(editing(isDirty = false), { events += it }, onOpenDefaults = { opened++ }) }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("meal_slots_defaults_button").assertIsEnabled().performClick()

        assertEquals(1, opened)
        rule.onNodeWithTag("meal_slots_defaults_hint").assertDoesNotExist()
    }
}
