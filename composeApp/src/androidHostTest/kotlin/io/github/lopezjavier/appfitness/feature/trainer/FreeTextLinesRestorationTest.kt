package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the lines screen keeps nothing locally; the excluded state always comes back from the repository. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class FreeTextLinesRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachIntakeRepository().apply {
        lines += FreeTextLine(
            FreeTextKind.Discomfort,
            "d1",
            LocalDate(2026, 10, 4),
            "Me duele la rodilla",
            FreeTextStatus.Sent,
        )
    }
    private val connectivity = FakeConnectivityObserver()

    @Test
    fun recreatedScreen_showsTheExcludedLineOff() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val model = remember { FreeTextLinesViewModel(repository, MutableClock()) }
                FreeTextLinesRoot(onBack = {}, viewModel = model, connectivity = connectivity)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("free_text_switch_discomfort_d1").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("free_text_switch_discomfort_d1").assertIsOff()
    }

    @Test
    fun recreatedViewModel_listsTheSameLines_withTheExcludedStateFromTheRepository() {
        var current by mutableStateOf(FreeTextLinesViewModel(repository, MutableClock()))
        rule.setContent {
            AppTheme { FreeTextLinesRoot(onBack = {}, viewModel = current, connectivity = connectivity) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("free_text_switch_discomfort_d1").performClick()
        rule.waitForIdle()

        current = FreeTextLinesViewModel(repository, MutableClock())
        rule.waitForIdle()

        rule.onNodeWithTag("free_text_line_discomfort_d1").assertExists()
        rule.onNodeWithTag("free_text_switch_discomfort_d1").assertIsOff()
    }
}
