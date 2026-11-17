package io.github.lopezjavier.appfitness.feature.analytics

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.StrengthSummary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the selected Fuerza row keeps its check and its picture after the screen is recreated. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class StrengthRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun selectedRow_keepsItsCheckAndThumbnail_afterRecreation() {
        val chips = listOf(
            StrengthChip("bench", "Press de banca", imageKey = "barbell-bench-press"),
            StrengthChip("squat", "Sentadilla", imageKey = "barbell-squat"),
        )
        val state = StrengthUiState.Content(chips, "squat", StrengthSummary(100.0, null))
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                AppTheme { StrengthScreen(state, {}, {}, {}, {}) }
            }
        }

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("strength_row_squat").assertIsSelected()
        rule.onNodeWithTag("strength_row_bench").assertIsNotSelected()
        rule.onNodeWithTag("strength_row_squat_selected_check", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("strength_row_squat_thumbnail_image", useUnmergedTree = true).assertExists()
    }
}
