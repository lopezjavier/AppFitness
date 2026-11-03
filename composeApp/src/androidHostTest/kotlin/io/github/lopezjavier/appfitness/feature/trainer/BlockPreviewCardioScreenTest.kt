package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.model.BlockCardio
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeFavoriteExercisesRepository
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.RecordingMessages
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-72 cardio scenarios: the cardio row, total time, "por tu cuenta" and the any-to-any swap sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class BlockPreviewCardioScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val blocks = FakeCoachBlockRepository()
    private val repository = FakeExerciseRepository(
        listOf(exerciseRow("ex-1", "bench"), exerciseRow("bike", "bike"), exerciseRow("walk", "walk")),
    ).apply {
        alternativesById = mapOf("bike" to listOf(ExerciseAlternative(rows[2], 0.4)))
    }

    private fun cardio(minutes: Int) = BlockExercise(
        exerciseId = "bike",
        exerciseKey = "key-bike",
        role = "cardio",
        muscleGroup = "cardio",
        sets = 1,
        repMin = 0,
        repMax = 0,
        restSeconds = 0,
        reason = "Cardio suave.",
        durationMin = minutes,
        pace = "suave",
    )

    private fun block(ownMin: Int): CoachBlock {
        val base = coachBlock(sessionsPerWeek = 2)
        val perRoutine = 50
        return base.copy(
            training = base.training.copy(
                cardio = BlockCardio(2 * perRoutine + ownMin, 2 * perRoutine, ownMin, listOf(50, 50), "suave"),
                routineTotalMin = listOf(110, 110),
            ),
            sessions = base.sessions.map { it.copy(exercises = listOf(blockExerciseOf("ex-1"), cardio(perRoutine))) },
        )
    }

    private fun blockExerciseOf(id: String) = blockExercise(id)

    private fun show(block: CoachBlock) {
        blocks.current = Result.success(block)
        val model = BlockPreviewViewModel(
            "plan-1",
            blocks,
            repository,
            FakeExerciseLibrary(
                mapOf(
                    "bench" to exerciseInfo("bench", "Press banca"),
                    "bike" to exerciseInfo("bike", "Bicicleta"),
                    "walk" to exerciseInfo("walk", "Caminar a paso ligero"),
                ),
            ),
            MutableClock(),
            FakeTrainingPreferencesRepository(),
            FakeFavoriteExercisesRepository(),
            SavedStateHandle(),
        )
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalAppMessages provides RecordingMessages(),
                    LocalExerciseMediaUris provides FakeExerciseMediaUris,
                ) {
                    BlockPreviewRoot("plan-1", {}, {}, viewModel = model, connectivity = FakeConnectivityObserver())
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun theCardioRow_readsMinutesAndPace_andTheTotalTimeFollows() {
        show(block(ownMin = 0))

        rule.onNodeWithTag("block_preview_cardio_row_1_0", useUnmergedTree = true).assertTextEquals("50 min · suave")
        rule.onNodeWithTag("block_preview_cardio_total_1_0").assertTextEquals("Tiempo total: 1 h 50 min")
        rule.onAllNodesWithText("Calentamiento").assertCountEquals(0)
    }

    @Test
    fun theOwnMinutesLine_showsOnlyWhenTheRoutinesDoNotHoldTheTarget() {
        show(block(ownMin = 120))

        rule.onNodeWithTag("block_preview_cardio_own_1")
            .assertTextEquals("Te faltan 120 min de cardio a la semana: hazlos por tu cuenta.")
    }

    @Test
    fun noOwnMinutes_showsNoLine() {
        show(block(ownMin = 0))

        rule.onNodeWithTag("block_preview_cardio_own_1").assertDoesNotExist()
    }

    @Test
    fun theCardioSwap_listsCardioWithoutPercent_andShowsTheNote() {
        show(block(ownMin = 0))

        tap("block_preview_replace_1_0_1")

        rule.onNodeWithTag("block_preview_replace_cardio_note")
            .assertTextEquals("Cuentan los mismos minutos con cualquier cardio, a ritmo suave.")
        rule.onNodeWithTag("block_preview_replace_option_walk").assertExists()
        rule.onNodeWithTag("block_preview_replace_percent_walk", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun pickingWalking_keepsTheMinutes() {
        show(block(ownMin = 0))
        tap("block_preview_replace_1_0_1")

        tap("block_preview_replace_option_walk")

        rule.onNodeWithTag("block_preview_exercise_1_0_1").assertTextContains("Caminar a paso ligero", substring = true)
        rule.onNodeWithTag("block_preview_changed_1_0_1", useUnmergedTree = true)
            .assertTextContains("50 min · suave", substring = true)
    }
}
