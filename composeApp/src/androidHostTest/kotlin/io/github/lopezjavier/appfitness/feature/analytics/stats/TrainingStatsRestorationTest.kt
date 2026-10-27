package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
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
        val detail = MuscleDetail(MuscleGroup.Triceps, 1.5, listOf(StatsExercise("bench", "Press banca", 9.0, 3)))
        val state = TrainingStatsUiState(
            range = ProgressRange.SixMonths,
            openMuscle = MuscleGroup.Triceps,
            muscleDetail = SectionState.Content(detail),
        )
        val restoration = StateRestorationTester(rule)
        restoration.setContent { AppTheme { TrainingStatsScreen(state, {}, {}, {}) } }

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("stats_range_sixmonths").assertIsSelected()
        rule.onNodeWithTag("stats_muscle_sheet_title").assertTextEquals("Tríceps")
    }
}
