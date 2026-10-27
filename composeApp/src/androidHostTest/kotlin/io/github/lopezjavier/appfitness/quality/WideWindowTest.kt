package io.github.lopezjavier.appfitness.quality

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.BuildInfo
import io.github.lopezjavier.appfitness.feature.about.AboutScreen
import io.github.lopezjavier.appfitness.feature.about.AboutUiState
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.weight.CircumferencesUiState
import io.github.lopezjavier.appfitness.feature.weight.WeightScreen
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionScreen
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-46 tablet: at 840 x 600 dp landscape the dashboards lay out in two columns under a capped width, and the
 * single-column screens stay capped and centred instead of stretching.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w840dp-h600dp-land-xxhdpi")
class WideWindowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun assertNoWiderThan(tag: String, max: androidx.compose.ui.unit.Dp) {
        val width = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().width
        assertTrue("$tag is $width wide, the cap is $max", width <= max)
    }

    @Test
    fun diary_usesTwoColumnsWithinTheCap() {
        rule.setContent { AppTheme { DiaryScreen(qualityDiaryState(), {}) } }
        rule.waitForIdle()

        rule.onNodeWithTag("diary_two_column").assertIsDisplayed()
        assertNoWiderThan("diary_two_column", WindowWidthClassDefaults.TwoColumnContentMaxWidth)
    }

    @Test
    fun trainingStats_usesTwoColumnsWithinTheCap() {
        rule.setContent { AppTheme { TrainingStatsScreen(qualityStatsState(), {}, {}, {}) } }
        rule.waitForIdle()

        rule.onNodeWithTag("training_stats_two_column").performScrollTo().assertIsDisplayed()
        assertNoWiderThan("training_stats_two_column", WindowWidthClassDefaults.TwoColumnContentMaxWidth)
    }

    @Test
    fun weight_staysACappedSingleColumn() {
        val model = qualityWeightModel()
        rule.setContent {
            AppTheme {
                val state by model.uiState.collectAsState()
                WeightScreen(state, model::onEvent, CircumferencesUiState.Loading, {}, timeZone = TimeZone.UTC)
            }
        }
        rule.waitForIdle()

        assertNoWiderThan("weight_current_card", WindowWidthClassDefaults.CardColumnMaxWidth)
    }

    @Test
    fun liveSession_staysACappedSingleColumn() {
        rule.setContent { AppTheme { LiveSessionScreen(qualityLiveState(), 0, {}, {}, onOpenExercise = {}) } }
        rule.waitForIdle()

        assertNoWiderThan("live_exercise_0_add_set", WindowWidthClassDefaults.CardColumnMaxWidth)
    }

    @Test
    fun about_staysACappedSingleColumn() {
        rule.setContent {
            AppTheme {
                AboutScreen(AboutUiState(BuildInfo("AppFitness", "1.0", 7, "prod")), onBack = {}, onOpenLicenses = {})
            }
        }
        rule.waitForIdle()

        assertNoWiderThan("about_licenses_row", WindowWidthClassDefaults.FormContentMaxWidth)
    }
}
