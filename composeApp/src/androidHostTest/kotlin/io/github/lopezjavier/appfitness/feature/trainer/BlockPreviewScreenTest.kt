package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-72: the block preview screen, one test per Gherkin scenario (display side; the rules live in the server). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class BlockPreviewScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<BlockPreviewEvent>()
    private var opened = mutableListOf<String>()
    private var backs = 0

    private val names = mapOf("ex-1" to "Press con mancuernas", "ex-2" to "Flexiones")

    private fun preview(
        block: CoachBlock = coachBlock(),
        week: Int = 1,
        reasonKey: String? = null,
        saving: Boolean = false,
    ) = BlockPreviewUiState.Preview(block, names, week, reasonKey, saving)

    private fun show(state: BlockPreviewUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme {
                BlockPreviewScreen(state, flowOf(online), { events += it }, { backs++ }, { opened += it })
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun preview_showsTheWeekTabsRoutinesAndTheReasonButton() {
        show(preview())

        rule.onNodeWithTag("block_preview_tab_1").assertExists()
        rule.onNodeWithTag("block_preview_tab_4").assertExists()
        rule.onNodeWithText("Semana 3").assertExists()
        for (index in 0..2) rule.onNodeWithTag("block_preview_routine_1_$index").assertExists()
        rule.onNodeWithTag("block_preview_reason_1_0_0").assertExists()
        tap("block_preview_reason_1_0_1")
        assertEquals(listOf<BlockPreviewEvent>(BlockPreviewEvent.OpenReason("1/0/1")), events)
    }

    @Test
    fun selectingAWeekTab_sendsTheWeek() {
        show(preview())

        tap("block_preview_tab_3")

        assertEquals(listOf<BlockPreviewEvent>(BlockPreviewEvent.SelectWeek(3)), events)
    }

    @Test
    fun theReasonSheet_showsTheCoachsReasonUnderTheExerciseName() {
        show(preview(reasonKey = "1/0/0"))

        rule.onNodeWithTag("block_preview_reason_sheet").assertExists()
        rule.onNodeWithText("Trabaja el pecho con poca fatiga.").assertExists()
    }

    @Test
    fun threeDays_showTheWeeklySetsOfEveryMuscle_andTheDaysPerWeek() {
        show(preview(coachBlock(sessionsPerWeek = 3)))

        rule.onNodeWithTag("block_preview_frequency").assertExists()
        rule.onNodeWithText("Series por grupo muscular").assertExists()
        rule.onNodeWithTag("block_preview_sets_chest").assertExists()
        rule.onNodeWithText("10 series · 2 días/semana").assertExists()
        rule.onNodeWithText("12 series · 2 días/semana").assertExists()
        rule.onNodeWithText("3 días por semana").assertExists()
    }

    @Test
    fun oneDay_showsASingleRoutinePerWeek() {
        show(preview(coachBlock(sessionsPerWeek = 1)))

        rule.onNodeWithTag("block_preview_routine_1_0").assertExists()
        rule.onAllNodesWithTag("block_preview_routine_1_1").assertCountEquals(0)
        rule.onNodeWithText("1 día por semana").assertExists()
    }

    @Test
    fun whenTheCoachChoseTheDays_theChosenFrequencyLineIsShown() {
        show(preview(coachBlock(training = blockTraining(days = 4, chosen = true))))

        rule.onNodeWithText("El coach ha elegido 4 días por semana (60 min por entreno).").assertExists()
    }

    @Test
    fun whenTheUserChoseTheDays_theChosenFrequencyLineIsAbsent() {
        show(preview(coachBlock(training = blockTraining(days = 4, chosen = false))))

        rule.onNodeWithText("4 días por semana").assertExists()
        rule.onAllNodesWithTag("block_preview_frequency").assertCountEquals(1)
        rule.onNodeWithText("El coach ha elegido", substring = true).assertDoesNotExist()
    }

    @Test
    fun withLimitedEquipment_onlyTheStoredExercisesAreRendered() {
        show(preview(coachBlock(sessionsPerWeek = 1)))

        rule.onAllNodesWithTag("block_preview_exercise_1_0_0").assertCountEquals(1)
        rule.onNodeWithText("Press con mancuernas").assertExists()
        rule.onNodeWithText("Flexiones").assertExists()
        rule.onNodeWithText("Press banca").assertDoesNotExist()
    }

    @Test
    fun eachExercise_showsSetsRepRangeAndRest_andStrengthSessionsKeepTheirOwnRanges() {
        val base = coachBlock(weeks = 1, sessionsPerWeek = 2)
        val strength = base.copy(
            sessions = base.sessions.mapIndexed { index, session: BlockSession ->
                val range = if (index == 0) 3 to 5 else 6 to 8
                session.copy(exercises = listOf(blockExercise("ex-1", range.first, range.second)))
            },
        )
        show(preview(strength))

        rule.onNodeWithText("3×3-5 reps · 1 min 30 s").assertExists()
        rule.onNodeWithText("3×6-8 reps · 1 min 30 s").assertExists()
    }

    @Test
    fun anExerciseOutsideTheCatalog_readsAsExercise_andIsNotOpenable() {
        val state = BlockPreviewUiState.Preview(coachBlock(sessionsPerWeek = 1), emptyMap(), 1)
        show(state)

        rule.onAllNodesWithTag("block_preview_exercise_1_0_0_thumbnail").assertCountEquals(0)
        rule.onNodeWithTag("block_preview_exercise_1_0_0").performClick()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun tappingAnExercise_opensItsDetail() {
        show(preview(coachBlock(sessionsPerWeek = 1)))

        tap("block_preview_exercise_1_0_0")

        assertEquals(listOf("ex-1"), opened)
    }

    @Test
    fun guardarBloque_sendsSave_andBackSendsNothing() {
        show(preview())

        tap("block_preview_save")

        assertEquals(listOf<BlockPreviewEvent>(BlockPreviewEvent.Save), events)
    }

    @Test
    fun back_leavesWithoutSavingAnything() {
        show(preview())

        rule.onNodeWithContentDescription("Volver").performClick()
        rule.waitForIdle()

        assertEquals(1, backs)
        assertTrue(events.isEmpty())
    }

    @Test
    fun guardarBloque_savesOnce_showsTheMessage_andLeaves() {
        val blocks = FakeCoachBlockRepository().apply { current = Result.success(coachBlock()) }
        val model = BlockPreviewViewModel(
            "plan-1",
            blocks,
            FakeExerciseRepository(listOf(exerciseRow("ex-1", "bench"))),
            FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca"))),
            MutableClock(),
        )
        val shown = mutableListOf<String>()
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides RecordingMessages(shown)) {
                    BlockPreviewRoot(
                        "plan-1",
                        { backs++ },
                        {},
                        viewModel = model,
                        connectivity = FakeConnectivityObserver(),
                    )
                }
            }
        }
        rule.waitForIdle()

        tap("block_preview_save")
        rule.waitForIdle()

        assertEquals(listOf("plan-1"), blocks.saves)
        assertEquals(1, backs)
        assertEquals(listOf("Bloque guardado en Entreno."), shown)
    }

    @Test
    fun requesting_showsTheProgress() {
        show(BlockPreviewUiState.Requesting)

        rule.onNodeWithTag("block_preview_progress").assertExists()
        rule.onNodeWithText("Preparando tu bloque…").assertExists()
    }

    @Test
    fun failure_showsTheBlockCopy_andRetry() {
        show(BlockPreviewUiState.Failed(PlanFailureKind.Other))

        rule.onNodeWithText("No se pudo crear el bloque. Inténtalo de nuevo.").assertExists()
        tap("block_preview_retry")
        assertEquals(listOf<BlockPreviewEvent>(BlockPreviewEvent.Retry), events)
    }

    @Test
    fun interrupted_saysTheBlockWasNotReceived() {
        show(BlockPreviewUiState.Failed(PlanFailureKind.Interrupted))

        rule.onNodeWithText("No se recibió el bloque. Inténtalo de nuevo.").assertExists()
    }

    @Test
    fun limit_showsTheLimitMessage() {
        show(BlockPreviewUiState.Failed(PlanFailureKind.Limit(CoachFailure.Scope.User)))

        rule.onNodeWithTag("block_preview_limit").assertExists()
    }

    @Test
    fun offline_disablesSaveWithTheHint() {
        show(preview(), online = false)

        rule.onNodeWithTag("block_preview_save").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede guardar el bloque").assertExists()
    }

    @Test
    fun online_saveIsEnabled() {
        show(preview())

        rule.onNodeWithTag("block_preview_save").assertIsEnabled()
    }
}

private class RecordingMessages(private val shown: MutableList<String>) : AppMessages(CoroutineScope(Job())) {
    override fun show(text: String) {
        shown += text
    }
}
