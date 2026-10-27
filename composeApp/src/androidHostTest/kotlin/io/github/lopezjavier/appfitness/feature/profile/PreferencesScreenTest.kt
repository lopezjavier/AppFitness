package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FRAMES_INTO_THE_SNAP = 4

/** US-06 editing data and the weight, US-07 saving the goals and US-08 macro split on Preferencias. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class PreferencesScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate(2026, 10, 14)
    private val events = mutableListOf<PreferencesEvent>()

    /** 26-year-old, 180 cm, 100 kg, sedentary: TDEE 2400, so the default split reads 180/270/67 g. */
    private fun loaded(
        split: MacroSplit = MacroSplit.DEFAULT,
        saved: MacroSplit = split,
        isOnline: Boolean = true,
        isSaving: Boolean = false,
        weightFromHealthConnect: Boolean = false,
    ): PreferencesUiState.Loaded {
        val base = PreferencesForm(
            name = "Ana",
            sex = Sex.Male,
            birthDate = LocalDate(2000, 3, 1),
            heightCm = 180,
            weightKg = 100.0,
            activity = ActivityLevel.Sedentary,
            strategy = GoalStrategy.Maintenance,
            adjustment = 0,
            split = split,
        )
        return PreferencesUiState.Loaded(
            form = base,
            saved = base.copy(split = saved),
            today = today,
            isSaving = isSaving,
            isOnline = isOnline,
            weightFromHealthConnect = weightFromHealthConnect,
        )
    }

    private fun show(
        state: PreferencesUiState = loaded(),
        fontScale: Float = 1f,
        effects: Channel<PreferencesEffect> = Channel(Channel.BUFFERED),
        onOpenMeals: () -> Unit = {},
    ) {
        events.clear()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme {
                    PreferencesScreen(
                        state = state,
                        onEvent = { events += it },
                        effects = effects.receiveAsFlow(),
                        onOpenMeals = onOpenMeals,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun mealsRow_opensTheMealsEditor_withoutTouchingTheProfileDraft() {
        var opened = 0
        show(onOpenMeals = { opened++ })

        rule.onNodeWithTag("preferences_meals_row").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Nombre y orden de tus comidas del día").assertExists()
        rule.onNodeWithTag("preferences_meals_row").performClick()

        assertEquals(1, opened)
        assertTrue(events.isEmpty())
        rule.onNodeWithTag("preferences_save").assertIsNotEnabled()
    }

    @Test
    fun preferences_hasNoPersonalRecordSettingOrNotification() {
        show()

        rule.onAllNodesWithText("¡Nuevo récord!", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("notificación", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Nivel de récord", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun mealsRow_hasTheTouchTarget() {
        show()

        rule.onNodeWithTag("preferences_meals_row").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    // -- US-08 initial display ------------------------------------------------------------------------------------

    @Test
    fun initialDisplay_showsTheTotalBadgeTheBarAndTheMacroTexts() {
        show()
        rule.onNodeWithTag("preferences_macro_total").performScrollTo().assertTextEquals("TOTAL: 100 %")
        rule.onNodeWithTag("preferences_macro_bar").assertExists()
        rule.onNodeWithText("30 %").assertExists()
        rule.onNodeWithText("180 g · 720 kcal").assertExists()
        rule.onNodeWithText("45 %").assertExists()
        rule.onNodeWithText("270 g · 1.080 kcal").assertExists()
        rule.onNodeWithText("25 %").assertExists()
        rule.onNodeWithText("67 g · 600 kcal").assertExists()
    }

    @Test
    fun sliders_speakPercentGramsAndKcal() {
        show()
        rule.onNodeWithTag("preferences_macro_protein_slider").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "30 por ciento, 180 gramos, 720 kilocalorías",
            ),
        )
    }

    @Test
    fun movingASlider_sendsTheMoveEvent() {
        show()
        rule.onNodeWithTag("preferences_macro_protein_slider").performSemanticsAction(SemanticsActions.SetProgress) {
            it(35f)
        }
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.MoveMacro(Macronutrient.Protein, 35)), events)
    }

    @Test
    fun rebalancedSplit_isShown() {
        show(loaded(split = MacroSplit(35, 42, 23), saved = MacroSplit.DEFAULT))
        rule.onNodeWithText("35 %").assertExists()
        rule.onNodeWithText("42 %").assertExists()
        rule.onNodeWithText("23 %").assertExists()
        rule.onNodeWithText("210 g · 840 kcal").assertExists()
    }

    // -- US-06 editing data ---------------------------------------------------------------------------------------

    @Test
    fun weight_isAnEditableRow_withoutTheOldHint() {
        show()
        rule.onNodeWithTag("preferences_weight_row")
            .performScrollTo()
            .assertContentDescriptionEquals("Peso actual, 100,0 kg, editar")
            .assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("100,0 kg").assertExists()
        rule.onNodeWithText("Se actualiza desde Progreso").assertDoesNotExist()
    }

    @Test
    fun weightFromHealthConnect_isReadOnlyWithTheNoteAndNoEditInItsDescription() {
        show(loaded(weightFromHealthConnect = true))

        rule.onNodeWithTag("preferences_weight_row")
            .performScrollTo()
            .assertContentDescriptionEquals("Peso actual, 100,0 kg, sincronizado desde Health Connect")
            .assertIsNotEnabled()
        rule.onNodeWithTag("preferences_weight_hc_note").assertTextEquals("Sincronizado desde Health Connect")
        rule.onNodeWithTag("preferences_weight_row").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_sheet_weight").assertDoesNotExist()
    }

    @Test
    fun manualWeight_hasNoHealthConnectNote() {
        show(loaded(weightFromHealthConnect = false))

        rule.onNodeWithTag("preferences_weight_hc_note").assertDoesNotExist()
    }

    @Test
    fun weightFromHealthConnect_staysReadOnlyAfterRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                PreferencesScreen(
                    state = loaded(weightFromHealthConnect = true),
                    onEvent = {},
                    effects = Channel<PreferencesEffect>(Channel.BUFFERED).receiveAsFlow(),
                    onOpenMeals = {},
                )
            }
        }
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_weight_row").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("preferences_weight_hc_note").assertExists()
    }

    @Test
    fun weightSheet_hasTheKgAndDecimalWheels_andListoAppliesTheTenths() {
        show()
        rule.onNodeWithTag("preferences_weight_row").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_sheet_weight").assertExists()
        rule.onNodeWithTag("preferences_weight_kg_picker").assertExists()
        rule.onNodeWithTag("preferences_weight_decimal_picker").assertExists()
        assertTrue("nothing is applied while the sheet is open", events.isEmpty())

        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.SetWeight(1000)), events)
    }

    @Test
    fun name_isShownInAnEditableField_andEditsAreSent() {
        show()
        rule.onNodeWithTag("preferences_name_field").performScrollTo().assertContentDescriptionEquals("Nombre")
        rule.onNodeWithTag("preferences_name_field").performTextInput("a")
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.SetName("Anaa")), events)
    }

    @Test
    fun blankName_showsTheErrorAndDisablesGuardar() {
        val base = loaded(split = MacroSplit(35, 42, 23), saved = MacroSplit.DEFAULT)
        show(base.copy(form = base.form.copy(name = "")))
        rule.onNodeWithTag("preferences_name_field_error").performScrollTo().assertTextEquals("Escribe tu nombre")
        rule.onNodeWithTag("preferences_save").assertIsNotEnabled()
    }

    @Test
    fun tdee_usesTheNewCopy_withItsHelperLine() {
        show()
        rule.onNodeWithTag("preferences_energy_tdee").performScrollTo()
            .assertTextEquals("Gasto diario estimado: 2.400 kcal")
        rule.onNodeWithTag("preferences_energy_tdee_helper")
            .assertTextEquals("Calorías que quemas al día según tu cuerpo y tu actividad.")
    }

    @Test
    fun personalRows_haveAccessibleNames_andTheTouchTarget() {
        show()
        rule.onNodeWithTag("preferences_height_row")
            .assertContentDescriptionEquals("Altura, 180 centímetros, editar")
            .assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("preferences_birth_date_row").assertContentDescriptionEquals("Edad, 26 años, editar")
        rule.onNodeWithTag("preferences_activity_row").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun sexSelector_sendsTheSelection() {
        show()
        rule.onNodeWithTag("preferences_sex_female").performClick()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.SelectSex(Sex.Female)), events)
    }

    @Test
    fun heightSheet_appliesOnlyWithListo() {
        show()
        rule.onNodeWithTag("preferences_height_row").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_sheet_height").assertExists()
        assertTrue("nothing is applied while the sheet is open", events.isEmpty())

        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.SetHeight(180)), events)
    }

    @Test
    fun activitySheet_listsTheLevels_andListoAppliesTheChoice() {
        show()
        rule.onNodeWithTag("preferences_activity_row").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_activity_light").performClick()
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.SelectActivity(ActivityLevel.Light)), events)
    }

    @Test
    fun birthDateSheet_hasTheWheels() {
        show()
        rule.onNodeWithTag("preferences_birth_date_row").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_sheet_birth_date").assertExists()
        rule.onNodeWithTag("preferences_birth_year_picker").assertExists()
    }

    // -- US-06 edited values reach the card (T-040) ---------------------------------------------------------------

    /** Shows the screen with a minimal reducer, so a sheet's "Listo" feeds the new state back to the card. */
    private fun showStateful() {
        var state by mutableStateOf(loaded())
        events.clear()
        rule.setContent {
            AppTheme {
                PreferencesScreen(
                    state = state,
                    onEvent = { event ->
                        events += event
                        state = reduce(state, event)
                    },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun reduce(state: PreferencesUiState.Loaded, event: PreferencesEvent): PreferencesUiState.Loaded =
        when (event) {
            is PreferencesEvent.SetHeight -> state.copy(form = state.form.copy(heightCm = event.heightCm))
            is PreferencesEvent.SetWeight -> state.copy(form = state.form.copy(weightKg = event.tenths / 10.0))
            is PreferencesEvent.SetName -> state.copy(form = state.form.copy(name = event.name))
            is PreferencesEvent.SetBirthDate -> state.copy(form = state.form.copy(birthDate = event.date))
            is PreferencesEvent.SelectActivity -> state.copy(form = state.form.copy(activity = event.level))
            is PreferencesEvent.SelectSex -> state.copy(form = state.form.copy(sex = event.sex))
            PreferencesEvent.Save -> state.copy(saved = state.form)
            else -> state
        }

    private fun openSheet(rowTag: String) {
        rule.onNodeWithTag(rowTag).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun setWheel(tag: String, value: Float) {
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }

    @Test
    fun editedHeight_isShownOnTheCardAfterListo() {
        showStateful()
        openSheet("preferences_height_row")
        setWheel("preferences_height_picker", 172f)
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_height_row").assertContentDescriptionEquals("Altura, 172 centímetros, editar")
        rule.onNodeWithText("172 cm").assertExists()
    }

    @Test
    fun editedHeight_startsTheWheelAtTheNewValueWhenReopened() {
        showStateful()
        openSheet("preferences_height_row")
        setWheel("preferences_height_picker", 172f)
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()

        openSheet("preferences_height_row")

        rule.onNodeWithTag("preferences_height_picker").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "172 cm"),
        )
    }

    @Test
    fun editedHeight_byTappingANeighbourItem_isShownOnTheCard() {
        showStateful()
        openSheet("preferences_height_row")
        rule.onNodeWithTag("preferences_height_picker_item_181").performClick()
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_height_row").assertContentDescriptionEquals("Altura, 181 centímetros, editar")
    }

    @Test
    fun editedHeight_bySwipingTheWheel_isAppliedByListo() {
        showStateful()
        openSheet("preferences_height_row")
        rule.onNodeWithTag("preferences_height_picker").performTouchInput { swipeUp() }
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()

        val applied = events.filterIsInstance<PreferencesEvent.SetHeight>().single().heightCm
        assertTrue("the swiped value ($applied) must differ from the old 180", applied > 180)
        rule.onNodeWithTag("preferences_height_row")
            .assertContentDescriptionEquals("Altura, $applied centímetros, editar")
    }

    @Test
    fun listoDuringAnInProgressSwipe_appliesTheCentredHeight() {
        showStateful()
        openSheet("preferences_height_row")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("preferences_height_picker").performTouchInput { swipeUp() }
        repeat(FRAMES_INTO_THE_SNAP) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        val applied = events.filterIsInstance<PreferencesEvent.SetHeight>().single().heightCm
        assertTrue("the centred value ($applied) must differ from the old 180", applied > 180)
        rule.onNodeWithTag("preferences_height_row")
            .assertContentDescriptionEquals("Altura, $applied centímetros, editar")
    }

    @Test
    fun listoDuringAnInProgressBirthYearSwipe_appliesTheCentredYear() {
        showStateful()
        openSheet("preferences_birth_date_row")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("preferences_birth_year_picker").performTouchInput { swipeUp() }
        repeat(FRAMES_INTO_THE_SNAP) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        val applied = events.filterIsInstance<PreferencesEvent.SetBirthDate>().single().date
        assertTrue("the centred year (${applied.year}) must differ from the old 2000", applied.year > 2000)
    }

    @Test
    fun editedBirthYear_isShownOnTheCardAfterListo() {
        showStateful()
        openSheet("preferences_birth_date_row")
        setWheel("preferences_birth_year_picker", 1990f)
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_birth_date_row").assertContentDescriptionEquals("Edad, 36 años, editar")
        rule.onNodeWithText("36 años").assertExists()
    }

    @Test
    fun changingTheWheel_doesNotTouchTheCardUntilListo() {
        showStateful()
        openSheet("preferences_height_row")
        setWheel("preferences_height_picker", 172f)
        rule.waitForIdle()

        assertTrue(events.isEmpty())
        rule.onNodeWithTag("preferences_height_row").assertContentDescriptionEquals("Altura, 180 centímetros, editar")
    }

    @Test
    fun editedActivity_isShownOnTheCardAfterListo() {
        showStateful()
        openSheet("preferences_activity_row")
        rule.onNodeWithTag("preferences_activity_light").performClick()
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_activity_row").assertContentDescriptionEquals("Actividad, Ligero, editar")
    }

    @Test
    fun editedSex_isShownAsSelected() {
        showStateful()
        rule.onNodeWithTag("preferences_sex_female").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_sex_female").assertIsSelected()
    }

    @Test
    fun afterSaving_theCardStillShowsTheEditedValues() {
        showStateful()
        openSheet("preferences_height_row")
        setWheel("preferences_height_picker", 172f)
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_save").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_height_row").assertContentDescriptionEquals("Altura, 172 centímetros, editar")
    }

    @Test
    fun editedWeight_isShownOnTheCardAfterListo_andTheTargetFollows() {
        showStateful()
        openSheet("preferences_weight_row")
        setWheel("preferences_weight_kg_picker", 90f)
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_weight_row").assertContentDescriptionEquals("Peso actual, 90,0 kg, editar")
        rule.onNodeWithText("90,0 kg").assertExists()
    }

    @Test
    fun afterSaving_theCardStillShowsTheNewWeight() {
        showStateful()
        openSheet("preferences_weight_row")
        setWheel("preferences_weight_kg_picker", 90f)
        rule.onNodeWithTag("preferences_sheet_done").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_save").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_weight_row").assertContentDescriptionEquals("Peso actual, 90,0 kg, editar")
    }

    @Test
    fun aLatestWeightFromAnotherSource_isShownAndOpensTheSameEditor() {
        val base = loaded()
        show(base.copy(form = base.form.copy(weightKg = 79.4), saved = base.form.copy(weightKg = 79.4)))
        rule.onNodeWithTag("preferences_weight_row")
            .performScrollTo()
            .assertContentDescriptionEquals("Peso actual, 79,4 kg, editar")
            .performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_sheet_weight").assertExists()
    }

    // -- US-07 saving the goals on Preferencias -------------------------------------------------------------------

    @Test
    fun energyCard_showsTheTdee() {
        show()
        rule.onNodeWithTag("preferences_energy_tdee").performScrollTo().assertExists()
        rule.onNodeWithTag("preferences_energy_target").assertExists()
    }

    @Test
    fun save_isDisabledWhenNothingChanged() {
        show()
        rule.onNodeWithTag("preferences_save").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun save_isEnabledWhenDirty_andSendsSave() {
        show(loaded(split = MacroSplit(35, 42, 23), saved = MacroSplit.DEFAULT))
        rule.onNodeWithTag("preferences_save").assertIsEnabled().performClick()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.Save), events)
    }

    @Test
    fun save_isDisabledOffline() {
        show(loaded(split = MacroSplit(35, 42, 23), saved = MacroSplit.DEFAULT, isOnline = false))
        rule.onNodeWithTag("preferences_save").assertIsNotEnabled()
    }

    @Test
    fun save_isDisabledWhileSaving() {
        rule.mainClock.autoAdvance = false
        show(loaded(split = MacroSplit(35, 42, 23), saved = MacroSplit.DEFAULT, isSaving = true))
        rule.onNodeWithTag("preferences_save").assertIsNotEnabled()
    }

    @Test
    fun saveFooter_staysVisibleWhileTheCardsScroll() {
        show()
        rule.onNodeWithTag("preferences_macro_fat_slider").performScrollTo()
        rule.onNodeWithTag("preferences_save").assertIsDisplayed()
    }

    @Test
    fun savedEffect_showsTheConfirmationSnackbar() {
        val effects = Channel<PreferencesEffect>(Channel.BUFFERED)
        show(effects = effects)
        effects.trySend(PreferencesEffect.Saved)
        rule.waitForIdle()
        rule.onNodeWithText("Cambios guardados").assertIsDisplayed()
    }

    @Test
    fun failedEffect_showsTheErrorMessages() {
        val effects = Channel<PreferencesEffect>(Channel.BUFFERED)
        show(effects = effects)
        effects.trySend(PreferencesEffect.SaveFailed(offline = false))
        rule.waitForIdle()
        rule.onNodeWithText("No se pudo guardar. Inténtalo de nuevo").assertIsDisplayed()
    }

    // -- states -----------------------------------------------------------------------------------------------------

    @Test
    fun loading_showsTheSkeleton() {
        rule.mainClock.autoAdvance = false
        show(PreferencesUiState.Loading)
        rule.onNodeWithTag("preferences_loading").assertExists()
        rule.onNodeWithTag("preferences_content").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsRetry_andRetryIsSent() {
        show(PreferencesUiState.LoadFailed)
        rule.onNodeWithTag("preferences_error_action").assertTextEquals("Reintentar").performClick()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.Retry), events)
    }

    @Test
    fun stateChange_fromLoadingToLoaded_replacesTheSkeleton() {
        var state by mutableStateOf<PreferencesUiState>(PreferencesUiState.LoadFailed)
        rule.setContent { AppTheme { PreferencesScreen(state, {}) } }
        rule.onNodeWithTag("preferences_error").assertExists()
        state = loaded()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_content").assertExists()
        rule.onNodeWithTag("preferences_error").assertDoesNotExist()
    }

    // -- layouts ----------------------------------------------------------------------------------------------------

    @Test
    @Config(qualifiers = "w840dp-h1000dp-mdpi")
    fun tablet_usesTwoColumns_andKeepsGuardarVisible() {
        show()
        val personal = rule.onNodeWithTag("preferences_sex_male").getUnclippedBoundsInRoot()
        val macros = rule.onNodeWithTag("preferences_macro_bar").getUnclippedBoundsInRoot()
        assertTrue("macro card is to the right of the personal card", macros.left >= personal.right)
        rule.onNodeWithTag("preferences_save").assertIsDisplayed()
    }

    @Test
    fun largeFont_keepsTheSaveButtonTheMacroTextsAndTheSheetButton() {
        show(fontScale = 1.3f)
        rule.onNodeWithTag("preferences_save").assertIsDisplayed()
        rule.onNodeWithText("180 g · 720 kcal").assertExists()
        rule.onNodeWithTag("preferences_height_row").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_sheet_done").assertHeightIsAtLeast(48.dp)
    }
}
