package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.RecordingAppHaptics
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseSummary
import io.github.lopezjavier.appfitness.domain.usecase.SessionSummary
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun row(index: Int, weight: String = "", reps: String = "", done: Boolean = false, pending: Boolean = false) =
    LiveSetRow(index, weight, reps, done, pendingSave = pending)

private fun active(
    isOnline: Boolean = true,
    finishSheet: FinishSheet? = null,
    discardConfirm: Boolean = false,
    exercises: List<LiveExercise> = listOf(
        LiveExercise(0, "e1", "Press banca", null, listOf(row(1, "60", "12", true), row(2), row(3))),
        LiveExercise(1, "e2", "Remo", SupersetLabel('A', 1), listOf(row(1), row(2))),
    ),
) = LiveSessionUiState.Active(
    name = "Torso",
    startedAt = SESSION_START,
    exercises = exercises,
    isOnline = isOnline,
    finishSheet = finishSheet,
    discardConfirm = discardConfirm,
)

/** US-11 live screen: header, exercise cards, sets, finish sheet, discard dialog and summary. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class LiveSessionScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<LiveSessionEvent>()
    private var backs = 0

    private val haptics = RecordingAppHaptics()

    private fun show(state: LiveSessionUiState, elapsed: Long = 125) {
        rule.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                AppTheme { LiveSessionScreen(state, elapsed, { events += it }, { backs++ }, onOpenExercise = {}) }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun completingASetWithTheCheckbox_givesExactlyOneConfirmAndNoReject() {
        show(active())

        rule.onNodeWithTag("live_set_0_2_check").performClick()

        haptics.assertSingle(HapticEvent.Confirm)
    }

    @Test
    fun uncheckingASet_givesNoHaptic() {
        show(active())

        rule.onNodeWithTag("live_set_0_1_check").performClick()

        assertTrue(haptics.calls.isEmpty())
    }

    @Test
    fun header_showsLiveTitle_timer_andNoKeepAwakeIndicatorNorFixedFinishButton() {
        show(active())

        rule.onNodeWithTag("live_screen").assertExists()
        rule.onNodeWithTag("live_subtitle").assertTextContains("EN VIVO · 02:05")
        rule.onNodeWithTag("live_keep_awake").assertDoesNotExist()
        rule.onNodeWithText("Pantalla activa").assertDoesNotExist()
        rule.onNodeWithTag("live_finish_button").assertDoesNotExist()
    }

    @Test
    fun timer_describesTheElapsedTimeForScreenReaders() {
        show(active(), elapsed = 125)

        rule.onNodeWithTag("live_subtitle")
            .assert(hasContentDescription("Tiempo de entreno, 2 minutos 0 segundos"))
    }

    @Test
    fun title_isTheRoutineName() {
        show(active())
        rule.onNodeWithTag("live_title").assertTextEquals("Torso")
    }

    @Test
    fun blankRoutineName_showsTheFallbackTitle() {
        show(active().copy(name = ""))
        rule.onNodeWithTag("live_title").assertTextEquals("Entreno en vivo")
    }

    @Test
    fun pauseAndMenu_areTopBarActions() {
        show(active())
        rule.onNodeWithTag("live_pause_toggle").assertExists()
        rule.onNodeWithTag("live_menu_button").assertExists()
    }

    @Test
    fun offline_hidesThePauseAction_butKeepsTheMenu() {
        show(active(isOnline = false))
        rule.onNodeWithTag("live_pause_toggle").assertDoesNotExist()
        rule.onNodeWithTag("live_menu_button").assertExists()
    }

    @Test
    fun addButtons_readAnadirSerieAndAnadirEjercicio() {
        show(active())
        rule.onNodeWithTag("live_exercise_0_add_set").assertTextEquals("Añadir serie")
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("live_add_exercise"))
        rule.onNodeWithTag("live_add_exercise").assertTextEquals("Añadir ejercicio")
    }

    @Test
    fun openMenu_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { LiveSessionScreen(active(), 0, { events += it }, {}, onOpenExercise = {}) } }
        rule.onNodeWithTag("live_menu_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("live_menu_finish").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("live_menu_finish").assertExists()
    }

    @Test
    fun back_callsOnBackWithoutFinishing() {
        show(active())

        rule.onNodeWithTag("live_back").performClick()

        assertEquals(1, backs)
        assertTrue(events.isEmpty())
    }

    @Test
    fun exercises_showTableHeaderAndTheirRows() {
        show(active())

        rule.onNodeWithTag("live_exercise_0").assertTextContains("Press banca")
        rule.onNodeWithTag("live_exercise_1").assertTextContains("Remo")
        rule.onNodeWithTag("live_exercise_1").assertTextContains("Superserie A1")
        listOf(1, 2, 3).forEach { rule.onNodeWithTag("live_set_0_$it").assertExists() }
        listOf(1, 2).forEach { rule.onNodeWithTag("live_set_1_$it").assertExists() }
        rule.onNodeWithTag("live_set_0_1_weight").assertTextContains("60")
        rule.onNodeWithTag("live_set_0_1_check").assertExists()
    }

    @Test
    fun checkingASet_sendsSetCheckedEvent() {
        show(active())

        rule.onNodeWithTag("live_set_0_2_check").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.SetChecked(0, 2, true)), events)
    }

    @Test
    fun uncheckingACompletedSet_sendsUncheckEvent() {
        show(active())

        rule.onNodeWithTag("live_set_0_1_check").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.SetChecked(0, 1, false)), events)
    }

    @Test
    fun typingWeightAndReps_sendsDraftEvents() {
        show(active())

        rule.onNodeWithTag("live_set_0_2_weight").performTextInput("8")
        rule.onNodeWithTag("live_set_0_2_reps").performTextInput("5")

        assertTrue(events.contains(LiveSessionEvent.WeightChanged(0, 2, "8")))
        assertTrue(events.contains(LiveSessionEvent.RepsChanged(0, 2, "5")))
    }

    @Test
    fun addSetAndAddExercise_sendTheirEvents() {
        show(active())

        rule.onNodeWithTag("live_exercise_1_add_set").performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("live_add_exercise"))
        rule.onNodeWithTag("live_add_exercise").performClick()

        assertEquals(listOf(LiveSessionEvent.AddSet(1), LiveSessionEvent.AddExercise), events)
    }

    @Test
    fun queuedRow_showsPendingLabelWithText() {
        val exercises =
            listOf(LiveExercise(0, "e1", "Press banca", null, listOf(row(1, "60", "12", true, pending = true))))
        show(active(isOnline = false, exercises = exercises))

        rule.onNodeWithTag("live_set_0_1_pending").assertTextContains("Pendiente de guardar")
    }

    @Test
    fun syncedRow_hasNoPendingLabel() {
        show(active())

        rule.onNodeWithTag("live_set_0_1_pending").assertDoesNotExist()
    }

    @Test
    fun offline_blocksFinishAndDiscard_andExplainsWhy() {
        show(active(isOnline = false))

        rule.onNodeWithTag("live_menu_button").performClick()
        rule.onNodeWithTag("live_menu_finish").assertIsNotEnabled()
        rule.onNodeWithTag("live_menu_discard").assertIsNotEnabled()
        rule.onNodeWithTag("live_menu_offline_reason").assertExists()
    }

    @Test
    fun online_hidesTheOfflineReason_andEnablesFinish() {
        show(active())

        rule.onNodeWithTag("live_menu_button").performClick()
        rule.onNodeWithTag("live_menu_finish").assertIsEnabled()
        rule.onNodeWithTag("live_menu_offline_reason").assertDoesNotExist()
    }

    @Test
    fun menu_offersFinishAboveDiscard() {
        show(active())

        rule.onNodeWithTag("live_menu_button").performClick()

        val finishTop = rule.onNodeWithTag("live_menu_finish").getUnclippedBoundsInRoot().top
        val discardTop = rule.onNodeWithTag("live_menu_discard").getUnclippedBoundsInRoot().top
        assertTrue(finishTop < discardTop)
    }

    @Test
    fun finishMenuRow_requestsFinish() {
        show(active())

        rule.onNodeWithTag("live_menu_button").performClick()
        rule.onNodeWithTag("live_menu_finish").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.RequestFinish), events)
    }

    @Test
    fun finishSheet_warnsAboutPendingSets_andConfirms() {
        show(active(finishSheet = FinishSheet(3)))

        rule.onNodeWithTag("live_finish_sheet_warning")
            .assertTextContains("Hay 3 series sin completar; no se guardarán")
        rule.onNodeWithTag("live_finish_sheet_commit").performClick()
        rule.onNodeWithTag("live_finish_sheet_close").performClick()
        rule.waitForIdle()

        assertEquals(listOf(LiveSessionEvent.ConfirmFinish, LiveSessionEvent.DismissFinish), events)
    }

    @Test
    fun finishSheet_withoutPendingSets_hasNoWarning() {
        show(active(finishSheet = FinishSheet(0)))

        rule.onNodeWithTag("live_finish_sheet_warning").assertDoesNotExist()
        rule.onNodeWithTag("live_finish_sheet_commit").assertExists()
    }

    @Test
    fun finishSheet_singularWarning() {
        show(active(finishSheet = FinishSheet(1)))

        rule.onNodeWithTag("live_finish_sheet_warning")
            .assertTextContains("Hay 1 serie sin completar; no se guardará")
    }

    @Test
    fun discardMenu_requestsDiscard_andDialogConfirms() {
        show(active())
        rule.onNodeWithTag("live_menu_button").performClick()
        rule.onNodeWithTag("live_menu_discard").performClick()
        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.RequestDiscard), events)
    }

    @Test
    fun discardDialog_confirmAndCancel() {
        show(active(discardConfirm = true))

        rule.onNodeWithTag("live_discard_confirm").performClick()
        rule.onNodeWithTag("live_discard_cancel").performClick()

        assertEquals(listOf(LiveSessionEvent.ConfirmDiscard, LiveSessionEvent.DismissDiscard), events)
    }

    @Test
    fun loading_showsSkeleton() {
        show(LiveSessionUiState.Loading)

        rule.onNodeWithTag("live_loading").assertExists()
    }

    @Test
    fun error_offersRetry() {
        show(LiveSessionUiState.Error)

        rule.onNodeWithTag("live_error").assertExists()
        rule.onNodeWithTag("live_error_action").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.Retry), events)
    }

    @Test
    fun noSession_neverShowsABlankScreen() {
        show(LiveSessionUiState.NoSession)

        rule.onNodeWithTag("live_loading").assertExists()
    }

    @Test
    fun summary_showsDurationSetsVolumeAndPersonalRecord() {
        val summary = LiveSummary(
            SessionSummary(
                durationSeconds = 3_725,
                completedSets = 3,
                exercises = listOf(
                    ExerciseSummary(
                        0,
                        "e1",
                        2_120.0,
                        true,
                        setOf(PersonalRecordType.Best1Rm, PersonalRecordType.HeaviestLift),
                    ),
                    ExerciseSummary(1, "e2", 400.0, false),
                ),
            ),
            mapOf(0 to "Press banca", 1 to null),
        )
        show(LiveSessionUiState.Finished(summary))

        rule.onNodeWithTag("summary_screen").assertExists()
        rule.onNodeWithTag("summary_duration").assertTextContains("1:02:05")
        rule.onNodeWithTag("summary_sets").assertTextContains("3")
        rule.onNodeWithTag("summary_volume_0").assertTextContains("2.120 kg")
        rule.onNodeWithTag("summary_volume_0").assertTextContains("Press banca")
        rule.onNodeWithTag(
            "session_summary_record_0_heaviest_lift",
        ).assert(hasContentDescription("Récord: Récord de peso"))
        rule.onNodeWithTag("session_summary_record_0_best_1rm").assert(hasContentDescription("Récord: 1RM estimado"))
        rule.onNodeWithTag("session_summary_records_1").assertDoesNotExist()
        rule.onNodeWithTag("session_summary_record_0_best_set").assertDoesNotExist()
        rule.onNodeWithTag("summary_done").performClick()
        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.Done), events)
    }

    @Test
    fun summary_durationAndDistanceExercise_showsItsRecordLabels() {
        val records = setOf(
            PersonalRecordType.BestDuration,
            PersonalRecordType.BestDistance,
            PersonalRecordType.MostSteps,
            PersonalRecordType.MostFloors,
        )
        val summary = LiveSummary(
            SessionSummary(60, 1, listOf(ExerciseSummary(0, "e1", 0.0, false, records))),
            mapOf(0 to "Cinta"),
        )
        show(LiveSessionUiState.Finished(summary))

        listOf("Mejor tiempo", "Mayor distancia", "Más escalones", "Más pisos").forEach {
            rule.onNode(hasText(it), useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun summary_withoutRecords_showsNoPills() {
        val summary = LiveSummary(
            SessionSummary(60, 1, listOf(ExerciseSummary(0, "e1", 100.0, false))),
            mapOf(0 to "Press banca"),
        )
        show(LiveSessionUiState.Finished(summary))

        rule.onNodeWithTag("session_summary_records_0").assertDoesNotExist()
    }

    @Test
    fun semantics_timerIsNotAHeading() {
        show(active())

        val config = rule.onNodeWithTag("live_subtitle").fetchSemanticsNode().config
        assertTrue(SemanticsProperties.Heading !in config)
    }

    @Test
    fun updatingTheState_rerendersRows() {
        var state by mutableStateOf<LiveSessionUiState>(active())
        rule.setContent { AppTheme { LiveSessionScreen(state, 0, { events += it }, { backs++ }, onOpenExercise = {}) } }

        state =
            active(
                exercises = listOf(LiveExercise(0, "e1", "Press banca", null, listOf(row(1), row(2), row(3), row(4)))),
            )
        rule.waitForIdle()

        rule.onNodeWithTag("live_set_0_4").assertExists()
    }

    private fun plannedState() = active(
        exercises = listOf(
            LiveExercise(
                0,
                "e1",
                "Press banca",
                null,
                listOf(
                    LiveSetRow(1, "", "", false, "40", "10", type = SetType.WarmUp, previous = "30 kg × 12"),
                    LiveSetRow(2, "", "", false, "80", "8"),
                    LiveSetRow(3, "", "", false, "80", "8"),
                    LiveSetRow(4, "", "", false, "80", "8"),
                ),
            ),
        ),
    )

    @Test
    fun plannedSets_showTheTypeLetterInTheBadge_andTheOrderAsAnOrdinalOutsideIt() {
        show(plannedState())

        fun badge(set: Int) = rule.onNode(
            hasTestTag("set_type_badge") and hasAnyAncestor(hasTestTag("live_set_0_$set")),
            useUnmergedTree = true,
        )
        badge(1).assertTextEquals("C")
        badge(2).assertTextEquals("N")
        badge(4).assertTextEquals("N")
        val ordinals = rule.onAllNodesWithTag("set_ordinal")
        ordinals[1].assertTextEquals("2")
        ordinals[3].assertTextEquals("4")
    }

    @Test
    fun plannedWeightAndReps_areDimHintsNotValues_andThePreviousColumnIsKept() {
        show(plannedState())

        rule.onNodeWithTag("live_set_0_2_weight").assertTextEquals("")
        rule.onNodeWithTag("live_set_0_2_reps").assertTextEquals("")
        // The planned targets travel as the dim placeholders of the row, never as typed values.
        val values = (plannedState().exercises.single().sets[1]).toSetRowValues()
        assertEquals("80" to "8", values.weightPlaceholder to values.repsPlaceholder)
        assertEquals("" to "", values.weight to values.reps)
        rule.onNodeWithTag("live_set_0_1_previous").assertTextContains("30 kg × 12")
    }
}
