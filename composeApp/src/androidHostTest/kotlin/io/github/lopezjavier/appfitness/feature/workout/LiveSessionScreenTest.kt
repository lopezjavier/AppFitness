package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppTheme
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

    private fun show(state: LiveSessionUiState, elapsed: Long = 125) {
        rule.setContent {
            AppTheme { LiveSessionScreen(state, elapsed, { events += it }, { backs++ }, onOpenExercise = {}) }
        }
        rule.waitForIdle()
    }

    @Test
    fun header_showsLiveTitle_timer_andNoKeepAwakeIndicatorNorFixedFinishButton() {
        show(active())

        rule.onNodeWithTag("live_screen").assertExists()
        rule.onNodeWithText("EN VIVO").assertExists()
        rule.onNodeWithTag("live_timer").assertTextContains("02:05")
        rule.onNodeWithTag("live_keep_awake").assertDoesNotExist()
        rule.onNodeWithText("Pantalla activa").assertDoesNotExist()
        rule.onNodeWithTag("live_finish_button").assertDoesNotExist()
    }

    @Test
    fun timer_describesTheElapsedTimeForScreenReaders() {
        show(active(), elapsed = 125)

        rule.onNodeWithTag("live_timer")
            .assert(hasContentDescription("Tiempo de entreno, 2 minutos 0 segundos"))
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
        rule.onNodeWithTag("live_finish_sheet_confirm").performClick()
        rule.onNodeWithTag("live_finish_sheet_cancel").performClick()

        assertEquals(listOf(LiveSessionEvent.ConfirmFinish, LiveSessionEvent.DismissFinish), events)
    }

    @Test
    fun finishSheet_withoutPendingSets_hasNoWarning() {
        show(active(finishSheet = FinishSheet(0)))

        rule.onNodeWithTag("live_finish_sheet_warning").assertDoesNotExist()
        rule.onNodeWithTag("live_finish_sheet_confirm").assertExists()
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
                    ExerciseSummary(0, "e1", 2_120.0, true),
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
        rule.onNodeWithTag("summary_pr_0").assert(hasContentDescription("Récord personal"))
        rule.onNodeWithTag("summary_pr_1").assertDoesNotExist()
        rule.onNodeWithTag("summary_done").performClick()
        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.Done), events)
    }

    @Test
    fun summary_prBadgeShowsPrText() {
        val summary = LiveSummary(
            SessionSummary(60, 1, listOf(ExerciseSummary(0, "e1", 100.0, true))),
            mapOf(0 to "Press banca"),
        )
        show(LiveSessionUiState.Finished(summary))

        rule.onNode(hasText("PR"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun semantics_timerIsNotAHeading() {
        show(active())

        val config = rule.onNodeWithTag("live_timer").fetchSemanticsNode().config
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
}
