package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppLineChartBandTest {
    @get:Rule
    val rule = createComposeRule()

    private val values = listOf<Double?>(80.0, 79.0, 78.0)

    @Test
    fun withoutBandOrReference_thereIsNoOverlay() {
        assertNull(overlaySpec(null, null, values, null))
    }

    @Test
    fun aBandFarAboveTheData_doesNotWidenTheRange() {
        val range = plotRange(null, values, null)!!
        val overlay = overlaySpec(null, 100.0..110.0, values, null)!!

        assertEquals(range, overlay.range)
        assertNull(overlay.band)
    }

    @Test
    fun aBandPartlyOutsideTheRange_isClippedToIt() {
        val range = plotRange(null, values, null)!!
        val overlay = overlaySpec(null, 77.0..100.0, values, null)!!

        assertEquals(range.start.coerceAtLeast(77.0)..range.endInclusive, overlay.band)
    }

    @Test
    fun theReferenceLine_stillWidensTheRange() {
        val plain = plotRange(null, values, null)!!
        val withTarget = plotRange(70.0, values, null)!!

        assertEquals(true, withTarget.start < plain.start)
    }

    @Test
    fun chartWithBand_keepsItsDescription() {
        rule.setContent {
            AppTheme {
                AppLineChart(
                    values = values,
                    labels = listOf("a", "b", "c"),
                    description = "Objetivo 70 kg; peso inicial 82 kg.",
                    modifier = Modifier.testTag("chart"),
                    referenceLine = 70.0,
                    band = 70.0..82.0,
                    markers = listOf(0),
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("chart").assertStateDescription("Objetivo 70 kg; peso inicial 82 kg.")
    }
}
