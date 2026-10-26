package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.food.photo.FakePhotoCapture
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoEvent
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoFlow
import io.github.lopezjavier.appfitness.feature.food.photo.foodPhotoModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the photo sheets survive recreation; a photo never does. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class FoodPhotoRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val saved = SavedStateHandle()
    private val capture = FakePhotoCapture()

    private fun show(tester: StateRestorationTester) {
        tester.setContent {
            AppTheme {
                // A new view model per (re)composition over the same saved state: what a new process sees.
                val model = remember {
                    foodPhotoModel(capture = capture, connectivity = FakeConnectivityObserver(), state = saved)
                }
                val state by model.uiState.collectAsState()
                FoodPhotoFlow(state, model::onEvent, capture::readJpeg)
                LaunchedEffect(Unit) { model.onEvent(FoodPhotoEvent.Open) }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun modeSheet_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        show(tester)
        rule.onNodeWithTag("photo_mode").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("photo_mode").assertExists()
    }

    @Test
    fun previewAfterRecreation_comesBackAsTheSourceSheet_neverAPreviewWithoutAPhoto() {
        val tester = StateRestorationTester(rule)
        show(tester)
        rule.onNodeWithTag("photo_mode_row_1").performClick()
        rule.onNodeWithTag("photo_source_row_1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("food_photo_preview").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("food_photo_preview").assertDoesNotExist()
        rule.onNodeWithTag("photo_source").assertExists()
    }
}
