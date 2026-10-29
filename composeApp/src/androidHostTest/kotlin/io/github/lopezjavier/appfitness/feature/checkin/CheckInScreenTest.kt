package io.github.lopezjavier.appfitness.feature.checkin

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import io.github.lopezjavier.appfitness.domain.model.PeriodTraining
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.model.WeekRecord
import io.github.lopezjavier.appfitness.domain.model.WeeklySummary
import io.github.lopezjavier.appfitness.domain.model.WeeklyWeightTrend
import io.github.lopezjavier.appfitness.feature.profile.CountingHaptics
import io.github.lopezjavier.appfitness.feature.profile.RecordingMessages
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

private val SUNDAY = LocalDate(2026, 11, 15)
private val FRIDAY = LocalDate(2026, 11, 13)
private val MONDAY = LocalDate(2026, 11, 9)

/** US-59: the weekly check-in screen renders the closed week, the energy/notes form and the editor behaviour. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class CheckInScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<CheckInEvent>()
    private val messages = RecordingMessages()
    private val haptics = CountingHaptics()
    private var closes = 0
    private var state by mutableStateOf<CheckInUiState>(CheckInUiState.Loading)

    private fun summary(
        current: TrainingSummary = TrainingSummary(4, 40, 12_400.0, 0),
        previous: TrainingSummary = TrainingSummary(3, 30, 11_600.0, 0),
        records: List<WeekRecord> = listOf(WeekRecord("bench", PersonalRecordType.HeaviestLift, 100.0, FRIDAY)),
        trend: WeeklyWeightTrend = WeeklyWeightTrend.Delta(-0.4),
    ) = CheckInSummaryState.Loaded(WeeklySummary(PeriodTraining(current, previous), records, trend))

    private fun ready(
        summary: CheckInSummaryState = summary(),
        energy: Int? = null,
        notes: String = "",
        day: LocalDate = SUNDAY,
        answer: CheckIn? = null,
        isOffline: Boolean = false,
    ) = CheckInUiState.Ready(
        window = CheckInDay(day, MONDAY, answer),
        energy = energy,
        notes = notes,
        summary = summary,
        isOffline = isOffline,
        exerciseNames = mapOf("bench" to "Press banca"),
    )

    private fun show(initial: CheckInUiState = ready(), effects: Channel<CheckInEffect> = Channel(Channel.BUFFERED)) {
        events.clear()
        state = initial
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages, LocalAppHaptics provides haptics) {
                    CheckInScreen(state, { events += it }, { closes++ }, effects = effects.receiveAsFlow())
                }
            }
        }
        rule.waitForIdle()
    }

    // -- Weekly summary ------------------------------

    @Test
    fun weeklySummary_showsVolumeWorkoutsAndSetsWithTheirDifference() {
        show()

        rule.onNodeWithTag("checkin_summary_volume").assertTextContains("12.400 kg", substring = true)
        rule.onNodeWithTag("checkin_summary_volume").assertTextContains("+800 kg vs semana anterior", substring = true)
        rule.onNodeWithTag("checkin_summary_workouts").assertTextContains("4 entrenos", substring = true)
        rule.onNodeWithTag(
            "checkin_summary_workouts",
        ).assertTextContains("+1 entreno vs semana anterior", substring = true)
        rule.onNodeWithTag("checkin_summary_sets").assertTextContains("40 series", substring = true)
        rule.onNodeWithTag("checkin_summary_sets").assertTextContains("+10 series vs semana anterior", substring = true)
    }

    @Test
    fun weeklySummary_listsTheRecordsWithTheExerciseName() {
        show()

        rule.onNodeWithText("Récords de la semana").assertIsDisplayed()
        rule.onNodeWithTag("checkin_summary_record_0").assertTextContains("Press banca", substring = true)
        rule.onNodeWithTag("checkin_summary_record_0").assertTextContains("100 kg", substring = true)
    }

    @Test
    fun weeklySummary_showsTheNameOfCatalogueAndCustomExercises() {
        val records = listOf(
            WeekRecord("bench", PersonalRecordType.HeaviestLift, 100.0, FRIDAY),
            WeekRecord("grip", PersonalRecordType.BestSet, 30.0, FRIDAY),
        )
        show(
            (ready(summary(records = records)) as CheckInUiState.Ready).copy(
                exerciseNames = mapOf("bench" to "Press banca", "grip" to "Hand grip"),
            ),
        )

        rule.onNodeWithTag("checkin_summary_record_0").assertTextContains("Press banca", substring = true)
        rule.onNodeWithTag("checkin_summary_record_1").assertTextContains("Hand grip", substring = true)
    }

    @Test
    fun weeklySummary_weightTrendShowsTheSignedDelta() {
        show()

        rule.onNodeWithTag("checkin_summary_weight").assertTextContains("−0,4 kg", substring = true)
        rule.onNodeWithTag("checkin_summary_weight").assertTextContains("vs semana anterior", substring = true)
    }

    @Test
    fun weeklySummary_withoutWeighInsSaysThereIsNotEnoughData() {
        show(ready(summary(trend = WeeklyWeightTrend.NotEnoughData)))

        rule.onNodeWithTag("checkin_summary_weight").assertTextContains("Sin datos suficientes", substring = true)
    }

    @Test
    fun emptyWeek_showsZerosAndNoRecords_notAnEmptyState() {
        val empty = TrainingSummary(0, 0, 0.0, 0)
        show(ready(summary(current = empty, previous = empty, records = emptyList())))

        rule.onNodeWithTag("checkin_summary_volume").assertTextContains("0 kg", substring = true)
        rule.onNodeWithTag("checkin_summary_workouts").assertTextContains("0 entrenos", substring = true)
        rule.onNodeWithTag("checkin_summary_sets").assertTextContains("0 series", substring = true)
        rule.onNodeWithTag(
            "checkin_summary_volume",
        ).assertTextContains("Igual que la semana anterior", substring = true)
        rule.onNodeWithTag("checkin_summary_records_empty").assertTextContains("Sin récords esta semana")
    }

    @Test
    fun aNonSundayCheckInDay_explainsTheMondayToSundayWeek() {
        show(ready(day = FRIDAY))

        rule.onNodeWithText("Semana de lunes a domingo").assertIsDisplayed()
    }

    @Test
    fun aSundayCheckInDay_needsNoWeekCaption() {
        show(ready(day = SUNDAY))

        rule.onAllNodesWithTag("checkin_summary_header").assertCountEquals(1)
        rule.onAllNodesWithText("Semana de lunes a domingo").assertCountEquals(0)
    }

    @Test
    fun coachSlot_existsButHasNoVisibleContent() {
        show()

        rule.onNodeWithTag("checkin_coach_slot").assertExists()
    }

    @Test
    fun energyHelp_opensASheetWithOneBulletPerValue() {
        show()

        rule.onNodeWithTag("checkin_energy_help").performClick()
        rule.waitForIdle()

        val bullet = { n: Int -> rule.onNodeWithTag("checkin_energy_help_sheet_bullet_$n", useUnmergedTree = true) }
        for (n in 1..5) bullet(n).assertExists()
        rule.onNode(
            hasText(
                "1: agotado",
                substring = true,
            ) and hasAnyAncestor(hasTestTag("checkin_energy_help_sheet_bullet_1")),
            useUnmergedTree = true,
        ).assertExists()
        rule.onNode(
            hasText("5: excelente", substring = true) and
                hasAnyAncestor(hasTestTag("checkin_energy_help_sheet_bullet_5")),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun energyHelp_doesNotSelectAnyEnergyValue() {
        show()

        rule.onNodeWithTag("checkin_energy_help").performClick()

        assertEquals(emptyList<CheckInEvent>(), events)
    }

    // -- Summary states, independent of the form ------------------------------

    @Test
    fun summaryLoading_showsSkeletonsAndTheFormStaysUsable() {
        show(ready(summary = CheckInSummaryState.Loading))

        rule.onNodeWithTag("checkin_summary_loading").assertIsDisplayed()
        rule.onNodeWithTag("checkin_energy_3").performClick()

        assertEquals(listOf<CheckInEvent>(CheckInEvent.SelectEnergy(3)), events)
    }

    @Test
    fun summaryError_offersReintentar_withoutBlockingTheForm() {
        show(ready(summary = CheckInSummaryState.Failed))

        rule.onNodeWithText("No se pudo cargar el resumen. Inténtalo de nuevo.").assertIsDisplayed()
        rule.onNodeWithTag("checkin_summary_retry").performClick()
        rule.onNodeWithTag("checkin_energy_2").performClick()

        assertEquals(listOf(CheckInEvent.RetrySummary, CheckInEvent.SelectEnergy(2)), events)
    }

    @Test
    fun loadFailed_offersReintentar() {
        show(CheckInUiState.LoadFailed)

        rule.onNodeWithTag("checkin_retry").performClick()

        assertEquals(listOf<CheckInEvent>(CheckInEvent.Retry), events)
    }

    @Test
    fun loading_showsTheSkeleton() {
        show(CheckInUiState.Loading)

        rule.onNodeWithTag("checkin_loading").assertIsDisplayed()
    }

    // -- Answering ------------------------------

    @Test
    fun energyHasFiveOptions_andPickingOneSendsIt() {
        show()

        for (level in 1..5) rule.onNodeWithTag("checkin_energy_$level").assertIsDisplayed()
        rule.onNodeWithTag("checkin_energy_4").performClick()

        assertEquals(listOf<CheckInEvent>(CheckInEvent.SelectEnergy(4)), events)
    }

    @Test
    fun notes_typedTextIsSent() {
        show()

        rule.onNodeWithTag("checkin_notes").performScrollTo().performTextInput("Dormí poco")

        assertEquals(listOf<CheckInEvent>(CheckInEvent.SetNotes("Dormí poco")), events)
    }

    @Test
    fun guardar_isDisabledUntilAnEnergyIsChosen() {
        show(ready(energy = null))

        rule.onNodeWithTag("checkin_save").assertIsNotEnabled()
    }

    @Test
    fun guardar_withAnEnergy_sendsSave() {
        show(ready(energy = 4))

        rule.onNodeWithTag("checkin_save").assertIsEnabled().performClick()

        assertEquals(listOf<CheckInEvent>(CheckInEvent.Save), events)
    }

    @Test
    fun saved_closesOnceWithOneConfirmHaptic() {
        val effects = Channel<CheckInEffect>(Channel.BUFFERED)
        show(ready(energy = 4), effects)

        effects.trySend(CheckInEffect.Saved)
        rule.waitForIdle()

        assertEquals(1, closes)
        assertEquals(1, haptics.confirms)
        assertEquals(0, haptics.rejects)
        assertTrue(messages.shown.isEmpty())
    }

    @Test
    fun saveFailure_keepsTheDraft_rejectsAndShowsTheMessage() {
        val effects = Channel<CheckInEffect>(Channel.BUFFERED)
        show(ready(energy = 4, notes = "x"), effects)

        effects.trySend(CheckInEffect.SaveFailed(offline = false))
        rule.waitForIdle()

        assertEquals(0, closes)
        assertEquals(1, haptics.rejects)
        assertEquals(listOf("No se pudo guardar el check-in. Inténtalo de nuevo."), messages.shown)
        rule.onNodeWithTag("checkin_save").assertIsEnabled()
    }

    @Test
    fun notesOverTheLimit_showTheMessageAndBlockGuardar() {
        show(ready(energy = 4, notes = "a".repeat(1001)))

        rule.onNodeWithText("Máximo 1000 caracteres").assertExists()
        rule.onNodeWithTag("checkin_notes_counter").assertTextContains("1001/1000")
        rule.onNodeWithTag("checkin_save").assertIsNotEnabled()
    }

    // -- Discard, offline and reopening ------------------------------

    @Test
    fun backWithChanges_asksToDiscard_andKeepEditingStays() {
        show(ready(energy = 4))

        rule.onNodeWithTag("checkin_back").performClick()
        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
        rule.onNodeWithTag("checkin_keep_editing").performClick()
        rule.waitForIdle()

        assertEquals(0, closes)
        rule.onNodeWithText("¿Descartar los cambios?").assertDoesNotExist()
    }

    @Test
    fun backWithChanges_discardLeaves() {
        show(ready(energy = 4))

        rule.onNodeWithTag("checkin_back").performClick()
        rule.onNodeWithTag("checkin_discard_confirm").performClick()
        rule.waitForIdle()

        assertEquals(1, closes)
    }

    @Test
    fun backWithoutChanges_leavesSilently() {
        show(ready(energy = null))

        rule.onNodeWithTag("checkin_back").performClick()
        rule.waitForIdle()

        assertEquals(1, closes)
        rule.onNodeWithText("¿Descartar los cambios?").assertDoesNotExist()
    }

    @Test
    fun offline_showsTheHint_andDisablesGuardar() {
        show(ready(energy = 4, isOffline = true))

        rule.onNodeWithText("Sin conexión: no se puede guardar").assertIsDisplayed()
        rule.onNodeWithTag("checkin_save").assertIsNotEnabled()
    }

    @Test
    fun reopenedAnswer_isPrefilled_andCleanUntilEdited() {
        show(ready(energy = 5, notes = "Muy bien", answer = CheckIn(MONDAY, 5, "Muy bien")))

        rule.onNodeWithTag("checkin_energy_5").assertIsSelected()
        rule.onNodeWithTag("checkin_notes").assertTextContains("Muy bien")
        rule.onNodeWithTag("checkin_save").assertIsNotEnabled()
    }

    @Test
    fun discardDialog_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { CheckInScreen(ready(energy = 4), {}, {}) } }
        rule.onNodeWithTag("checkin_back").performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
    }

    private fun assertTrue(condition: Boolean) = kotlin.test.assertTrue(condition)
}
