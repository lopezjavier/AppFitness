package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: leaving the app and coming back keeps the summary open and where the user had scrolled to. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h400dp-xxhdpi")
class WeekSummaryRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val summary = v2Summary(deload = true).copy(
        stalls = listOf(StallDiagnosis("id-bench", StallCause.NoClearCause(proteinGateMissing = true))),
    )
    private val names = mapOf("id-bench" to "Press banca", "id-fly" to "Aperturas", "id-press" to "Press inclinado")
    private val state = WeekSummaryUiState.Content(summary, names, weekReportOf(summary, names, live = true))

    @Test
    fun recreation_keepsTheGroupSheetOpen() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            AppTheme { WeekSummaryScreen(state, flowOf(true), {}, {}) }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("week_summary_strength_group_chest"))
        rule.onNodeWithTag("week_summary_strength_group_chest").performClick()
        rule.onNodeWithTag("week_summary_strength_sheet_exercise_id-bench").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("week_summary_strength_sheet_exercise_id-bench").assertIsDisplayed()
    }

    @Test
    fun recreation_keepsTheSummaryAndTheScrollPosition() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            AppTheme { WeekSummaryScreen(state, flowOf(true), {}, {}) }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("week_summary_section_deload"))
        rule.onNodeWithTag("week_summary_section_deload").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("week_summary_screen").assertIsDisplayed()
        rule.onNodeWithTag("week_summary_section_deload").assertIsDisplayed()
    }
}
