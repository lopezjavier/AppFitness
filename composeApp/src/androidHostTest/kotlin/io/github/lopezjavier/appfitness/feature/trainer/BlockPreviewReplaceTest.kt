package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.RecordingMessages
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-83: swapping an exercise in the block preview, one test per Gherkin scenario (real ViewModel, fakes). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class BlockPreviewReplaceTest {
    @get:Rule
    val rule = createComposeRule()

    private val bench = exerciseRow("ex-1", "bench")
    private val row = exerciseRow("ex-2", "row")
    private val fly = exerciseRow("ex-3", "fly", equipment = Equipment.BandLong)
    private val blocks = FakeCoachBlockRepository().apply { current = Result.success(coachBlock(sessionsPerWeek = 2)) }
    private val repository = FakeExerciseRepository(listOf(bench, row, fly)).apply {
        alternativesById = mapOf("ex-1" to listOf(ExerciseAlternative(fly, 0.87)))
    }
    private val messages = RecordingMessages()
    private val saved = SavedStateHandle()
    private var backs = 0

    private fun newModel(handle: SavedStateHandle = saved) = BlockPreviewViewModel(
        "plan-1",
        blocks,
        repository,
        FakeExerciseLibrary(
            mapOf(
                "bench" to exerciseInfo("bench", "Press banca"),
                "row" to exerciseInfo("row", "Remo"),
                "fly" to exerciseInfo("fly", "Aperturas con banda"),
            ),
        ),
        MutableClock(),
        handle,
    )

    private var current by mutableStateOf(newModel())

    private fun show() {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalAppMessages provides messages,
                    LocalExerciseMediaUris provides FakeExerciseMediaUris,
                ) {
                    BlockPreviewRoot("plan-1", {
                        backs++
                    }, {}, viewModel = current, connectivity = FakeConnectivityObserver())
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun swapFirstExercise() {
        tap("block_preview_replace_1_0_0")
        tap("block_preview_replace_option_fly")
    }

    @Test
    fun theSheet_offersOnlyTheAlternativesOfThatExercise_withPercentage() {
        show()

        tap("block_preview_replace_1_0_0")

        rule.onNodeWithTag("block_preview_replace_sheet").assertExists()
        rule.onNodeWithTag("block_preview_replace_option_fly").assertExists()
        rule.onNodeWithTag("block_preview_replace_percent_fly", useUnmergedTree = true)
            .assertContentDescriptionEquals("87 por ciento de similitud")
        rule.onNodeWithTag("block_preview_replace_option_row").assertDoesNotExist()
        rule.onNodeWithTag("block_preview_replace_reset").assertDoesNotExist()
        assertEquals(listOf("ex-1"), repository.alternativesCalls)
    }

    @Test
    fun picking_showsTheReplacementMarkedAsChanged_withTheSameSets_andWritesNothing() {
        show()

        swapFirstExercise()

        rule.onNodeWithTag("block_preview_replace_sheet").assertDoesNotExist()
        rule.onNodeWithTag("block_preview_exercise_1_0_0").assertTextContains("Aperturas con banda", substring = true)
        rule.onNodeWithTag("block_preview_changed_1_0_0", useUnmergedTree = true)
            .assertTextContains("Cambiado", substring = true)
            .assertTextContains("3×8-12 reps", substring = true)
        assertTrue(blocks.saves.isEmpty())
    }

    @Test
    fun leavingWithoutSaving_discardsTheChange() {
        show()
        swapFirstExercise()

        current = newModel(SavedStateHandle())
        rule.waitForIdle()

        rule.onNodeWithTag("block_preview_exercise_1_0_0").assertTextContains("Press banca", substring = true)
        rule.onNodeWithTag("block_preview_changed_1_0_0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun save_sendsTheReplacementForTheSlot() {
        show()
        swapFirstExercise()

        tap("block_preview_save")

        assertEquals(listOf(listOf(BlockEdit(0, 1, "ex-3"))), blocks.saveEdits)
        assertEquals(1, backs)
    }

    @Test
    fun anInvalidReplacement_isRefused_withAMessage_andTheOriginalIsShownAgain() {
        blocks.saveResult = Result.failure(CoachException(CoachFailure.InvalidReplacement))
        show()
        swapFirstExercise()

        tap("block_preview_save")

        assertEquals(listOf("No se pudo cambiar el ejercicio. Inténtalo de nuevo."), messages.shown)
        rule.onNodeWithTag("block_preview_changed_1_0_0", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, backs)
    }

    @Test
    fun reset_bringsBackTheOriginalExercise() {
        show()
        swapFirstExercise()

        tap("block_preview_replace_1_0_0")
        tap("block_preview_replace_reset")

        rule.onNodeWithTag("block_preview_changed_1_0_0", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("block_preview_exercise_1_0_0").assertTextContains("Press banca", substring = true)
    }

    @Test
    fun withoutAlternatives_theSheetShowsTheEmptyState() {
        repository.alternativesById = emptyMap()
        show()

        tap("block_preview_replace_1_0_0")

        rule.onNodeWithTag("block_preview_replace_empty").assertExists()
        rule.onNodeWithText("Este ejercicio no tiene similares").assertExists()
    }

    @Test
    fun offline_theSheetShowsTheOfflineState() {
        repository.alternativesFailure = AppError.Network
        show()

        tap("block_preview_replace_1_0_0")

        rule.onNodeWithTag("block_preview_replace_offline").assertExists()
    }

    @Test
    fun aFailure_offersRetry_thatLoadsAgain() {
        repository.alternativesFailure = AppError.Unknown
        show()
        tap("block_preview_replace_1_0_0")
        repository.alternativesFailure = null

        tap("block_preview_replace_retry")

        rule.onNodeWithTag("block_preview_replace_option_fly").assertExists()
    }

    @Test
    fun pendingReplacementAndOpenSheet_surviveARecreatedModel() {
        show()
        swapFirstExercise()
        tap("block_preview_replace_1_1_0")
        val callsBefore = blocks.saves.size

        current = newModel()
        rule.waitForIdle()

        rule.onNodeWithTag("block_preview_changed_1_0_0", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("block_preview_replace_sheet").assertExists()
        assertEquals(callsBefore, blocks.saves.size)
    }
}
