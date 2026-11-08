package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
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

    private val state = WeekSummaryUiState.Content(
        weekSummary(
            deload = true,
            nutrition = weekNutrition(decisions = listOf(weekDecision())),
            stalls = listOf(StallDiagnosis("id-bench", StallCause.NoClearCause(proteinGateMissing = true))),
        ),
        mapOf("id-bench" to "Press banca"),
    )

    @Test
    fun recreation_keepsTheNutritionSectionOpen() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            AppTheme { WeekSummaryScreen(state, flowOf(true), {}, {}) }
        }
        rule.onNodeWithTag("week_summary_nutrition_kcal").performScrollTo().assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("week_summary_section_nutrition").assertIsDisplayed()
        rule.onNodeWithTag("week_summary_nutrition_kcal").assertIsDisplayed()
    }

    @Test
    fun recreation_keepsTheSummaryAndTheScrollPosition() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            AppTheme { WeekSummaryScreen(state, flowOf(true), {}, {}) }
        }
        rule.onNodeWithTag("week_summary_section_deload").performScrollTo()
        rule.onNodeWithTag("week_summary_section_deload").assertIsDisplayed()
        rule.onNodeWithTag("week_summary_section_sessions").assertIsNotDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("week_summary_screen").assertIsDisplayed()
        rule.onNodeWithTag("week_summary_section_deload").assertIsDisplayed()
        rule.onNodeWithTag("week_summary_section_sessions").assertIsNotDisplayed()
    }
}
