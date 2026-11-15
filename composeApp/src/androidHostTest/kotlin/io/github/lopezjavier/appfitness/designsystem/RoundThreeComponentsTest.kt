package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 3 components of the design system (T-283): [SegmentedProgress], [BarListRow], [DecisionButtonRow],
 * [SectionCard], [RowGroupCard] and the one-time fill motion of [rememberFillProgress] (R-97).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per behaviour of the five components.
class RoundThreeComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun mainClockFillTotalMillis() =
        (FillMotionDefaults.DurationMillis + FillMotionDefaults.StaggerMillis).toLong()

    // SegmentedProgress

    @Test
    fun segmentedProgress_speaksItsStateDescription_andDrawsOneSegmentPerStep() {
        rule.setContent {
            AppTheme { SegmentedProgress(total = 4, done = 1, stateDescription = "Semana 2 de 4", testTag = "seg") }
        }

        val description = rule.onNodeWithTag("seg").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.StateDescription)
        assertEquals("Semana 2 de 4", description)
        for (i in 0 until 4) rule.onNodeWithTag("seg_segment_$i", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("seg_segment_4", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun segmentedProgress_aboveTwelveSteps_fallsBackToALinearBar() {
        rule.setContent {
            AppTheme {
                SegmentedProgress(total = 13, done = 5, stateDescription = "5 de 13", testTag = "seg")
            }
        }

        rule.onNodeWithTag("seg").assertExists()
        rule.onAllNodesWithTag("seg_segment_0", useUnmergedTree = true).assertCountEquals(0)
    }

    // BarListRow

    @Test
    fun barListRow_isOneNode_readingLabelThenValue() {
        rule.setContent {
            AppTheme { BarListRow(label = "Pecho", value = "10", fraction = 0.5f, testTag = "bar_row") }
        }

        rule.onNodeWithTag("bar_row").assertTextContains("Pecho")
        rule.onNodeWithTag("bar_row").assertTextContains("10")
    }

    @Test
    fun barListRow_clampsAFractionOutsideZeroToOne_withoutCrashing() {
        rule.setContent {
            AppTheme {
                BarListRow(label = "A", value = "1", fraction = 3f, testTag = "over")
                BarListRow(label = "B", value = "2", fraction = -1f, testTag = "under")
            }
        }

        rule.onNodeWithTag("over").assertExists()
        rule.onNodeWithTag("under").assertExists()
    }

    // DecisionButtonRow

    @Test
    fun decisionButtonRow_tapsCallTheirOwnCallback() {
        var accepted = 0
        var discarded = 0
        rule.setContent {
            AppTheme {
                DecisionButtonRow("Aceptar", "Descartar", { accepted++ }, { discarded++ }, "decision")
            }
        }

        rule.onNodeWithTag("decision_accept").performClick()
        rule.onNodeWithTag("decision_discard").performClick()
        rule.onNodeWithTag("decision_discard").performClick()

        assertEquals(1, accepted)
        assertEquals(2, discarded)
    }

    @Test
    fun decisionButtonRow_busy_disablesBothButtons() {
        rule.setContent {
            AppTheme { DecisionButtonRow("Aceptar", "Descartar", {}, {}, "decision", busy = true) }
        }

        rule.onNodeWithTag("decision_accept").assertIsNotEnabled()
        rule.onNodeWithTag("decision_discard").assertIsNotEnabled()
    }

    @Test
    fun decisionButtonRow_withRoomForBothLabels_putsDiscardLeftOfAccept() {
        rule.setContent {
            AppTheme { DecisionButtonRow("Aceptar", "Descartar", {}, {}, "decision") }
        }

        val discard = rule.onNodeWithTag("decision_discard").fetchSemanticsNode().boundsInRoot
        val accept = rule.onNodeWithTag("decision_accept").fetchSemanticsNode().boundsInRoot
        assertTrue(discard.right <= accept.left)
        assertEquals(discard.top, accept.top, 1f)
    }

    @Test
    fun decisionButtonRow_whenALabelDoesNotFit_stacksAcceptAboveDiscard() {
        rule.setContent {
            AppTheme {
                DecisionButtonRow(
                    acceptText = "Aceptar el plan con todos los cambios propuestos ".repeat(4),
                    discardText = "Descartar la propuesta completa por ahora ".repeat(4),
                    onAccept = {},
                    onDiscard = {},
                    testTagPrefix = "decision",
                )
            }
        }

        val discard = rule.onNodeWithTag("decision_discard").fetchSemanticsNode().boundsInRoot
        val accept = rule.onNodeWithTag("decision_accept").fetchSemanticsNode().boundsInRoot
        assertTrue(accept.bottom <= discard.top)
    }

    // SectionCard and RowGroupCard

    @Test
    fun sectionCard_showsItsTitleContentAndHelpSlot() {
        rule.setContent {
            AppTheme {
                SectionCard(
                    title = "Peso",
                    testTag = "section",
                    help = { Text("ayuda", Modifier.testTag("section_help")) },
                ) { Text("contenido") }
            }
        }

        rule.onNodeWithText("Peso").assertExists()
        rule.onNodeWithText("contenido").assertExists()
        rule.onNodeWithTag("section_help", useUnmergedTree = true).assertExists()
    }

    @Test
    fun rowGroupCard_rendersEveryRow() {
        rule.setContent {
            AppTheme {
                RowGroupCard(
                    rows = listOf(
                        { Text("uno", Modifier.testTag("row_1")) },
                        { Text("dos", Modifier.testTag("row_2")) },
                        { Text("tres", Modifier.testTag("row_3")) },
                    ),
                )
            }
        }

        listOf("row_1", "row_2", "row_3").forEach { rule.onNodeWithTag(it).assertExists() }
    }

    // Fill motion (R-97)

    @Composable
    private fun FillProbe(staggerIndex: Int, sink: (State<Float>) -> Unit) {
        sink(rememberFillProgress(staggerIndex))
    }

    @Test
    fun fillMotion_risesFromZeroToOne_overItsDuration() {
        lateinit var fill: State<Float>
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme { FillProbe(0) { fill = it } } }

        rule.mainClock.advanceTimeByFrame()
        assertTrue("starts below 1", fill.value < 1f)
        rule.mainClock.advanceTimeBy(mainClockFillTotalMillis())
        assertEquals(1f, fill.value, 0.001f)
    }

    @Test
    fun fillMotion_staggerDelaysTheStart() {
        lateinit var first: State<Float>
        lateinit var fifth: State<Float>
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AppTheme {
                FillProbe(0) { first = it }
                FillProbe(5) { fifth = it }
            }
        }

        rule.mainClock.advanceTimeBy((FillMotionDefaults.StaggerMillis * 2).toLong())

        assertTrue(first.value > 0f)
        assertEquals(0f, fifth.value, 0f)
    }

    @Test
    fun fillMotion_withAnimationsOff_isFullAtOnce() {
        lateinit var fill: State<Float>
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalAnimationsEnabled provides false) {
                AppTheme { FillProbe(3) { fill = it } }
            }
        }

        assertEquals(1f, fill.value, 0f)
    }

    @Test
    fun fillMotion_doesNotReplayOnRecomposition() {
        lateinit var fill: State<Float>
        var tick by mutableIntStateOf(0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AppTheme {
                Text("tick $tick")
                FillProbe(0) { fill = it }
            }
        }
        rule.mainClock.advanceTimeBy(mainClockFillTotalMillis())
        assertEquals(1f, fill.value, 0.001f)

        tick++
        rule.mainClock.advanceTimeByFrame()

        assertEquals(1f, fill.value, 0.001f)
    }
}
