package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun rowUi(
    key: String,
    label: String,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
    inUse: Boolean = false,
    isLastMeal: Boolean = false,
) = MealSlotRowUi(
    key,
    label,
    canMoveUp,
    canMoveDown,
    isNew = false,
    inUse = inUse,
    isLastMeal = isLastMeal,
    canDelete = !inUse && !isLastMeal,
)

private val Rows = listOf(
    rowUi("breakfast", "Desayuno", canMoveUp = false),
    rowUi("lunch", "Comida"),
    rowUi("dinner", "Cena", canMoveDown = false),
)

private fun editing(
    rows: List<MealSlotRowUi> = Rows,
    sheet: MealNameSheet? = null,
    isOnline: Boolean = true,
    isSaving: Boolean = false,
    saveError: MealSlotsSaveError? = null,
    canSave: Boolean = true,
    isDirty: Boolean = true,
) = MealSlotsUiState.Editing(
    rows = rows,
    sheet = sheet,
    isOnline = isOnline,
    isSaving = isSaving,
    isDirty = isDirty,
    saveError = saveError,
    canSave = canSave,
    canNavigateToDefaults = !isDirty,
)

/** US-56 meals editor rendered from a given state: rows, Name sheet, states and the events they send. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per rule or state.
class MealSlotsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<MealSlotsEvent>()
    private val messages = RecordingMessages()
    private var closed = 0
    private var defaultsOpened = 0

    private fun show(state: MealSlotsUiState) {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    MealSlotsScreen(state, {
                        events += it
                    }, onOpenDefaults = { defaultsOpened++ }, onClose = { closed++ })
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun title_isComidasDelDia() {
        show(editing())

        rule.onNodeWithTag("meals_title").assertTextEquals("Comidas del día")
    }

    @Test
    fun rows_showEveryMealWithItsName() {
        show(editing(Rows + rowUi("extra", "Extra")))

        rule.onNodeWithTag("meals_name_breakfast").assertTextContains("Desayuno")
        rule.onNodeWithTag("meals_name_extra").assertTextContains("Extra")
        rule.onNodeWithTag("meals_row_extra").assertExists()
    }

    @Test
    fun row_hasHandleAndTrash_andNoEditUpDownIcons() {
        show(editing())

        rule.onNodeWithTag("meals_handle_lunch").assertExists()
        rule.onNodeWithTag("meals_delete_lunch").assertExists()
        listOf("edit", "up", "down").forEach { rule.onNodeWithTag("meals_${it}_lunch").assertDoesNotExist() }
        rule.onNodeWithTag("meals_label_lunch").assertDoesNotExist()
    }

    @Test
    fun trash_isNamedQuitar_becauseItOnlyStagesTheRemoval() {
        show(editing())

        rule.onNodeWithContentDescription("Quitar Comida").assertExists()
        rule.onNodeWithContentDescription("Eliminar Comida").assertDoesNotExist()
    }

    @Test
    fun trash_sendsDelete() {
        show(editing())

        rule.onNodeWithTag("meals_delete_lunch").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Delete("lunch")), events)
    }

    @Test
    fun tappingTheName_sendsRename() {
        show(editing())

        rule.onNodeWithTag("meals_name_lunch").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Rename("lunch")), events)
    }

    @Test
    fun addButton_readsAnadirComidaDelDia_andSendsAdd() {
        show(editing())

        rule.onNodeWithTag("meals_add").assertTextContains("Añadir comida del día")
        rule.onNodeWithTag("meals_add").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Add), events)
    }

    @Test
    fun mealInUse_disablesTrashAndShowsTheReasonInTheRow() {
        show(editing(listOf(rowUi("lunch", "Comida", inUse = true), rowUi("dinner", "Cena"))))

        rule.onNodeWithTag("meals_delete_lunch").assertIsNotEnabled()
        rule.onNodeWithTag("meals_hint_lunch").assertTextEquals("Tiene alimentos registrados: no se puede quitar")
        assertTrue(rule.onNodeWithTag("meals_hint_lunch").getUnclippedBoundsInRoot().width > 150.dp)
        rule.onNodeWithTag("meals_hint_dinner").assertDoesNotExist()
    }

    @Test
    fun lastMeal_disablesTrashAndShowsTheReasonInTheRow() {
        show(editing(listOf(rowUi("lunch", "Comida", isLastMeal = true))))

        rule.onNodeWithTag("meals_delete_lunch").assertIsNotEnabled()
        rule.onNodeWithTag("meals_hint_lunch").assertTextEquals("Debe quedar al menos una comida del día")
    }

    @Test
    fun defaultsRow_whileDirty_isDisabledAndExplainsWhy() {
        show(editing(isDirty = true))

        rule.onNodeWithTag("meal_slots_defaults_button").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("meal_slots_defaults_hint")
            .assertTextEquals("Guarda los cambios para abrir las comidas por defecto")
        assertEquals(0, defaultsOpened)
    }

    @Test
    fun defaultsRow_whenClean_opensTheDefaults() {
        show(editing(isDirty = false, canSave = false))

        rule.onNodeWithTag("meal_slots_defaults_hint").assertDoesNotExist()
        rule.onNodeWithTag("meal_slots_defaults_button").assertIsEnabled().performClick()

        assertEquals(1, defaultsOpened)
    }

    @Test
    fun createSheet_opensFocusedWithAnadirFooter() {
        show(editing(sheet = MealNameSheet.Create))

        rule.onNodeWithTag("meal_name_sheet_title").assertTextEquals("Nueva comida del día")
        rule.onNodeWithTag("meal_name_sheet_input").assertIsFocused()
        rule.onNodeWithTag("meal_name_sheet_commit").assertTextContains("Añadir")
    }

    @Test
    fun createSheet_submitsTheTrimmedName() {
        show(editing(sheet = MealNameSheet.Create))

        rule.onNodeWithTag("meal_name_sheet_input").performTextInput("  Pre-entreno ")
        rule.onNodeWithTag("meal_name_sheet_commit").performClick()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.SubmitName("Pre-entreno")), events)
    }

    @Test
    fun createSheet_refusesADuplicateInAnyCase_inline() {
        show(editing(sheet = MealNameSheet.Create))

        rule.onNodeWithTag("meal_name_sheet_input").performTextInput("  cOMIDA ")

        rule.onNodeWithTag("meal_name_sheet_input_error")
            .assertTextEquals("Ya existe una comida del día con ese nombre")
        rule.onNodeWithTag("meal_name_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun renameSheet_startsWithTheCurrentName_andReadsListo() {
        show(editing(sheet = MealNameSheet.Rename("lunch", "Comida")))

        rule.onNodeWithTag("meal_name_sheet_title").assertTextEquals("Renombrar comida del día")
        rule.onNodeWithTag("meal_name_sheet_input").assertTextContains("Comida")
        rule.onNodeWithTag("meal_name_sheet_commit").assertTextContains("Listo").assertIsEnabled()
    }

    @Test
    fun nameSheet_isNotGatedByConnectivity() {
        show(editing(sheet = MealNameSheet.Create, isOnline = false))

        rule.onNodeWithTag("meal_name_sheet_input").performTextInput("Extra")

        rule.onNodeWithTag("meal_name_sheet_commit").assertIsEnabled()
        rule.onNodeWithTag("meal_name_sheet_commit_hint").assertDoesNotExist()
    }

    @Test
    fun closingTheSheet_sendsDismiss() {
        show(editing(sheet = MealNameSheet.Create))

        rule.onNodeWithTag("meal_name_sheet_close").performClick()
        rule.waitForIdle()

        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.DismissSheet), events)
    }

    @Test
    fun save_sendsSave_andIsDisabledWhenNotDirty() {
        show(editing())
        rule.onNodeWithTag("meals_save").performClick()
        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Save), events)
    }

    @Test
    fun save_isDisabledWithoutChanges() {
        show(editing(isDirty = false, canSave = false))

        rule.onNodeWithTag("meals_save").assertIsNotEnabled()
    }

    @Test
    fun offline_showsTheTemplateHintAndDisablesSave() {
        show(editing(isOnline = false, canSave = false))

        rule.onNodeWithTag("offline_hint").assertTextEquals("Sin conexión: no se puede guardar")
        rule.onNodeWithTag("meals_save").assertIsNotEnabled()
    }

    @Test
    fun saveError_isAOneShotMessage_andTheDraftStays() {
        show(editing(saveError = MealSlotsSaveError.Failed))

        assertEquals(listOf("No se pudo guardar. Inténtalo de nuevo."), messages.shown)
        rule.onNodeWithTag("meals_row_lunch").assertIsDisplayed()
        rule.onNodeWithTag("meals_save_error").assertDoesNotExist()
    }

    @Test
    fun rejectedSave_saysToReviewTheMeals() {
        show(editing(saveError = MealSlotsSaveError.Rejected))

        assertEquals(listOf("No se pudo guardar: revisa las comidas del día."), messages.shown)
    }

    @Test
    fun backWhenDirty_asksThroughTheTemplate_andDiscardCloses() {
        show(editing())

        rule.onNodeWithTag("meals_back").performClick()
        rule.onNodeWithTag("meals_discard_dialog").assertExists()
        rule.onNodeWithTag("meals_discard_confirm").performClick()

        assertEquals(1, closed)
    }

    @Test
    fun backWhenClean_closesAtOnce() {
        show(editing(isDirty = false, canSave = false))

        rule.onNodeWithTag("meals_back").performClick()

        assertEquals(1, closed)
        rule.onNodeWithTag("meals_discard_dialog").assertDoesNotExist()
    }

    @Test
    fun loading_showsSkeletons() {
        rule.mainClock.autoAdvance = false
        show(MealSlotsUiState.Loading)

        rule.onNodeWithTag("meals_loading").assertIsDisplayed()
        rule.onNodeWithTag("meals_save").assertDoesNotExist()
    }

    @Test
    fun loadError_offersRetry() {
        show(MealSlotsUiState.LoadError)

        rule.onNodeWithText("No se pudieron cargar tus comidas").assertIsDisplayed()
        rule.onNodeWithTag("meals_error_action").performClick()
        assertEquals(listOf<MealSlotsEvent>(MealSlotsEvent.Retry), events)
    }
}
