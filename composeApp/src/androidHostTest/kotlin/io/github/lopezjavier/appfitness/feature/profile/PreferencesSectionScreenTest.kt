package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
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

/**
 * US-56 on the Cooking and Training editors: each is an [io.github.lopezjavier.appfitness.designsystem.AppEditorScreen]
 * whose "Guardar" is enabled only by a change in its own section. The "Datos personales" editor is covered by
 * `PreferencesScreenTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h900dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class PreferencesSectionScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<PreferencesEvent>()
    private val messages = RecordingMessages()
    private val haptics = CountingHaptics()
    private var closes = 0
    private var state by mutableStateOf<PreferencesUiState>(PreferencesUiState.Loading)

    private val base = PreferencesForm(
        name = "Ana",
        sex = Sex.Male,
        birthDate = LocalDate(2000, 3, 1),
        heightCm = 180,
        weightKg = 100.0,
        activity = ActivityLevel.Sedentary,
    )

    private fun loaded(
        section: PreferencesSection,
        form: PreferencesForm = base,
        isOnline: Boolean = true,
        isSaving: Boolean = false,
        isSaved: Boolean = false,
    ) = PreferencesUiState.Loaded(
        form = form,
        saved = base,
        section = section,
        today = LocalDate(2026, 10, 14),
        isSaving = isSaving,
        isOnline = isOnline,
        isSaved = isSaved,
    )

    private val dirtyCooking = base.copy(foodPreferences = FoodPreferencesForm(griddleText = "7"))

    private fun show(
        section: PreferencesSection,
        initial: PreferencesUiState = loaded(section),
        effects: Channel<PreferencesEffect> = Channel(Channel.BUFFERED),
    ) {
        events.clear()
        state = initial
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages, LocalAppHaptics provides haptics) {
                    PreferencesSectionScreen(
                        section = section,
                        state = state,
                        onEvent = { events += it },
                        onClose = { closes++ },
                        effects = effects.receiveAsFlow(),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tag(section: PreferencesSection) = "preferences_${section.key}"

    // -- titles ------------------------------

    private fun assertTitle(section: PreferencesSection, title: String) {
        show(section)
        rule.onNodeWithTag("${tag(section)}_title").assertTextEquals(title)
    }

    @Test
    fun cookingEditor_isTitledCocina() = assertTitle(PreferencesSection.Cooking, "Cocina")

    @Test
    fun trainingEditor_isTitledRegistroDeSeries() = assertTitle(PreferencesSection.Training, "Registro de series")

    // -- Guardar per section ------------------------------

    private fun dirtyState(section: PreferencesSection): PreferencesUiState = loaded(
        section,
        when (section) {
            PreferencesSection.Cooking -> dirtyCooking
            PreferencesSection.Training -> base.copy(warmUpIncluded = true)
            PreferencesSection.PersonalData -> base.copy(name = "Eva")
            PreferencesSection.Reminders -> base.copy(weightReminder = false)
            PreferencesSection.MyTraining -> base.copy(intention = TrainingIntention.Strength)
        },
    )

    private fun assertCleanSaveDisabled(section: PreferencesSection) {
        show(section)
        rule.onNodeWithTag("${tag(section)}_save").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun nothingChanged_keepsGuardarDisabled_inCooking() = assertCleanSaveDisabled(PreferencesSection.Cooking)

    @Test
    fun nothingChanged_keepsGuardarDisabled_inTraining() = assertCleanSaveDisabled(PreferencesSection.Training)

    @Test
    fun editingCooking_enablesGuardar_andSendsSave() {
        show(PreferencesSection.Cooking, dirtyState(PreferencesSection.Cooking))
        rule.onNodeWithTag("preferences_cooking_save").assertIsEnabled().performClick()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.Save), events)
    }

    @Test
    fun editingTraining_enablesGuardar() {
        show(PreferencesSection.Training, dirtyState(PreferencesSection.Training))
        rule.onNodeWithTag("preferences_training_save").assertIsEnabled()
    }

    @Test
    fun anEditOfAnotherSection_doesNotEnableGuardar() {
        show(PreferencesSection.Cooking, loaded(PreferencesSection.Cooking, base.copy(warmUpIncluded = true)))
        rule.onNodeWithTag("preferences_cooking_save").assertIsNotEnabled()
    }

    @Test
    fun invalidCookingField_keepsGuardarDisabled() {
        show(
            PreferencesSection.Cooking,
            loaded(PreferencesSection.Cooking, base.copy(foodPreferences = FoodPreferencesForm(griddleText = ""))),
        )
        rule.onNodeWithTag("preferences_cooking_save").assertIsNotEnabled()
    }

    // -- one Confirm and one close ------------------------------

    @Test
    fun saved_vibratesOneConfirm_andClosesOnce() {
        show(PreferencesSection.Cooking, dirtyState(PreferencesSection.Cooking))
        state = loaded(PreferencesSection.Cooking, dirtyCooking, isSaved = true)
        rule.waitForIdle()
        assertEquals(1, haptics.confirms)
        assertEquals(1, closes)
        assertEquals(0, haptics.rejects)
    }

    // -- back and discard ------------------------------

    @Test
    fun backWhenClean_leavesSilently() {
        show(PreferencesSection.Cooking)
        rule.onNodeWithTag("preferences_cooking_back").performClick()
        assertEquals(1, closes)
        rule.onNodeWithTag("preferences_cooking_discard_dialog").assertDoesNotExist()
    }

    @Test
    fun backWhenDirty_asksToDiscard_andKeepEditingStays() {
        show(PreferencesSection.Cooking, dirtyState(PreferencesSection.Cooking))
        rule.onNodeWithTag("preferences_cooking_back").performClick()
        rule.onNodeWithTag("preferences_cooking_discard_dialog").assertExists()
        rule.onNodeWithTag("preferences_cooking_keep_editing").performClick()
        rule.waitForIdle()
        assertEquals(0, closes)
        rule.onNodeWithTag("preferences_cooking_discard_dialog").assertDoesNotExist()
    }

    @Test
    fun backWhenDirty_discardLeaves() {
        show(PreferencesSection.Cooking, dirtyState(PreferencesSection.Cooking))
        rule.onNodeWithTag("preferences_cooking_back").performClick()
        rule.onNodeWithTag("preferences_cooking_discard_confirm").performClick()
        rule.waitForIdle()
        assertEquals(1, closes)
    }

    // -- offline and failures ------------------------------

    @Test
    fun offline_disablesGuardar_showsTheHint_andATapVibratesOneReject() {
        show(PreferencesSection.Cooking, loaded(PreferencesSection.Cooking, dirtyCooking, isOnline = false))
        rule.onNodeWithTag("preferences_cooking_save").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("offline_hint").assertTextEquals("Sin conexión: no se puede guardar.")
        assertEquals(1, haptics.rejects)
        assertTrue(events.isEmpty())
    }

    @Test
    fun failedSave_showsOneMessage_vibratesReject_andStaysOpen() {
        val effects = Channel<PreferencesEffect>(Channel.BUFFERED)
        show(PreferencesSection.Cooking, dirtyState(PreferencesSection.Cooking), effects)
        effects.trySend(PreferencesEffect.SaveFailed(offline = false))
        rule.waitForIdle()
        assertEquals(listOf("No se pudo guardar. Inténtalo de nuevo."), messages.shown)
        assertEquals(1, haptics.rejects)
        assertEquals(0, closes)
        rule.onNodeWithTag("preferences_cooking_save").assertIsEnabled()
    }

    @Test
    fun offlineFailure_saysThereIsNoConnection() {
        val effects = Channel<PreferencesEffect>(Channel.BUFFERED)
        show(PreferencesSection.Cooking, dirtyState(PreferencesSection.Cooking), effects)
        effects.trySend(PreferencesEffect.SaveFailed(offline = true))
        rule.waitForIdle()
        assertEquals(listOf("Sin conexión: no se puede guardar."), messages.shown)
    }

    // -- content ------------------------------

    @Test
    fun cooking_showsItsThreeFields_withoutACardTitleOfItsOwn() {
        show(PreferencesSection.Cooking)
        listOf("griddle", "air_fryer", "fried").forEach {
            rule.onNodeWithTag("preferences_cooking_$it").performScrollTo().assertExists()
        }
    }

    @Test
    fun training_showsTheSwitches_theSelectorAndTheCalculators() {
        show(PreferencesSection.Training)
        rule.onNodeWithTag("preferences_warm_up_switch").performScrollTo().assertExists()
        rule.onNodeWithTag("preferences_auto_update_switch").performScrollTo().assertExists()
        rule.onNodeWithTag("preferences_previous_mode").performScrollTo().assertExists()
    }

    // -- states ------------------------------

    @Test
    fun loading_showsTheSkeleton() {
        rule.mainClock.autoAdvance = false
        show(PreferencesSection.Cooking, PreferencesUiState.Loading)
        rule.onNodeWithTag("preferences_loading").assertExists()
        rule.onNodeWithTag("preferences_content").assertDoesNotExist()
    }

    @Test
    fun loadFailed_showsRetry_andRetryIsSent() {
        show(PreferencesSection.Cooking, PreferencesUiState.LoadFailed)
        rule.onNodeWithTag("preferences_error_action").assertTextEquals("Reintentar").performClick()
        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.Retry), events)
    }

    @Test
    fun stateChange_fromLoadFailedToLoaded_replacesTheError() {
        show(PreferencesSection.Cooking, PreferencesUiState.LoadFailed)
        rule.onNodeWithTag("preferences_error").assertExists()
        state = loaded(PreferencesSection.Cooking)
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_content").assertExists()
        rule.onNodeWithTag("preferences_error").assertDoesNotExist()
    }

    // -- Avisos (US-57, US-142) -----------------------------

    @Test
    fun remindersEditor_isTitledAvisos() {
        show(PreferencesSection.Reminders)

        rule.onNodeWithTag("preferences_avisos_title").assertTextEquals("Avisos")
    }

    /** US-142: Avisos holds the four notice switches and no check-in switch, day row or picker. */
    @Test
    fun avisos_showsTheFourSwitches_andNoCheckInRow() {
        show(PreferencesSection.Reminders)

        listOf(
            "preferences_reminders_switch_weight",
            "preferences_reminders_switch_circumference",
            "preferences_switch_week_summary",
            "preferences_switch_block_end",
        ).forEach { rule.onNodeWithTag(it).assertExists() }
        listOf(
            "preferences_reminders_switch_checkin",
            "preferences_reminders_checkin_help",
            "preferences_checkin_weekday_row",
            "preferences_checkin_day_help",
        ).forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
        rule.onNodeWithText("Día del check-in", ignoreCase = true).assertDoesNotExist()
        rule.onNodeWithText("Check-in semanal", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun reminders_switchesRenderTheFormValues() {
        val form = base.copy(weightReminder = true, circumferenceReminder = false)
        show(PreferencesSection.Reminders, loaded(PreferencesSection.Reminders, form))

        rule.onNodeWithTag("preferences_reminders_switch_weight").assertIsOn()
        rule.onNodeWithTag("preferences_reminders_switch_circumference").assertIsOff()
        rule.onNodeWithText("Recordatorio de peso").assertIsDisplayed()
        rule.onNodeWithText("Si pasan 2 días sin pesarte.").assertIsDisplayed()
        rule.onNodeWithText("Si pasan 2 semanas sin medirte.").assertIsDisplayed()
    }

    @Test
    fun reminders_weekSummarySwitch_isOnAndSendsItsKind() {
        show(PreferencesSection.Reminders)

        rule.onNodeWithTag("preferences_switch_week_summary").assertIsOn()
        rule.onNodeWithText("Resumen semanal").assertIsDisplayed()
        rule.onNodeWithTag("preferences_switch_week_summary").performClick()

        assertEquals(
            listOf<PreferencesEvent>(PreferencesEvent.SetReminderEnabled(NotificationKind.WeekSummary, false)),
            events,
        )
    }

    @Test
    fun reminders_blockEndSwitch_isOnAndSendsItsKind() {
        show(PreferencesSection.Reminders)

        rule.onNodeWithTag("preferences_switch_block_end").performScrollTo().assertIsOn()
        rule.onNodeWithText("Bloque terminado").assertIsDisplayed()
        rule.onNodeWithTag("preferences_switch_block_end").performClick()

        assertEquals(
            listOf<PreferencesEvent>(PreferencesEvent.SetReminderEnabled(NotificationKind.BlockEnd, false)),
            events,
        )
    }

    @Test
    fun reminders_togglingASwitch_sendsSetReminderEnabled_withoutSaving() {
        show(PreferencesSection.Reminders)

        rule.onNodeWithTag("preferences_reminders_switch_weight").performClick()
        rule.onNodeWithTag("preferences_reminders_switch_circumference").performClick()

        assertEquals(
            listOf<PreferencesEvent>(
                PreferencesEvent.SetReminderEnabled(NotificationKind.Weight, false),
                PreferencesEvent.SetReminderEnabled(NotificationKind.Circumference, false),
            ),
            events,
        )
    }

    @Test
    fun reminders_changeEnablesGuardar_andSendsSave() {
        show(PreferencesSection.Reminders, dirtyState(PreferencesSection.Reminders))

        rule.onNodeWithTag("preferences_avisos_save").assertIsEnabled().performClick()

        assertEquals(listOf<PreferencesEvent>(PreferencesEvent.Save), events)
    }

    @Test
    fun reminders_cleanKeepsGuardarDisabled() = assertCleanSaveDisabled(PreferencesSection.Reminders)

    @Test
    fun reminders_offlineDisablesGuardar_withTheHint() {
        val dirty = base.copy(weightReminder = false)
        show(PreferencesSection.Reminders, loaded(PreferencesSection.Reminders, dirty, isOnline = false))

        rule.onNodeWithTag("preferences_avisos_save").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("offline_hint").assertTextEquals("Sin conexión: no se puede guardar.")
        assertTrue(events.isEmpty())
    }

    // -- tablet ------------------------------

    @Test
    @Config(qualifiers = "w840dp-h1000dp-mdpi")
    fun tablet_capsTheEditorWidth_inASingleColumn() {
        show(PreferencesSection.Cooking)
        val bar = rule.onNodeWithTag("preferences_content").getUnclippedBoundsInRoot()
        val save = rule.onNodeWithTag("preferences_cooking_save").getUnclippedBoundsInRoot()
        assertTrue("bar ${bar.width}", bar.width <= WindowWidthClassDefaults.FormContentMaxWidth)
        assertTrue("save ${save.width}", save.width <= WindowWidthClassDefaults.FormContentMaxWidth)
    }
}
