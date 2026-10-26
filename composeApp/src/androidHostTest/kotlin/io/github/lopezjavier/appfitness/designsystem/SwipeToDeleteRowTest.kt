package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One behaviour per test.
class SwipeToDeleteRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var deletes = 0
    private val haptics = RecordingAppHaptics()
    private var present by mutableStateOf(true)

    private fun show(enabled: Boolean = true, animated: Boolean = true, label: String? = null) {
        deletes = 0
        present = true
        rule.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics, LocalAnimationsEnabled provides animated) {
                AppTheme {
                    if (present) {
                        val body: @Composable () -> Unit = {
                            Box(Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).testTag("content")) {
                                Text("Avena")
                            }
                        }
                        if (label == null) {
                            SwipeToDeleteRow("Eliminar Avena", enabled, {
                                deletes++
                            }, "bg", Modifier.testTag("row"), content = body)
                        } else {
                            SwipeToDeleteRow(
                                deleteLabel = "Eliminar Avena",
                                enabled = enabled,
                                onDelete = { deletes++ },
                                backgroundTag = "bg",
                                modifier = Modifier.testTag("row"),
                                label = label,
                                content = body,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Drags from the right edge to [fraction] of the width and returns without releasing. */
    private fun dragTo(fraction: Float, release: Boolean) {
        rule.onNodeWithTag("row").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * (1f - fraction), centerY))
            if (release) up()
        }
        rule.waitForIdle()
    }

    private fun contentLeft() = rule.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot.left

    @Test
    fun dragToFortyFivePercentAndRelease_springsBackWithoutDeletingOrVibrating() {
        show()

        dragTo(0.45f, release = true)

        assertEquals(0, deletes)
        assertEquals(0f, contentLeft(), 0.5f)
        assertTrue(haptics.calls.isEmpty())
    }

    @Test
    fun dragPastHalf_armsWithOneVibration() {
        show()

        dragTo(0.65f, release = false)

        haptics.assertSingle(HapticEvent.Armed)
    }

    @Test
    fun dragBackBelowFortyPercent_disarmsOnce() {
        show()

        rule.onNodeWithTag("row").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * 0.35f, centerY))
            moveTo(Offset(right * 0.5f, centerY))
            moveTo(Offset(right * 0.62f, centerY))
        }
        rule.waitForIdle()

        assertEquals(listOf(HapticEvent.Armed, HapticEvent.Disarmed), haptics.calls)
    }

    @Test
    fun releaseArmed_deletesOnceWithOneConfirm() {
        show()

        dragTo(0.7f, release = true)

        assertEquals(1, deletes)
        assertEquals(listOf(HapticEvent.Armed, HapticEvent.Confirm), haptics.calls)
    }

    @Test
    fun fullSwipeToTheEnd_deletesOnce() {
        show()

        rule.onNodeWithTag("row").performTouchInput { swipeLeft(startX = right - 1f, endX = 0f) }
        rule.waitForIdle()

        assertEquals(1, deletes)
    }

    @Test
    fun unarmedFling_neverDeletes() {
        show()

        rule.onNodeWithTag("row").performTouchInput {
            swipeWithVelocity(
                start = Offset(right - 1f, centerY),
                end = Offset(right * 0.7f, centerY),
                endVelocity = 5000f,
            )
        }
        rule.waitForIdle()

        assertEquals(0, deletes)
        assertTrue(haptics.calls.none { it == HapticEvent.Confirm })
    }

    @Test
    fun swipeLeftToRight_doesNothing() {
        show()

        rule.onNodeWithTag("row").performTouchInput { swipeRight() }
        rule.waitForIdle()

        assertEquals(0, deletes)
        assertTrue(haptics.calls.isEmpty())
        assertEquals(0f, contentLeft(), 0.5f)
    }

    @Test
    fun disabledRow_neverDeletesAndHasNoAction() {
        show(enabled = false)

        rule.onNodeWithTag("row").performTouchInput { swipeLeft(startX = right - 1f, endX = 0f) }
        rule.waitForIdle()

        assertEquals(0, deletes)
        val config = rule.onNodeWithTag("row").fetchSemanticsNode().config
        assertTrue(config.getOrNull(SemanticsActions.CustomActions).isNullOrEmpty())
    }

    @Test
    fun accessibilityAction_deletesWithExactlyOneVibration() {
        show()

        val actions = rule.onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        val action = actions?.firstOrNull { it.label == "Eliminar Avena" }
        assertNotNull(action)
        rule.runOnUiThread { action!!.action() }
        rule.waitForIdle()

        assertEquals(1, deletes)
        haptics.assertSingle(HapticEvent.Confirm)
    }

    @Test
    fun reducedMotion_snapsAndStillVibrates() {
        show(animated = false)

        dragTo(0.7f, release = true)

        assertEquals(1, deletes)
        assertEquals(listOf(HapticEvent.Armed, HapticEvent.Confirm), haptics.calls)
    }

    @Test
    fun removeLabelVariant_showsQuitarBehindTheRow() {
        show(label = "Quitar")

        dragTo(0.3f, release = false)

        rule.onNodeWithText("Quitar").assertIsDisplayed()
    }

    @Test
    fun defaultLabel_showsEliminarBehindTheRow() {
        show()

        dragTo(0.3f, release = false)

        rule.onNodeWithText("Eliminar").assertIsDisplayed()
    }

    @Test
    fun rowReturnedByTheCaller_isShownAgainAtRest() {
        show()
        dragTo(0.7f, release = true)
        rule.runOnUiThread { present = false }
        rule.waitForIdle()

        rule.runOnUiThread { present = true }
        rule.waitForIdle()

        assertEquals(0f, contentLeft(), 0.5f)
        rule.onNodeWithTag("content").assertIsDisplayed()
    }

    @Test
    fun rowIsAtLeastTheMinimumTouchTarget() {
        show()

        rule.onNodeWithTag("row").assertHeightIsAtLeast(48.dp)
    }
}
