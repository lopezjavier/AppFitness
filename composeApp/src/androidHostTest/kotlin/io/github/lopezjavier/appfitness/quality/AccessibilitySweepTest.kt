package io.github.lopezjavier.appfitness.quality

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-46 accessibility: on the main screens every tappable node is at least 48 x 48 dp and is named (text or content
 * description or click label), and the ring and macro bars expose their value as a state description.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class AccessibilitySweepTest {
    @get:Rule
    val rule = createComposeRule()

    // Layout rounds 48 dp to 47.99998 at xxhdpi.
    private val minTarget = 47.9.dp

    private fun assertEveryClickableIsNamedAndBigEnough(screen: String) {
        rule.waitForIdle()
        val clickable = rule.onAllNodes(hasClickAction())
        val count = clickable.fetchSemanticsNodes().size
        assertTrue("$screen has no tappable node, the sweep would prove nothing", count > 0)
        val problems = (0 until count).mapNotNull { index ->
            val node = clickable[index]
            val config = node.fetchSemanticsNode().config
            val named = config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.isNotBlank() } ||
                config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() } ||
                !config.getOrNull(SemanticsActions.OnClick)?.label.isNullOrBlank()
            // Value fields share one row of sets, so only their height is held to 48 dp.
            val isField = config.getOrNull(SemanticsProperties.EditableText) != null
            val bounds = node.getUnclippedBoundsInRoot()
            val tag = config.getOrNull(SemanticsProperties.TestTag) ?: "#$index"
            when {
                !named -> "$tag has no text or content description"
                bounds.height < minTarget -> "$tag is ${bounds.height} high"
                !isField && bounds.width < minTarget -> "$tag is ${bounds.width} wide"
                else -> null
            }
        }
        assertTrue("$screen: ${problems.joinToString("; ")}", problems.isEmpty())
    }

    @Test
    fun about_everyTappableNodeIsNamedAndBigEnough() {
        rule.setContent {
            AppTheme {
                AboutScreen(
                    AboutUiState(BuildInfo("AppFitness", "1.0", 7, "prod")),
                    onBack = {},
                    onOpenLicenses = {},
                )
            }
        }
        assertEveryClickableIsNamedAndBigEnough("About")
    }

    @Test
    fun diary_everyTappableNodeIsNamedAndBigEnough_andTheRingAndMacroBarsExposeTheirState() {
        rule.setContent { AppTheme { DiaryScreen(qualityDiaryState(), {}) } }
        assertEveryClickableIsNamedAndBigEnough("Diary")

        listOf("diary_ring", "diary_macro_protein", "diary_macro_carbs", "diary_macro_fat").forEach { tag ->
            val config = rule.onNodeWithTag(tag).fetchSemanticsNode().config
            val described = config.getOrNull(SemanticsProperties.StateDescription) != null ||
                config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().isNotEmpty()
            assertTrue("$tag exposes no state or content description", described)
        }
    }

    @Test
    fun weight_everyTappableNodeIsNamedAndBigEnough() {
        val model = qualityWeightModel()
        rule.setContent {
            AppTheme {
                val state by model.uiState.collectAsState()
                WeightScreen(state, model::onEvent, CircumferencesUiState.Loading, {}, timeZone = TimeZone.UTC)
            }
        }
        assertEveryClickableIsNamedAndBigEnough("Weight")
    }

    @Test
    fun trainingStats_everyTappableNodeIsNamedAndBigEnough() {
        rule.setContent { AppTheme { TrainingStatsScreen(qualityStatsState(), {}, {}, {}) } }
        assertEveryClickableIsNamedAndBigEnough("Training stats")
    }

    @Test
    fun activity_everyTappableNodeIsNamedAndBigEnough() {
        rule.setContent {
            AppTheme { ActivityScreen(qualityActivityState(), MutableStateFlow(true), {}, {}, {}) }
        }
        assertEveryClickableIsNamedAndBigEnough("Activity")
    }

    @Test
    fun liveSession_everyTappableNodeIsNamedAndBigEnough() {
        rule.setContent { AppTheme { LiveSessionScreen(qualityLiveState(), 0, {}, {}, onOpenExercise = {}) } }
        assertEveryClickableIsNamedAndBigEnough("Live session")
    }
}
