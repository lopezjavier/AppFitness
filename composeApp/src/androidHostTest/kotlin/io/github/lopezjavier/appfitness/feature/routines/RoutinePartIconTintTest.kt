package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R-125: the part icon is cyan on the next routine and lime otherwise, read from the rendered pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class RoutinePartIconTintTest {
    @get:Rule
    val rule = createComposeRule()

    /** Mean of blue minus red over the badge: positive for cyan, negative for lime. */
    private fun blueBias(tag: String): Float {
        val image = rule.onNodeWithTag(tag).captureToImage()
        val map = image.toPixelMap()
        val pixels = (0 until image.width).flatMap { x -> (0 until image.height).map { y -> map[x, y] } }
        return pixels.map { it.blue - it.red }.average().toFloat()
    }

    @Test
    fun card_tintsTheNextRoutineCyan_andTheOthersLime() {
        val repository = FakeRoutineRepository(listOf(routineOf("r1", "Torso", "a"), routineOf("r2", "Pierna", "b")))
        val vm =
            RoutinesViewModel(
                repository,
                FakeWorkoutSessionRepository(),
                FakeConnectivityObserver(),
                FakeClock(),
                exerciseRepository = FakeExerciseRepository(),
            )
        rule.setContent {
            AppTheme {
                val state by vm.uiState.collectAsState()
                LaunchedEffect(vm) { vm.effects.collect { } }
                RoutinesScreen(state, vm::onEvent)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("routines_row_menu_r1").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Siguiente entreno").performClick()
        rule.waitForIdle()

        assertTrue("next routine icon is cyan", blueBias("routines_row_icon_r1") > 0f)
        assertTrue("other routine icon is lime", blueBias("routines_row_icon_r2") < 0f)
    }

    @Test
    fun detailsHero_tintsTheIconLime() {
        rule.setContent {
            AppTheme {
                RoutineDetailsScreen(
                    state = RoutineDetailsUiState.Content(name = "Torso A", exercises = emptyList(), lastDone = null),
                    onEvent = {},
                    onEdit = {},
                    onOpenExercise = {},
                    onBack = {},
                    timeZone = TimeZone.UTC,
                    today = LocalDate(2026, 10, 26),
                )
            }
        }
        rule.waitForIdle()

        assertTrue("details icon is lime", blueBias("routine_details_summary_icon") < 0f)
    }
}
