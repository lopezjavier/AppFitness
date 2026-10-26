package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppLinearProgressTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(fraction: Float) {
        rule.setContent {
            AppTheme {
                AppLinearProgress(fraction, "Progreso hacia el objetivo, 50 %", Modifier.testTag("bar"))
            }
        }
    }

    @Test
    fun speaksTheStateDescription() {
        show(0.5f)

        val config = rule.onNodeWithTag("bar").fetchSemanticsNode().config
        assertEquals("Progreso hacia el objetivo, 50 %", config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun exposesTheFractionAsProgress() {
        show(0.5f)

        val info = rule.onNodeWithTag(
            "bar",
        ).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        assertEquals(ProgressBarRangeInfo(0.5f, 0f..1f), info)
    }

    @Test
    fun clampsAFractionOutsideZeroToOne() {
        show(1.7f)

        val info = rule.onNodeWithTag(
            "bar",
        ).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        assertEquals(1f, info?.current)
    }
}
