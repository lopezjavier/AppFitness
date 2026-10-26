package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.swipeLeft
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The catalog swipe demo keeps its list through recreation (ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class CatalogSwipeRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aDeletedRow_staysGoneAfterRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { SwipeSection() } }

        rule.onNodeWithTag("catalog_swipe_row_1").performTouchInput { swipeLeft(startX = right - 1f, endX = 0f) }
        rule.waitForIdle()
        rule.onNodeWithTag("catalog_swipe_row_1").assertDoesNotExist()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag("catalog_swipe_row_1").assertDoesNotExist()
        rule.onNodeWithTag("catalog_swipe_row_3").assertIsDisplayed()
    }
}
