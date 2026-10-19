package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseSummary
import io.github.lopezjavier.appfitness.domain.usecase.SessionSummary
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a wide window the live content is one centred column of at most 640 dp and the summary at most 480 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w840dp-h900dp-xxhdpi")
class LiveSessionTabletTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun liveContent_isCappedAndCentred() {
        val exercise = LiveExercise(0, "e1", "Press banca", null, listOf(LiveSetRow(1, "", "", false)))
        val state = LiveSessionUiState.Active("Torso", SESSION_START, listOf(exercise))
        rule.setContent { AppTheme { LiveSessionScreen(state, 0, {}, {}) } }

        assertTrue(rule.onNodeWithTag("live_exercise_0_add_set").getUnclippedBoundsInRoot().width <= 640.dp)
        assertTrue(rule.onNodeWithTag("live_finish_button").getUnclippedBoundsInRoot().width <= 640.dp)
    }

    @Test
    fun summary_isCapped() {
        val summary = LiveSummary(
            SessionSummary(60, 1, listOf(ExerciseSummary(0, "e1", 100.0, false))),
            mapOf(0 to "Press banca"),
        )
        rule.setContent { AppTheme { LiveSessionScreen(LiveSessionUiState.Finished(summary), 0, {}, {}) } }

        assertTrue(rule.onNodeWithTag("summary_done").getUnclippedBoundsInRoot().width <= 480.dp)
    }
}
