package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppAnimateItemTest {
    @get:Rule
    val rule = createComposeRule()

    private val rows = mutableStateListOf("a", "b", "c")

    private fun show(animationsEnabled: Boolean) {
        rule.setContent {
            CompositionLocalProvider(LocalAnimationsEnabled provides animationsEnabled) {
                LazyColumn {
                    items(rows, key = { it }) { row ->
                        Text(row, appAnimateItem(Modifier.height(40.dp).testTag("row_$row")))
                    }
                }
            }
        }
    }

    @Test
    fun removingARow_withReducedMotion_dropsItAtOnce() {
        show(animationsEnabled = false)
        rule.mainClock.autoAdvance = false

        rows.remove("b")
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        rule.onAllNodesWithTag("row_b").assertCountEquals(0)
    }

    @Test
    fun removingARow_withMotion_keepsTheOthersAndFinishesRemoved() {
        show(animationsEnabled = true)

        rows.remove("b")
        rule.waitForIdle()

        rule.onAllNodesWithTag("row_b").assertCountEquals(0)
        rule.onNodeWithTag("row_a").assertExists()
        rule.onNodeWithTag("row_c").assertExists()
    }

    @Test
    fun reordering_keepsEveryRow() {
        show(animationsEnabled = true)

        rows.reverse()
        rule.waitForIdle()

        assertEquals(listOf("c", "b", "a"), rows.toList())
        listOf("a", "b", "c").forEach { rule.onNodeWithTag("row_$it").assertExists() }
    }
}
