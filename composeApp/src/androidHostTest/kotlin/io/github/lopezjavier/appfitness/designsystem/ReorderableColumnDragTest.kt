package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
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
class ReorderableColumnDragTest {
    @get:Rule
    val rule = createComposeRule()

    private val moves = mutableListOf<Pair<Int, Int>>()
    private var items by mutableStateOf(listOf("A", "B", "C"))

    private fun show() = rule.setContent {
        AppTheme {
            ReorderableColumn(
                items = items,
                key = { it },
                onMove = { from, to ->
                    moves += from to to
                    items = moveExercise(items, from, to)
                },
            ) { item, _, _ ->
                Row(Modifier.fillMaxWidth().height(64.dp).testTag("row_$item")) {
                    DragHandle("Reordenar $item", Modifier.dragHandle().testTag("handle_$item"))
                    Text(item)
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
    fun longPressAndDragOnTheHandle_movesTheRowDown_andReportsOneMove() {
        show()

        rule.onNodeWithTag("handle_A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + EXTRA_MILLIS)
            repeat(STEPS) { moveBy(Offset(0f, STEP_PX)) }
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(0 to 1), moves)
        assertEquals(listOf("B", "A", "C"), items)
        assertTrue(top("A") > top("B"))
    }

    @Test
    fun aShortDragWithoutLongPress_doesNotReorder() {
        show()

        rule.onNodeWithTag("handle_A").performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, STEP_PX)) }
            up()
        }
        rule.waitForIdle()

        assertTrue(moves.isEmpty())
    }
}

private const val EXTRA_MILLIS = 100L
private const val STEPS = 6
private const val STEP_PX = 50f
