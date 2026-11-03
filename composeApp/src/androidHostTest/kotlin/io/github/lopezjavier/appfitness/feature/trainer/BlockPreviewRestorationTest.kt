package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BlockCardio
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeFavoriteExercisesRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the block preview survives recreation (scroll position, week tab, open reason sheet, a cut request). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h480dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per restored state.
class BlockPreviewRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val blocks = FakeCoachBlockRepository().apply { current = Result.success(coachBlock(sessionsPerWeek = 4)) }
    private val prefs = FakeTrainingPreferencesRepository()
    private val saved = SavedStateHandle()

    private fun newModel(handle: SavedStateHandle = saved) = BlockPreviewViewModel(
        "plan-1",
        blocks,
        FakeExerciseRepository(listOf(exerciseRow("ex-1", "bench"), exerciseRow("ex-2", "row"))),
        FakeExerciseLibrary(
            mapOf("bench" to exerciseInfo("bench", "Press banca"), "row" to exerciseInfo("row", "Remo")),
        ),
        prefs,
        FakeFavoriteExercisesRepository(),
        handle,
    )

    private var current by mutableStateOf(newModel())

    private fun showRoot() {
        rule.setContent {
            AppTheme {
                BlockPreviewRoot("plan-1", {}, {}, viewModel = current, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
    }

    private fun recreate(handle: SavedStateHandle = saved) {
        current = newModel(handle)
        rule.waitForIdle()
    }

    @Test
    fun scrollPosition_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                BlockPreviewScreen(
                    state = BlockPreviewUiState.Preview(
                        coachBlock(sessionsPerWeek = 4),
                        mapOf(
                            "ex-1" to "Press",
                            "ex-2" to "Flexiones",
                        ),
                        1,
                    ),
                    isOnline = flowOf(true),
                    onEvent = {},
                    onBack = {},
                    onOpenExercise = {},
                )
            }
        }
        rule.onNodeWithTag("block_preview_routine_1_3").performScrollTo()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("block_preview_routine_1_3").assertIsDisplayed()
    }

    @Test
    fun selectedWeek_survivesARecreatedModel() {
        showRoot()
        rule.onNodeWithTag("block_preview_tab_3").performClick()
        rule.waitForIdle()

        recreate()

        assertEquals(3, (current.uiState.value as BlockPreviewUiState.Preview).selectedWeek)
        rule.onNodeWithTag("block_preview_routine_3_0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun openReasonSheet_staysOpenAfterARecreatedModel() {
        showRoot()
        rule.onNodeWithTag("block_preview_reason_1_0_0").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Trabaja el pecho con poca fatiga.").assertExists()

        recreate()

        rule.onNodeWithText("Trabaja el pecho con poca fatiga.").assertExists()
    }

    private fun cardioBlockWithSwapTargets(): CoachBlock {
        val base = coachBlock(sessionsPerWeek = 2)
        val cardio = BlockExercise(
            exerciseId = "bike",
            exerciseKey = "bike",
            role = "cardio",
            muscleGroup = "cardio",
            sets = 1,
            repMin = 0,
            repMax = 0,
            restSeconds = 0,
            reason = "Cardio suave.",
            durationMin = 50,
            pace = "suave",
        )
        return base.copy(
            training = base.training.copy(
                cardio = BlockCardio(100, 100, 0, listOf(50, 50), "suave"),
                routineTotalMin = listOf(110, 110),
            ),
            sessions = base.sessions.map { it.copy(exercises = listOf(blockExercise("ex-1"), cardio)) },
        )
    }

    private fun newCardioModel(handle: SavedStateHandle): BlockPreviewViewModel {
        val rows = FakeExerciseRepository(
            listOf(exerciseRow("ex-1", "bench"), exerciseRow("bike", "bike"), exerciseRow("walk", "walk")),
        ).apply { alternativesById = mapOf("bike" to listOf(ExerciseAlternative(rows[2], 0.4))) }
        return BlockPreviewViewModel(
            "plan-1",
            blocks,
            rows,
            FakeExerciseLibrary(
                mapOf(
                    "bench" to exerciseInfo("bench", "Press banca"),
                    "bike" to exerciseInfo("bike", "Bicicleta"),
                    "walk" to exerciseInfo("walk", "Caminar a paso ligero"),
                ),
            ),
            prefs,
            FakeFavoriteExercisesRepository(),
            handle,
        )
    }

    private fun showCardio(handle: SavedStateHandle) {
        blocks.current = Result.success(cardioBlockWithSwapTargets())
        current = newCardioModel(handle)
        showRoot()
    }

    @Test
    fun openCardioSwapSheet_staysOpenOnTheSameSlot_afterARecreatedModel() {
        val handle = SavedStateHandle()
        showCardio(handle)
        rule.onNodeWithTag("block_preview_tab_2").performClick()
        rule.onNodeWithTag("block_preview_replace_2_0_1").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("block_preview_replace_cardio_note").assertExists()

        current = newCardioModel(handle)
        rule.waitForIdle()

        assertEquals(2, (current.uiState.value as BlockPreviewUiState.Preview).selectedWeek)
        rule.onNodeWithTag("block_preview_replace_cardio_note").assertExists()
        rule.onNodeWithTag("block_preview_replace_option_walk").assertExists()
    }

    @Test
    fun pendingCardioSwap_survivesARecreatedModel() {
        val handle = SavedStateHandle()
        showCardio(handle)
        rule.onNodeWithTag("block_preview_replace_1_0_1").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("block_preview_replace_option_walk").performClick()
        rule.waitForIdle()

        current = newCardioModel(handle)
        rule.waitForIdle()

        rule.onNodeWithTag("block_preview_exercise_1_0_1")
            .assertTextContains("Caminar a paso ligero", substring = true)
    }

    @Test
    fun withNothingStored_isInterrupted_andNeverRequests() {
        blocks.current = Result.success(null)
        val handle = SavedStateHandle()
        current = newModel(handle)
        showRoot()

        rule.onNodeWithText("No se recibió el bloque. Inténtalo de nuevo.").assertExists()
        assertEquals(0, blocks.requests.size)
    }
}
