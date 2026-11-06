package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RestTimerBarTest {
    private val c = KineticDarkColors
    private val typography = appTypography(FontFamily.Default)

    @Test
    fun countdownText_start() = assertEquals("02:00", RestTimerBarDefaults.countdownText(120.seconds))

    @Test
    fun countdownText_gherkinExamples() {
        assertEquals("01:12", RestTimerBarDefaults.countdownText(72.seconds))
        assertEquals("01:42", RestTimerBarDefaults.countdownText(102.seconds))
        assertEquals("00:30", RestTimerBarDefaults.countdownText(30.seconds))
    }

    @Test
    fun countdownText_ceilsPartialSeconds() {
        assertEquals("02:00", RestTimerBarDefaults.countdownText(119.seconds + 1.milliseconds))
        assertEquals("01:59", RestTimerBarDefaults.countdownText(119.seconds))
        assertEquals("00:01", RestTimerBarDefaults.countdownText(999.milliseconds))
        assertEquals("00:01", RestTimerBarDefaults.countdownText(1.milliseconds))
    }

    @Test
    fun countdownText_zeroAndNegative() {
        assertEquals("00:00", RestTimerBarDefaults.countdownText(Duration.ZERO))
        assertEquals("00:00", RestTimerBarDefaults.countdownText((-5).seconds))
    }

    @Test
    fun countdownText_maxRestAndBeyond() {
        assertEquals("10:00", RestTimerBarDefaults.countdownText(600.seconds))
        assertEquals("100:00", RestTimerBarDefaults.countdownText(6000.seconds))
    }

    @Test
    fun progress_proportional() {
        assertEquals(0.5f, RestTimerBarDefaults.progress(60.seconds, 120.seconds))
        assertEquals(1f, RestTimerBarDefaults.progress(120.seconds, 120.seconds))
        assertEquals(0f, RestTimerBarDefaults.progress(Duration.ZERO, 120.seconds))
    }

    @Test
    fun progress_clamped() {
        assertEquals(1f, RestTimerBarDefaults.progress(130.seconds, 120.seconds))
        assertEquals(0f, RestTimerBarDefaults.progress((-5).seconds, 120.seconds))
        assertEquals(0f, RestTimerBarDefaults.progress(10.seconds, Duration.ZERO))
    }

    @Test
    fun extend_gherkinExample() {
        val extended = RestTimerBarDefaults.extend(RestCountdown(72.seconds, 120.seconds))
        assertEquals(RestCountdown(102.seconds, 120.seconds), extended)
        assertEquals("01:42", RestTimerBarDefaults.countdownText(extended.remaining))
    }

    @Test
    fun extend_beyondTotal_raisesTotal() {
        val extended = RestTimerBarDefaults.extend(RestCountdown(102.seconds, 120.seconds))
        assertEquals(RestCountdown(132.seconds, 132.seconds), extended)
        assertEquals(1f, RestTimerBarDefaults.progress(extended.remaining, extended.total))
    }

    @Test
    fun extend_fromZeroOrNegative() {
        val expected = RestCountdown(30.seconds, 120.seconds)
        assertEquals(expected, RestTimerBarDefaults.extend(RestCountdown(Duration.ZERO, 120.seconds)))
        assertEquals(expected, RestTimerBarDefaults.extend(RestCountdown((-3).seconds, 120.seconds)))
    }

    @Test
    fun spokenParts() {
        assertEquals(1 to 12, RestTimerBarDefaults.spokenParts(72.seconds))
        assertEquals(0 to 1, RestTimerBarDefaults.spokenParts(999.milliseconds))
        assertEquals(0 to 0, RestTimerBarDefaults.spokenParts(Duration.ZERO))
    }

    @Test
    fun speaksSecondsOnly_whenNoMinutes() {
        assertTrue(RestTimerBarDefaults.speaksSecondsOnly(30.seconds))
        assertFalse(RestTimerBarDefaults.speaksSecondsOnly(72.seconds))
        assertFalse(RestTimerBarDefaults.speaksSecondsOnly(60.seconds))
    }

    @Test
    fun extension_is30s() = assertEquals(30.seconds, RestTimerBarDefaults.Extension)

    @Test
    fun minHeight48_actionTouch48() {
        assertEquals(48.dp, RestTimerBarDefaults.MinHeight)
        assertEquals(48.dp, RestTimerBarDefaults.ActionMinTouch)
    }

    @Test
    fun progressHeight_onSubGrid() = assertEquals(4.dp, RestTimerBarDefaults.ProgressHeight)

    @Test
    fun colors_tokens() {
        val colors = RestTimerBarDefaults.colors(c)
        assertEquals(c.surfaceOverlay, colors.container)
        assertEquals(c.athleticCyan, colors.countdown)
        assertEquals(c.athleticCyan, colors.progress)
        assertEquals(c.surfaceBase, colors.progressTrack)
        assertEquals(c.surfaceRaised, colors.actionContainer)
        assertEquals(c.textPrimary, colors.addContent)
        assertEquals(c.textSecondary, colors.skipContent)
    }

    @Test
    fun countdownStyle_isHeadlineMetric_tabular() {
        val style = RestTimerBarDefaults.countdownStyle(typography)
        assertEquals(typography.headlineMetric.fontSize, style.fontSize)
        assertEquals(NUMERIC_FONT_FEATURES, style.fontFeatureSettings)
    }

    @Test
    fun actionStyle_isTitleMedium() = assertEquals(typography.titleMedium, RestTimerBarDefaults.actionStyle(typography))

    @Test
    fun elapsedText_countsUpAndTruncatesSeconds() {
        assertEquals("12:05", CardioTimerBarDefaults.elapsedText(12.minutes + 5.seconds + 900.milliseconds, 40.minutes))
        assertEquals("00:00", CardioTimerBarDefaults.elapsedText(Duration.ZERO, null))
    }

    @Test
    fun elapsedText_overTargetShowsThePlusExcess() {
        assertEquals("+01:30", CardioTimerBarDefaults.elapsedText(41.minutes + 30.seconds, 40.minutes))
        assertEquals("40:00", CardioTimerBarDefaults.elapsedText(40.minutes, 40.minutes))
    }

    @Test
    fun isOverTarget_neverWithoutATarget() {
        assertFalse(CardioTimerBarDefaults.isOverTarget(5.minutes, null))
        assertFalse(CardioTimerBarDefaults.isOverTarget(40.minutes, 40.minutes))
        assertTrue(CardioTimerBarDefaults.isOverTarget(40.minutes + 1.seconds, 40.minutes))
    }

    @Test
    fun cardioProgress_isElapsedOverTargetCoercedTo01() {
        assertEquals(0.5f, CardioTimerBarDefaults.cardioProgress(20.minutes, 40.minutes))
        assertEquals(1f, CardioTimerBarDefaults.cardioProgress(50.minutes, 40.minutes))
        assertEquals(0f, CardioTimerBarDefaults.cardioProgress(5.minutes, null))
    }

    @Test
    fun cardioColors_limeThenAmberOverTarget_restKeepsCyan() {
        assertEquals(c.neonLime, CardioTimerBarDefaults.cardioColors(c, overTarget = false).countdown)
        assertEquals(c.energyAmber, CardioTimerBarDefaults.cardioColors(c, overTarget = true).progress)
        assertEquals(c.athleticCyan, RestTimerBarDefaults.colors(c).countdown)
    }

    @Test
    fun elapsedParts_truncate() {
        assertEquals(12 to 5, CardioTimerBarDefaults.elapsedParts(12.minutes + 5.seconds + 999.milliseconds))
    }
}
