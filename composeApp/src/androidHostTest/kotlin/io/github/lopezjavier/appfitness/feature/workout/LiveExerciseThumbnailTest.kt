package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun exercise(position: Int, id: String, name: String, imageKey: String?, vararg completed: Boolean) =
    LiveExercise(
        position,
        id,
        name,
        null,
        completed.mapIndexed { i, done -> LiveSetRow(i + 1, "60", "10", done) },
        imageKey = imageKey,
    )

private fun state(vararg exercises: LiveExercise) =
    LiveSessionUiState.Active(name = "Torso", startedAt = SESSION_START, exercises = exercises.toList())

/** T-301: the live exercise header shows the exercise picture (placeholder for custom) and a text-backed done cue. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class LiveExerciseThumbnailTest {
    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun show(state: LiveSessionUiState.Active, fontScale: Float = 1f) {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalExerciseMediaUris provides FakeExerciseMediaUris,
            ) {
                AppTheme { LiveSessionScreen(state, 0, {}, {}, onOpenExercise = { opened += it }) }
            }
        }
        rule.waitForIdle()
    }

    private val mixed = state(
        exercise(0, "bench", "Press banca", "barbell-bench-press", false),
        exercise(1, "mine", "Mi ejercicio", null, false),
    )

    @Test
    fun aCatalogueExercise_showsItsPicture_andACustomOneThePlaceholder() {
        show(mixed)

        rule.onNodeWithTag("live_exercise_0_thumbnail_image", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("live_exercise_1_thumbnail_image", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("live_exercise_1_thumbnail_placeholder", useUnmergedTree = true).assertExists()
    }

    @Test
    fun theThumbnail_keepsTheLogoFootprintOf32dp() {
        show(mixed)

        rule.onNodeWithTag("live_exercise_0_thumbnail_placeholder", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("live_exercise_1_thumbnail_placeholder", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("live_exercise_0_header_visual", useUnmergedTree = true).assertWidthIsEqualTo(32.dp)
    }

    @Test
    fun whenEverySetIsCompleted_theHeaderShowsTheDoneCueDescribedAsCompleted() {
        show(state(exercise(0, "bench", "Press banca", "barbell-bench-press", true, true)))

        rule.onNodeWithTag("live_exercise_0_done", useUnmergedTree = true)
            .assertContentDescriptionEquals("Ejercicio completado")
    }

    @Test
    fun whileASetIsPending_thereIsNoDoneCue() {
        show(state(exercise(0, "bench", "Press banca", "barbell-bench-press", true, false)))

        rule.onNodeWithTag("live_exercise_0_done", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun anExerciseWithoutSets_hasNoDoneCue() {
        show(state(exercise(0, "bench", "Press banca", "barbell-bench-press")))

        rule.onNodeWithTag("live_exercise_0_done", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theHeadingStillOpensTheExercise_andTheMenuRemains() {
        show(mixed)

        rule.onNodeWithTag("live_exercise_0_open").performClick()

        assertEquals(listOf("bench"), opened)
        rule.onNodeWithTag("live_exercise_0_menu").assertIsDisplayed()
    }

    @Test
    fun atFontScale2_theHeaderKeepsItsThumbnailAndCue() {
        show(state(exercise(0, "bench", "Press banca", "barbell-bench-press", true)), fontScale = 2f)

        rule.onNodeWithTag("live_exercise_0_thumbnail_image", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("live_exercise_0_done", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("live_exercise_0_menu").assertIsDisplayed()
    }
}
