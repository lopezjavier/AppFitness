package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.domain.usecase.moveExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Smoke test of the drag path (the deterministic reorder logic is covered by [ReorderableColumnTest]). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One behaviour per test.
class ReorderableColumnDragTest {
    @get:Rule
    val rule = createComposeRule()

    private val moves = mutableListOf<Pair<Int, Int>>()
    private var items by mutableStateOf(listOf("A", "B", "C"))

    private val haptics = RecordingAppHaptics()

    private fun show(onAutoScroll: (suspend (Float) -> Float)? = null) = rule.setContent {
        CompositionLocalProvider(LocalAppHaptics provides haptics) {
            AppTheme {
                ReorderableColumn(
                    onAutoScroll = onAutoScroll,
                    items = items,
                    key = { it },
                    onMove = { from, to ->
                        moves += from to to
                        items = moveExercise(items, from, to)
                    },
                ) { item, _, _ ->
                    Row(Modifier.fillMaxWidth().height(64.dp).reorderActions().testTag("row_$item")) {
                        DragHandle("Reordenar $item", Modifier.dragHandle().testTag("handle_$item"))
                        Text(item)
                    }
                }
            }
        }
    }

    private fun top(item: String) = rule.onNodeWithTag("row_$item").getUnclippedBoundsInRoot().top

    @Test
    fun handle_isLabelled() {
        show()

        rule.onNodeWithTag("handle_B").assertContentDescriptionEquals("Reordenar B")
    }

    @Test
    fun dragOnTheHandleWithoutLongPress_movesTheRowDown_andReportsOneMove() {
        show()

        rule.onNodeWithTag("handle_A").performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, STEP_PX)) }
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(0 to 1), moves)
        assertEquals(listOf("B", "A", "C"), items)
        assertTrue(top("A") > top("B"))
    }

    @Test
    fun pickUpAndEachSwap_vibrateOneTickEach() {
        show()

        rule.onNodeWithTag("handle_A").performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, STEP_PX)) }
            up()
        }
        rule.waitForIdle()

        // One tick on pick-up plus one for the single swap A past B.
        assertEquals(listOf(HapticEvent.Tick, HapticEvent.Tick), haptics.calls)
    }

    @Test
    fun aTouchWithoutMovement_doesNotReorderOrVibrate() {
        show()

        rule.onNodeWithTag("handle_A").performTouchInput {
            down(center)
            up()
        }
        rule.waitForIdle()

        assertTrue(moves.isEmpty())
        assertTrue(haptics.calls.isEmpty())
    }

    @Test
    fun moveDownAction_reordersAndTicksOnce() {
        show()

        rule.runOnUiThread { action("row_A", "Bajar").action() }
        rule.waitForIdle()

        assertEquals(listOf(0 to 1), moves)
        haptics.assertSingle(HapticEvent.Tick)
    }

    @Test
    fun moveUpAction_isNotOfferedOnTheFirstItem_andBothOnTheMiddleOne() {
        show()

        assertEquals(listOf("Bajar"), actionLabels("row_A"))
        assertEquals(listOf("Subir", "Bajar"), actionLabels("row_B"))
        assertEquals(listOf("Subir"), actionLabels("row_C"))
    }

    @Test
    fun draggingNearTheTop_callsAutoScrollWithNegativeDelta() {
        val requested = mutableListOf<Float>()
        show { delta ->
            requested += delta
            0f
        }

        rule.mainClock.autoAdvance = false
        val handle = rule.onNodeWithTag("handle_A")
        handle.performTouchInput { down(center) }
        repeat(STEPS) {
            handle.performTouchInput { moveBy(Offset(0f, STEP_PX)) }
            rule.mainClock.advanceTimeByFrame()
        }
        // The finger stays down while frames run, so the per-frame auto-scroll loop is active.
        repeat(FRAMES) { rule.mainClock.advanceTimeByFrame() }

        assertTrue("requested=$requested haptics=${haptics.calls}", requested.isNotEmpty() && requested.all { it < 0f })
    }

    private fun actionLabels(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

    private fun action(tag: String, label: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsActions.CustomActions).orEmpty().first { it.label == label }
}

private const val STEPS = 6
private const val STEP_PX = 50f
private const val FRAMES = 10
