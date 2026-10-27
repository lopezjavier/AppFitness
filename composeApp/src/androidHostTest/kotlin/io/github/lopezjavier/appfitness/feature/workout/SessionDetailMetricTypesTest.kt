package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.SessionStats
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun cardioSession(volumeKg: Double) = SessionDetailUiState.Content(
    name = "Cardio y core",
    date = LocalDate(2026, 10, 18),
    today = LocalDate(2026, 10, 19),
    stats = SessionStats(durationSeconds = 2_400, completedSets = 4, volumeKg = volumeKg),
    exercises = listOf(
        DetailExercise(0, "e0", "Cinta de correr", listOf(DetailSet(1, SetType.Working, "5,0 km · 30:00")), false),
        DetailExercise(1, "e1", "Plancha", listOf(DetailSet(1, SetType.Working, "01:30")), false),
        DetailExercise(2, "e2", "Crunch", listOf(DetailSet(1, SetType.Working, "20 reps")), false),
        DetailExercise(3, "e3", "Dominadas", listOf(DetailSet(1, SetType.Working, "+10 kg × 8")), false),
    ),
)

/** US-31 on the session detail: sets that are not weight x reps render in the same row format. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class SessionDetailMetricTypesTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: SessionDetailUiState) {
        rule.setContent { AppTheme { SessionDetailScreen(state, {}) } }
        rule.waitForIdle()
    }

    @Test
    fun nonWeightSets_renderTheirOwnText() {
        show(cardioSession(volumeKg = 0.0))

        rule.onNodeWithTag("session_detail_set_0_1").assertTextContains("5,0 km · 30:00")
        rule.onNodeWithTag("session_detail_set_1_1").assertTextContains("01:30")
        rule.onNodeWithTag("session_detail_set_2_1").assertTextContains("20 reps")
        rule.onNodeWithTag("session_detail_set_3_1").assertTextContains("+10 kg × 8")
    }

    @Test
    fun aSessionWithoutVolume_showsADashInsteadOfZeroKilos() {
        show(cardioSession(volumeKg = 0.0))

        rule.onNodeWithTag("session_detail_volume").assertTextContains("—")
    }

    @Test
    fun aSessionWithVolume_stillShowsKilos() {
        show(cardioSession(volumeKg = 672.0))

        rule.onNodeWithTag("session_detail_volume").assertTextContains("672 kg")
    }
}
