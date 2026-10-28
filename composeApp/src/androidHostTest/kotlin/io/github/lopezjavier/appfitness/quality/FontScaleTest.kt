package io.github.lopezjavier.appfitness.quality

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.domain.model.BuildInfo
import io.github.lopezjavier.appfitness.feature.about.AboutScreen
import io.github.lopezjavier.appfitness.feature.about.AboutUiState
import io.github.lopezjavier.appfitness.feature.activity.ActivityScreen
import io.github.lopezjavier.appfitness.feature.analytics.stats.TrainingStatsScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.weight.CircumferencesUiState
import io.github.lopezjavier.appfitness.feature.weight.WeightScreen
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-46 font scaling: at 130 % the key figures of the main screens stay on screen and inside the window width. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
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

        listOf("live_subtitle", "live_set_0_1_weight", "live_set_0_1_reps", "live_set_0_1_check").forEach {
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

    @Test
    fun statistics_atLargeFont_keepsTheSummaryTilesVisible() {
        rule.setContent { WithFontScale { TrainingStatsScreen(qualityStatsState(), {}, {}, {}) } }
        rule.waitForIdle()

        listOf("stats_tile_workouts", "stats_tile_sets", "stats_tile_volume", "stats_tile_duration").forEach {
            rule.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
            rule.assertWithinWindowWidth(it)
        }
    }
}
