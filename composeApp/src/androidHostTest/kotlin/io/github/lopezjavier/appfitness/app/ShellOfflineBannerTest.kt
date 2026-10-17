package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Tolerance = 0.5.dp

private fun assertAtLeast(message: String, actual: Dp, expected: Dp) =
    assertTrue("$message: $actual >= $expected", actual >= expected - Tolerance)

/** US-04 Offline: the "Sin conexión" banner below the top bar on every destination and layout. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ShellOfflineBannerTest {
    @get:Rule
    val rule = createComposeRule()

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun online_noBanner() {
        rule.setContent { AppTheme { AppScaffold(isOffline = false) } }
        rule.onNodeWithTag("offline_banner").assertDoesNotExist()
    }

    @Test
    fun offline_bar_bannerBelowTopBarAboveContent() {
        rule.setContent { AppTheme { AppScaffold(isOffline = true) } }
        rule.waitForIdle()
        assertAtLeast("banner top vs topbar bottom", bounds("offline_banner").top, bounds("topbar").bottom)
        assertAtLeast("content top vs banner bottom", bounds("diary_screen").top, bounds("offline_banner").bottom)
        rule.onNodeWithTag("offline_banner_message", useUnmergedTree = true).assertTextEquals("Sin conexión")
        rule.onNodeWithTag("offline_banner").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun offline_rail_bannerAboveRail() {
        rule.setContent { AppTheme { AppScaffold(navigationType = AppNavigationType.Rail, isOffline = true) } }
        rule.waitForIdle()
        val banner = bounds("offline_banner")
        assertAtLeast("banner top vs topbar bottom", banner.top, bounds("topbar").bottom)
        assertAtLeast("rail top vs banner bottom", bounds("nav_rail").top, banner.bottom)
        assertTrue("banner spans the root width", kotlin.math.abs((banner.width - bounds("topbar").width).value) < 1f)
        assertAtLeast("content left vs rail right", bounds("diary_screen").left, bounds("nav_rail").right)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-mdpi")
    fun offline_rail_shortLandscape_navigationFitsInsideRoot() {
        rule.setContent { AppTheme { AppScaffold(navigationType = AppNavigationType.Rail, isOffline = true) } }
        rule.waitForIdle()
        val rootBottom = rule.onRoot().getUnclippedBoundsInRoot().bottom
        listOf("nav_tab_diary", "nav_tab_workout", "nav_tab_progress").forEach { tag ->
            val b = bounds(tag)
            assertTrue(
                "$tag bottom ${b.bottom} must be within root bottom $rootBottom",
                b.bottom <= rootBottom + Tolerance,
            )
        }
    }

    @Test
    fun offline_profile_bannerShown() {
        rule.setContent { AppTheme { AppScaffold(isOffline = true) } }
        rule.onNodeWithTag("topbar_avatar").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("offline_banner").assertExists()
        assertAtLeast("profile top vs banner bottom", bounds("profile_screen").top, bounds("offline_banner").bottom)
    }

    @Test
    fun toggleOfflineOnline_bannerHidesAndContentReturns() {
        var offline by mutableStateOf(true)
        rule.setContent { AppTheme { AppScaffold(isOffline = offline) } }
        rule.waitForIdle()
        rule.onNodeWithTag("offline_banner").assertExists()
        offline = false
        rule.waitForIdle()
        rule.onNodeWithTag("offline_banner").assertDoesNotExist()
        assertTrue(
            "content returns to the top bar bottom",
            kotlin.math.abs((bounds("diary_screen").top - bounds("topbar").bottom).value) < 1f,
        )
    }
}
