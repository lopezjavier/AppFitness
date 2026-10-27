package io.github.lopezjavier.appfitness.feature.meals.defaults

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.feature.meals.RecordingMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Cards = listOf(
    MealDefaultsCardUi("breakfast", "Desayuno", emptyList(), canAdd = true),
    MealDefaultsCardUi(
        "lunch",
        "Comida",
        listOf(AssignmentUi("rice", "Arroz con pollo", 2, 612.4, setOf(1, 3, 5))),
        canAdd = true,
    ),
)

private val Comidas = listOf(
    DefaultComidaOptionUi("eggs", "Huevos", 1, 143.0),
    DefaultComidaOptionUi("rice", "Arroz con pollo", 2, 612.4),
)

private fun content(
    meals: List<MealDefaultsCardUi> = Cards,
    comidas: List<DefaultComidaOptionUi> = Comidas,
    hasSavedComidas: Boolean = true,
    isOnline: Boolean = true,
    isSaving: Boolean = false,
    canSave: Boolean = true,
    saveError: MealDefaultsSaveError? = null,
    picker: String? = null,
    sheet: AssignSheetUi? = null,
) = MealDefaultsUiState.Content(
    meals = meals,
    comidas = comidas,
    hasSavedComidas = hasSavedComidas,
    isOnline = isOnline,
    isSaving = isSaving,
    isDirty = canSave,
    canSave = canSave,
    saved = false,
    saveError = saveError,
    picker = picker,
    sheet = sheet,
)

private fun sheet(selected: Set<Int> = (1..7).toSet(), conflicts: Map<Int, String> = emptyMap()) =
    AssignSheetUi("breakfast", "Desayuno", "eggs", "Huevos", selected, conflicts, canSave = selected.isNotEmpty())

/** US-51 default comidas editor rendered from a given state: cards, sheets, states and the events they send. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per rule or state.
class MealDefaultsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<MealDefaultsEvent>()

    private val messages = RecordingMessages()
    private var closed = 0

    private fun show(state: MealDefaultsUiState) {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    MealDefaultsScreen(state, { events += it }, onClose = { closed++ })
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun opening_showsOneCardPerMealWithItsAssignedComida() {
        show(content())

        rule.onNodeWithTag("meal_defaults_card_breakfast").assertIsDisplayed()
        rule.onNodeWithTag("meal_defaults_card_lunch").assertIsDisplayed()
        rule.onNodeWithTag(
            "meal_defaults_assignment_lunch_rice",
        ).assertTextContains("Arroz con pollo", substring = true)
        rule.onNodeWithTag(
            "meal_defaults_assignment_lunch_rice",
        ).assertTextContains("2 alimentos · 612 kcal", substring = true)
        rule.onNodeWithTag("meal_defaults_assignment_lunch_rice").assertTextContains("L X V", substring = true)
        rule.onNodeWithTag("meal_defaults_note").assertTextContains("a partir del próximo día", substring = true)
    }

    @Test
    fun tappingAnAssignment_opensItsSheet() {
        show(content())

        rule.onNodeWithTag("meal_defaults_assignment_lunch_rice").performClick()

        assertEquals(listOf<MealDefaultsEvent>(MealDefaultsEvent.OpenAssignment("lunch", "rice")), events)
    }

    @Test
    fun tappingAddComida_opensThePickerOfThatMeal() {
        show(content())

        rule.onNodeWithTag("meal_defaults_add_breakfast").performClick()

        assertEquals(listOf<MealDefaultsEvent>(MealDefaultsEvent.OpenPicker("breakfast")), events)
    }

    @Test
    fun removeIcon_removesWithoutConfirmationAndNamesTheComidaAndMeal() {
        show(content())

        rule.onNodeWithContentDescription("Quitar Arroz con pollo de Comida").performClick()

        assertEquals(listOf<MealDefaultsEvent>(MealDefaultsEvent.Remove("lunch", "rice")), events)
        rule.onNodeWithTag("meal_defaults_discard_confirm").assertDoesNotExist()
    }

    @Test
    fun everyDayAssigned_disablesAddAndSaysWhy() {
        show(content(meals = listOf(Cards[1].copy(canAdd = false))))

        rule.onNodeWithTag("meal_defaults_add_lunch").assertIsNotEnabled()
        rule.onNodeWithTag("meal_defaults_all_taken_lunch").assertTextEquals("Todos los días ya tienen una comida")
    }

    @Test
    fun addComida_isEnabledWhileThereAreFreeDays() {
        show(content())

        rule.onNodeWithTag("meal_defaults_add_lunch").assertIsEnabled()
        rule.onNodeWithTag("meal_defaults_all_taken_lunch").assertDoesNotExist()
    }

    @Test
    fun pickerSheet_listsTheSavedComidasWithNameCountAndKcal() {
        show(content(picker = "breakfast"))

        rule.onNodeWithTag("default_picker_sheet").assertIsDisplayed()
        rule.onNodeWithTag("default_picker_row_eggs").assertTextContains("1 alimento · 143 kcal", substring = true)
        rule.onNodeWithTag("default_picker_row_rice").performClick()
        assertEquals(listOf<MealDefaultsEvent>(MealDefaultsEvent.OpenAssignment("breakfast", "rice")), events)
    }

    @Test
    fun pickerSheet_withoutSavedComidas_showsTheEmptyStateAndTheHint() {
        show(content(comidas = emptyList(), hasSavedComidas = false, picker = "breakfast"))

        rule.onNodeWithText("No tienes comidas guardadas").assertIsDisplayed()
        rule.onNodeWithText("Crea una desde", substring = true).assertIsDisplayed()
    }

    @Test
    fun assignSheet_startsWithEveryDayOnAndSendsToggles() {
        show(content(sheet = sheet()))

        rule.onNodeWithTag("assign_default_sheet").assertIsDisplayed()
        for (day in 1..7) rule.onNodeWithTag("assign_default_day_$day").assertIsOn()
        rule.onNodeWithTag("assign_default_day_2").performClick()
        assertEquals(listOf<MealDefaultsEvent>(MealDefaultsEvent.ToggleDay(2)), events)
    }

    @Test
    fun assignSheet_showsConflictsAsVisibleTextAndDisablesThoseDays() {
        show(content(sheet = sheet(selected = setOf(2, 3), conflicts = mapOf(1 to "Desayuno A"))))

        rule.onNodeWithTag("assign_default_day_1").assertIsNotEnabled()
        rule.onNodeWithTag("assign_default_conflict_note").assertTextContains("Ya asignado a Desayuno A")
    }

    @Test
    fun assignSheet_withNoConflicts_hasNoConflictNote() {
        show(content(sheet = sheet()))

        rule.onNodeWithTag("assign_default_conflict_note").assertDoesNotExist()
    }

    @Test
    fun assignSheet_withNoDay_showsTheErrorAndDisablesSave() {
        show(content(sheet = sheet(selected = emptySet())))

        rule.onNodeWithTag("assign_default_day_error").assertTextEquals("Selecciona al menos un día")
        rule.onNodeWithTag("assign_default_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun assignSheet_listo_confirmsTheSheet() {
        show(content(sheet = sheet(selected = setOf(1, 3, 5))))

        rule.onNodeWithTag("assign_default_day_error").assertDoesNotExist()
        rule.onNodeWithTag("assign_default_sheet_commit").performClick()

        assertEquals(listOf<MealDefaultsEvent>(MealDefaultsEvent.ConfirmSheet), events)
    }

    @Test
    fun save_sendsSaveAndIsDisabledWithoutChanges() {
        show(content())
        rule.onNodeWithTag("meal_defaults_save").performClick()
        assertEquals(listOf<MealDefaultsEvent>(MealDefaultsEvent.Save), events)
    }

    @Test
    fun save_isDisabledWhenThereIsNothingToSave() {
        show(content(canSave = false))

        rule.onNodeWithTag("meal_defaults_save").assertIsNotEnabled()
    }

    @Test
    fun offline_showsTheTemplateHintAndDisablesSave() {
        show(content(isOnline = false, canSave = false))

        rule.onNodeWithTag("offline_hint").assertTextEquals("Sin conexión: no se puede guardar")
        rule.onNodeWithTag("meal_defaults_save").assertIsNotEnabled()
    }

    @Test
    fun saveError_isAOneShotMessageWhileTheDraftStays() {
        show(content(saveError = MealDefaultsSaveError.Failed))

        assertEquals(listOf("No se pudo guardar. Inténtalo de nuevo."), messages.shown)
        rule.onNodeWithTag("meal_defaults_save_error").assertDoesNotExist()
        rule.onNodeWithTag("meal_defaults_assignment_lunch_rice").assertIsDisplayed()
    }

    @Test
    fun loading_showsSkeletonsInsteadOfASpinner() {
        rule.mainClock.autoAdvance = false
        show(MealDefaultsUiState.Loading)

        rule.onNodeWithTag("meal_defaults_loading").assertIsDisplayed()
        rule.onNodeWithTag("meal_defaults_save").assertDoesNotExist()
    }

    @Test
    fun loadError_offersRetry() {
        show(MealDefaultsUiState.LoadError)

        rule.onNodeWithTag("meal_defaults_error").assertIsDisplayed()
        rule.onNodeWithTag("meal_defaults_retry").performClick()
        assertEquals(listOf<MealDefaultsEvent>(MealDefaultsEvent.Retry), events)
    }

    @Test
    fun backWhenDirty_asksThroughTheTemplate_keepEditingStays_discardCloses() {
        show(content())

        rule.onNodeWithTag("meal_defaults_back").performClick()
        rule.onNodeWithTag("meal_defaults_discard_dialog").assertExists()
        rule.onNodeWithTag("meal_defaults_keep_editing").performClick()
        assertEquals(0, closed)
        rule.onNodeWithTag("meal_defaults_back").performClick()
        rule.onNodeWithTag("meal_defaults_discard_confirm").performClick()

        assertEquals(1, closed)
    }

    @Test
    fun backWhenClean_closesAtOnce() {
        show(content(canSave = false))

        rule.onNodeWithTag("meal_defaults_back").performClick()

        assertEquals(1, closed)
    }

    @Test
    fun addButton_readsAnadirComidaPorDefecto() {
        show(content())

        rule.onNodeWithTag("meal_defaults_add_breakfast").assertTextContains("Añadir comida por defecto")
    }

    @Test
    fun title_isComidasPorDefecto() {
        show(content())

        rule.onNodeWithTag("meal_defaults_title").assertTextEquals("Comidas por defecto")
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp-xxhdpi")
    fun tabletWidth_centresTheContentAtTheFormMaxWidth() {
        show(content())

        val card = rule.onNodeWithTag("meal_defaults_card_breakfast").getUnclippedBoundsInRoot()
        assertTrue("card width ${card.width}", card.width <= 480.dp)
        assertTrue("card left ${card.left}", card.left > 100.dp)
    }
}
