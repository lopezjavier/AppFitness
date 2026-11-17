package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the selected range and the open muscle sheet are still shown after the screen is recreated. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class TrainingStatsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun selectedRange_andOpenMuscleSheet_surviveRecreation() {
        val bench = StatsExercise("bench", "Press banca", 9.0, 3, imageKey = "barbell-bench-press")
        val detail = MuscleDetail(MuscleGroup.Triceps, 1.5, listOf(bench))
        val state = TrainingStatsUiState(
            range = ProgressRange.SixMonths,
            openMuscle = MuscleGroup.Triceps,
            muscleDetail = SectionState.Content(detail),
        )
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                AppTheme { TrainingStatsScreen(state, {}, {}, {}) }
            }
        }

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("stats_range_sixmonths").assertIsSelected()
        rule.onNodeWithTag("stats_muscle_sheet_title").assertTextEquals("Tríceps")
        rule.onNodeWithTag("stats_muscle_sheet_exercise_bench_thumbnail_image", useUnmergedTree = true).assertExists()
    }

    @Test
    fun openAdductorsSheet_survivesRecreation() {
        val detail = MuscleDetail(MuscleGroup.Adductors, 0.0, emptyList())
        val state = TrainingStatsUiState(
            openMuscle = MuscleGroup.Adductors,
            muscleDetail = SectionState.Content(detail),
        )
        val restoration = StateRestorationTester(rule)
        restoration.setContent { AppTheme { TrainingStatsScreen(state, {}, {}, {}) } }

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("stats_muscle_sheet_title").assertTextEquals("Aductores")
    }
}
