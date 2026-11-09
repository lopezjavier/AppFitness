package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-118: my foods come first in the search and starring an Open Food Facts food makes my copy the favourite. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class FoodSearchOffFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val foods = FakeFoodRepository()
    private val own = food("own1", "Galletas caseras", isManual = true)
    private val off = food("off1", "Galletas Marca", isOff = true)

    @Composable
    private fun Host(model: FoodSearchViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { FoodSearchScreen(state, model::onEvent, {}) }
    }

    private fun startWithQuery() {
        foods.manualResults["galleta"] = Result.success(listOf(own))
        foods.searchResults["galleta"] = Result.success(listOf(off))
        foods.known[off.id] = off
        val model = foodSearchModel(
            foods,
            FakeConnectivityObserver(),
            FakeBarcodeScanner(),
            SavedStateHandle(mapOf("food_search_query" to "galleta")),
        )
        rule.setContent { Host(model) }
        rule.waitForIdle()
    }

    @Test
    fun myFood_isListedAboveTheOpenFoodFactsRow() {
        startWithQuery()

        val ownTop = rule.onNodeWithTag("food_search_row_own1").getUnclippedBoundsInRoot().top
        val offTop = rule.onNodeWithTag("food_search_row_off1").getUnclippedBoundsInRoot().top
        assertTrue("own row above OFF row: $ownTop < $offTop", ownTop < offTop)
    }

    @Test
    fun starOnAnOffRow_favouritesMyCopy_andTheRowShowsItFilled() {
        startWithQuery()

        rule.onNodeWithTag("food_search_favourite_off1").performClick()
        rule.waitForIdle()

        assertEquals(listOf("off1"), foods.copyCalls)
        assertEquals(listOf("copy-off1" to true), foods.favouriteCalls)
        rule.onNodeWithTag("food_search_favourite_copy-off1").assertIsOn()
    }

    @Test
    fun starOnMyFood_doesNotCopyAnything() {
        startWithQuery()

        rule.onNodeWithTag("food_search_favourite_own1").performClick()
        rule.waitForIdle()

        assertEquals(emptyList<String>(), foods.copyCalls)
        assertEquals(listOf("own1" to true), foods.favouriteCalls)
    }
}
