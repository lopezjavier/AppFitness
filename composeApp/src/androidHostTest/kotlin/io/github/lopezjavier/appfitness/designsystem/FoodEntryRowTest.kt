package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-56 / R-16: the shared logged-food row. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class FoodEntryRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0
    private var deletes = 0
    private val haptics = RecordingAppHaptics()
    private val model = FoodEntryRowModel("e1", "Avena", "1 vaso (200 ml)", 1240, 80, 150, 40)

    private fun show(onClick: (() -> Unit)? = { clicks++ }, onDelete: (() -> Unit)? = { deletes++ }) {
        rule.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics) {
                AppTheme { FoodEntryRow(model, onClick = onClick, onDelete = onDelete, tagPrefix = "row") }
            }
        }
    }

    private fun drag(fraction: Float) {
        rule.onNodeWithTag("row_e1").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * (1f - fraction), centerY))
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun showsNameDetailKcalAndWholeGramMacros() {
        show()
        rule.onNodeWithText("Avena").assertIsDisplayed()
        rule.onNodeWithTag("row_detail_e1", useUnmergedTree = true).assertTextEquals("1 vaso (200 ml)")
        rule.onNodeWithText("1.240 kcal").assertIsDisplayed()
        rule.onNodeWithText("P 80 g").assertIsDisplayed()
        rule.onNodeWithText("C 150 g").assertIsDisplayed()
        rule.onNodeWithText("G 40 g").assertIsDisplayed()
    }

    @Test
    fun click_callsOnClick() {
        show()
        rule.onNodeWithText("Avena").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun swipePastHalf_callsOnDeleteOnce() {
        show()
        drag(0.7f)
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, deletes)
    }

    @Test
    fun swipeBelowHalf_springsBackWithoutDeleting() {
        show()
        drag(0.3f)
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(0, deletes)
    }

    @Test
    fun withoutOnDelete_thereIsNoSwipeLayer() {
        show(onDelete = null)
        drag(0.7f)
        assertEquals(0, deletes)
        rule.onNodeWithTag("row_delete_e1").assertDoesNotExist()
    }

    @Test
    fun talkBackAction_deleteNamesTheFood() {
        show()
        val actions = rule.onNodeWithTag("row_e1").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        assertNotNull(actions)
        assertTrue(actions.any { it.label == "Eliminar Avena" })
    }

    @Test
    fun withoutOnDelete_hasNoDeleteAction() {
        show(onDelete = null)
        val actions = rule.onNodeWithTag("row_e1").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        assertNull(actions)
    }
}
