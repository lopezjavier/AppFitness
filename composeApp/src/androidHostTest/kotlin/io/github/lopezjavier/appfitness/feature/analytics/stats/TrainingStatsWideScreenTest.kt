package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-56 tablets: from 600 dp "Estadísticas" lays its cards out in two columns under the same bar. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w840dp-h1000dp-xxhdpi")
class TrainingStatsWideScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun groups() = MuscleGroup.entries.map { MuscleGroupSets(it, if (it == MuscleGroup.Chest) 3.0 else 0.0) }

    private val state = TrainingStatsUiState(
        summary = SectionState.Content(
            TrainingSummary(workouts = 4, sets = 40, volume = 12_500.0, durationSeconds = 60),
        ),
        setCounts = SectionState.Content(listOf(SetCountPoint(LocalDate(2026, 10, 5), 24))),
        muscleSets = SectionState.Content(groups()),
        topExercises = SectionState.Content(listOf(StatsExercise("bench", "Press banca", 9.0, 3))),
        radar = SectionState.Content(MuscleRadar(groups(), null)),
        bodyMap = SectionState.Content(groups().filter { it.muscleGroup != MuscleGroup.FullBody }),
    )

    @Test
    fun wideWindow_showsTheRadarInASecondColumnNextToTheSummary() {
        rule.setContent { AppTheme { TrainingStatsScreen(state, {}, {}, {}) } }
        rule.waitForIdle()

        rule.onNodeWithTag("stats_radar").performScrollTo()
        val radar = rule.onNodeWithTag("stats_radar").getUnclippedBoundsInRoot()
        val tiles = rule.onNodeWithTag("stats_tile_workouts").getUnclippedBoundsInRoot()

        assertTrue("radar ${radar.left} should be right of tiles ${tiles.left}", radar.left > tiles.right - 1.dp)
    }
}
