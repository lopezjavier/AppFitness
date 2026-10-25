package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class RadarChartTest {
    @get:Rule
    val rule = createComposeRule()

    private val axes = listOf("Pecho", "Espalda", "Hombros", "Bíceps", "Tríceps", "Cuerpo completo")

    private fun show(current: List<Double>, previous: List<Double>?, fontScale: Float = 1f) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme {
                    Column {
                        RadarLegend("Actual", previous?.let { "Periodo anterior" })
                        RadarChart(
                            axes = axes,
                            current = current,
                            previous = previous,
                            description = "Pecho 3 series, Espalda 0 series",
                            modifier = Modifier.fillMaxWidth().testTag("radar"),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun chart_exposesTheValuesAsStateDescription() {
        show(listOf(3.0, 0.0, 1.5, 0.0, 0.0, 0.0), listOf(1.0, 2.0, 0.0, 0.0, 0.0, 0.0))

        val description = rule.onNodeWithTag("radar").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals("Pecho 3 series, Espalda 0 series", description)
    }

    @Test
    fun legend_showsBothSeriesWhenThereIsAPreviousPeriod() {
        show(listOf(3.0, 0.0, 1.5, 0.0, 0.0, 0.0), listOf(1.0, 2.0, 0.0, 0.0, 0.0, 0.0))

        rule.onNodeWithText("Actual").assertIsDisplayed()
        rule.onNodeWithText("Periodo anterior").assertIsDisplayed()
    }

    @Test
    fun legend_omitsThePreviousSeriesWithoutOne() {
        show(listOf(3.0, 0.0, 1.5, 0.0, 0.0, 0.0), null)

        rule.onNodeWithText("Actual").assertIsDisplayed()
        rule.onNodeWithText("Periodo anterior").assertDoesNotExist()
    }

    @Test
    fun allZero_andLargeFontScale_stillCompose() {
        show(List(6) { 0.0 }, List(6) { 0.0 }, fontScale = 1.3f)

        rule.onNodeWithTag("radar").assertIsDisplayed()
    }
}
