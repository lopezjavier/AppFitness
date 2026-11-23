package io.github.lopezjavier.appfitness.quality

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.domain.model.BuildInfo
import io.github.lopezjavier.appfitness.feature.about.AboutScreen
import io.github.lopezjavier.appfitness.feature.about.AboutUiState
import io.github.lopezjavier.appfitness.feature.activity.ActivityScreen
import io.github.lopezjavier.appfitness.feature.analytics.MuscleVolumeScreen
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.trainer.hasTextIgnoringNbsp
import io.github.lopezjavier.appfitness.feature.weight.CircumferencesUiState
import io.github.lopezjavier.appfitness.feature.weight.WeightScreen
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-46 font scaling: at 130 % the key figures of the main screens stay on screen and inside the window width. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per screen and scale.
class FontScaleTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun about_atLargeFont_showsNameVersionCreditsAndLicences() {
        rule.setContent {
            WithFontScale {
                AboutScreen(
                    AboutUiState(BuildInfo("AppFitness", "1.0", 7, "local")),
                    onBack = {},
                    onOpenLicenses = {},
                )
            }
        }
        rule.waitForIdle()

        listOf("about_app_name", "about_version").forEach {
            rule.onNodeWithTag(it).assertIsDisplayed()
            rule.assertWithinWindowWidth(it)
        }
        listOf("about_credit_0", "about_credit_1", "about_credit_2", "about_licenses_row").forEach {
            rule.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
            rule.assertWithinWindowWidth(it)
        }
    }

    @Test
    fun diary_atLargeFont_keepsTheRingAndMacroStripVisible() {
        rule.setContent { WithFontScale { DiaryScreen(qualityDiaryState(), {}) } }
        rule.waitForIdle()

        listOf("diary_ring", "diary_macro_protein", "diary_macro_carbs", "diary_macro_fat").forEach {
            rule.onNodeWithTag(it).assertIsDisplayed()
            rule.assertWithinWindowWidth(it)
        }
    }

    @Test
    fun weight_atLargeFont_keepsTheCurrentWeightCardVisible() {
        val model = qualityWeightModel()
        rule.setContent {
            WithFontScale {
                val state by model.uiState.collectAsState()
                WeightScreen(state, model::onEvent, CircumferencesUiState.Loading, {}, timeZone = TimeZone.UTC)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("weight_current_card").assertIsDisplayed()
        rule.assertWithinWindowWidth("weight_current_card")
    }

    @Test
    fun liveSession_atLargeFont_keepsTheTimerAndTheSetRowReachable() {
        rule.setContent { WithFontScale { LiveSessionScreen(qualityLiveState(), 3_725, {}, {}, onOpenExercise = {}) } }
        rule.waitForIdle()

        listOf("live_timer", "live_set_0_1_weight", "live_set_0_1_reps", "live_set_0_1_check").forEach {
            rule.onNodeWithTag(it).assertIsDisplayed()
            rule.assertWithinWindowWidth(it)
        }
    }

    @Test
    fun activity_atLargeFont_stacksTheCardsInOneColumn() {
        rule.setContent {
            WithFontScale {
                ActivityScreen(qualityActivityState(), MutableStateFlow(true), {}, qualityHealthSync(), {}, {})
            }
        }
        rule.waitForIdle()

        listOf(
            "activity_sleep_card",
            "activity_steps_card",
            "activity_active_kcal_card",
            "activity_total_kcal_card",
            "activity_resting_hr_card",
        ).forEach {
            rule.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
            rule.assertWithinWindowWidth(it)
        }
    }

    private fun assertStatsRowsInsideTheWindow(scale: Float) {
        rule.setContent { WithFontScale(scale) { TrainingStatsScreen(qualityStatsState(), {}, {}, {}) } }
        rule.waitForIdle()

        listOf("stats_progress_card", "stats_progress_row_bench", "stats_records_card", "stats_record_1").forEach {
            rule.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
            rule.assertWithinWindowWidth(it)
        }
    }

    @Test
    fun statistics_atFontScale1_3_keepProgressRowsAndRecordsInsideTheWindow() = assertStatsRowsInsideTheWindow(1.3f)

    @Test
    fun statistics_atFontScale2_0_keepProgressRowsAndRecordsInsideTheWindow() = assertStatsRowsInsideTheWindow(2f)

    @Test
    fun statistics_atFontScale1_3_theRecordBadgeMovesUnderTheFactsLine() {
        rule.setContent { WithFontScale(1.3f) { TrainingStatsScreen(qualityStatsState(), {}, {}, {}) } }
        rule.waitForIdle()

        val record = rule.onNodeWithTag("stats_record_1").performScrollTo().getUnclippedBoundsInRoot()
        val facts = rule.onNode(hasTextIgnoringNbsp("antes 95 kg"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val badge = rule.onNode(hasTextIgnoringNbsp("101 kg (+6 kg)"), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue("badge ${badge.top} should sit below the facts ${facts.bottom}", badge.top >= facts.bottom)
        assertTrue("badge is inside the record row", badge.bottom <= record.bottom)
    }

    private fun assertGroupHeaderIsTwoRows(scale: Float) {
        rule.setContent { WithFontScale(scale) { MuscleVolumeScreen(qualityMuscleSetsState(), {}, {}, {}) } }
        rule.waitForIdle()

        rule.onNodeWithTag("muscle_sets_group_chest").performScrollTo().assertIsDisplayed()
        rule.assertWithinWindowWidth("muscle_sets_group_chest")
        val inCard = hasAnyAncestor(hasTestTag("muscle_sets_group_chest"))
        val name = rule.onNode(hasText("Pecho") and inCard, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val label = rule.onNode(hasText("Esta semana") and inCard, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the name sits above the figures row", name.bottom <= label.top)
    }

    @Test
    fun muscleSets_atFontScale1_0_groupNameIsOnItsOwnRow() = assertGroupHeaderIsTwoRows(1f)

    @Test
    fun muscleSets_atFontScale1_3_groupNameIsOnItsOwnRow() = assertGroupHeaderIsTwoRows(1.3f)

    @Test
    fun muscleSets_atFontScale2_0_groupNameIsOnItsOwnRow() = assertGroupHeaderIsTwoRows(2f)
}
