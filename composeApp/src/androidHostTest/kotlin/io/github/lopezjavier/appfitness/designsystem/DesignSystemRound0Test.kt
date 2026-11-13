package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_inbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Host coverage for the design system round 0 visuals (T-292..T-295): stop dot, sleep ramp, error buttons, R-64. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class DesignSystemRound0Test {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun linearProgress_showsTheTrackStopDot() {
        rule.setContent { AppTheme { AppLinearProgress(0.5f, "Progreso") } }

        rule.onAllNodesWithTag(AppLinearProgressDefaults.STOP_DOT_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun macroProgressBar_showsTheTrackStopDot() {
        rule.setContent { AppTheme { MacroProgressBar("Proteínas", Macro.Protein, 50, 100) } }

        rule.onAllNodesWithTag(AppLinearProgressDefaults.STOP_DOT_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun stopDot_is4dpSquare() {
        rule.setContent { AppTheme { AppLinearProgress(0f, "Progreso") } }

        val bounds = rule.onNodeWithTag(
            AppLinearProgressDefaults.STOP_DOT_TAG,
            useUnmergedTree = true,
        ).getUnclippedBoundsInRoot()
        assertEquals(4f, (bounds.right - bounds.left).value, 0.01f)
        assertEquals(4f, (bounds.bottom - bounds.top).value, 0.01f)
    }

    @Test
    fun sleepRamp_isOrderedCyanAlphas_andUnknownIsTertiary() {
        val colors = mutableMapOf<SleepStageKind, Color>()
        var cyan = Color.Unspecified
        var tertiary = Color.Unspecified
        var amber = Color.Unspecified
        rule.setContent {
            AppTheme {
                cyan = AppTheme.colors.athleticCyan
                tertiary = AppTheme.colors.textTertiary
                amber = AppTheme.colors.energyAmber
                SleepStageKind.entries.forEach { colors[it] = SleepStagesBarDefaults.color(it) }
            }
        }

        assertEquals(cyan, colors[SleepStageKind.Deep])
        assertEquals(cyan.copy(alpha = 0.75f), colors[SleepStageKind.Rem])
        assertEquals(cyan.copy(alpha = 0.5f), colors[SleepStageKind.Light])
        assertEquals(amber, colors[SleepStageKind.Awake])
        assertEquals(tertiary, colors[SleepStageKind.Unknown])
    }

    @Test
    fun errorState_bothButtonsAreAtLeast200dpWide() {
        rule.setContent {
            AppTheme {
                ErrorState(
                    message = "Falló",
                    action = AppStateAction("Reintentar") {},
                    secondaryAction = AppStateAction("Crear manualmente") {},
                    actionTestTag = "primary",
                    secondaryActionTestTag = "secondary",
                )
            }
        }

        listOf("primary", "secondary").forEach {
            val width = rule.onNodeWithTag(it).getUnclippedBoundsInRoot().let { (it.right - it.left).value }
            assertTrue("$it is $width dp wide", width >= 200f)
        }
    }

    @Test
    fun emptyState_buttonIsAtLeast200dpWide() {
        rule.setContent {
            AppTheme {
                EmptyState("Nada", Res.drawable.ic_inbox, action = AppStateAction("Añadir") {}, testTagPrefix = "empty")
            }
        }

        val width = rule.onNodeWithTag("empty_action").getUnclippedBoundsInRoot().let { (it.right - it.left).value }
        assertTrue("action is $width dp wide", width >= 200f)
    }
}
