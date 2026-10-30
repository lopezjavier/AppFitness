package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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
class BlockPreviewRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val blocks = FakeCoachBlockRepository().apply { current = Result.success(coachBlock(sessionsPerWeek = 4)) }
    private val saved = SavedStateHandle()

    private fun newModel(handle: SavedStateHandle = saved) = BlockPreviewViewModel(
        "plan-1",
        blocks,
        FakeExerciseRepository(listOf(exerciseRow("ex-1", "bench"), exerciseRow("ex-2", "row"))),
        FakeExerciseLibrary(
            mapOf("bench" to exerciseInfo("bench", "Press banca"), "row" to exerciseInfo("row", "Remo")),
        ),
        MutableClock(),
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
                    state = BlockPreviewUiState.Preview(coachBlock(sessionsPerWeek = 4), emptyMap(), 1),
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

    @Test
    fun recreatedMidRequest_withNothingStored_isInterrupted_andNeverRequestsAgain() {
        blocks.current = Result.success(null)
        val handle = SavedStateHandle(mapOf("block_requesting" to true))
        current = newModel(handle)
        showRoot()

        rule.onNodeWithText("No se recibió el bloque. Inténtalo de nuevo.").assertExists()
        assertEquals(0, blocks.requests.size)
    }
}
