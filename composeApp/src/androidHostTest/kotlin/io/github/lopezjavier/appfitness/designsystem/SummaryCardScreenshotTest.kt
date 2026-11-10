package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden image of [SummaryCard] with two lines and with none, on a 360 dp wide screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h400dp-xxhdpi")
class SummaryCardScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun withAndWithoutLines() {
        rule.setContent {
            AppTheme {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SummaryCard("Tu plan", listOf("Definición · 2.100 kcal · 150 g de proteína", "Cardio 90 min"), {
                    }, "a")
                    SummaryCard("Resumen semanal", emptyList(), {}, "b")
                }
            }
        }
        rule.onRoot().assertScreenshot("summary_card")
    }
}
