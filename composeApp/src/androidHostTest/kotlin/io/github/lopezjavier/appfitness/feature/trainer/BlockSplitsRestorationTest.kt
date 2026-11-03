package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: "Tipo de rutina" keeps its options and the pick across recreation and never asks (or bills) again. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BlockSplitsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val blocks = FakeCoachBlockRepository()
    private val saved = SavedStateHandle()

    private fun newModel(handle: SavedStateHandle = saved) =
        BlockSplitsViewModel("plan-1", blocks, MutableClock(), handle)

    private var current by mutableStateOf(newModel())

    private fun showRoot() {
        rule.setContent {
            AppTheme {
                BlockSplitsRoot("plan-1", {}, {}, viewModel = current, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
    }

    private fun recreate(handle: SavedStateHandle = saved) {
        current = newModel(handle)
        rule.waitForIdle()
    }

    @Test
    fun thePick_survivesRecreation_withoutAskingTheCoachAgain() {
        showRoot()
        rule.onNodeWithTag("block_splits_option_a").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("block_splits_option_a").assertIsSelected()

        recreate()

        rule.onNodeWithTag("block_splits_option_a").assertIsSelected()
        assertEquals(1, blocks.splitRequests)
    }

    @Test
    fun aBuildInFlightWhenRecreated_showsInterrupted_andDoesNotBuildAgain() {
        showRoot()
        rule.onNodeWithTag("block_splits_option_a").performClick()
        val handle = SavedStateHandle()
        saved.keys().forEach { handle[it] = saved.get<Any>(it) }
        handle["splits_building"] = true
        val requestsBefore = blocks.requests.size

        recreate(handle)

        rule.onNodeWithText("No se recibió el bloque. Inténtalo de nuevo.").assertExists()
        assertEquals(requestsBefore, blocks.requests.size)
    }
}
