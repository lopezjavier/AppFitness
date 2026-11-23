package io.github.lopezjavier.appfitness.feature.analytics

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleSetsHistory
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the selected group of "Series por grupo muscular" and the open help are still shown after recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class MuscleVolumeRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val history = MuscleSetsHistory(
        weeks = (0..7).map { LocalDate.fromEpochDays(LocalDate(2026, 9, 7).toEpochDays() + 7 * it) },
        series = mapOf(
            MuscleGroup.Chest to listOf(0.0, 8.0, 0.0, 12.5, 14.0, 10.0, 16.0, 18.5),
            MuscleGroup.Back to listOf(6.0, 0.0, 0.0, 9.0, 11.0, 10.5, 12.0, 8.0),
        ),
    )

    @Test
    fun selectedGroup_keepsItsChipAndChart_afterRecreation() {
        val state = MuscleVolumeUiState.Content(history, MuscleGroup.Back)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { AppTheme { MuscleVolumeScreen(state, {}, {}, {}) } }

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("muscle_sets_chip_back").assertIsSelected()
        rule.onNodeWithTag("muscle_sets_chart").assertExists()
        rule.onNodeWithTag("muscle_sets_group_chest").assertDoesNotExist()
    }

    @Test
    fun openHelp_survivesRecreation() {
        val state = MuscleVolumeUiState.Content(history, null)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { AppTheme { MuscleVolumeScreen(state, {}, {}, {}) } }
        rule.onNodeWithTag("muscle_sets_help").performClick()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("muscle_sets_help_sheet", useUnmergedTree = true).assertExists()
    }
}
