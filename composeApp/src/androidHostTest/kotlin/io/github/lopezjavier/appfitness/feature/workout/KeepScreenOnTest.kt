package io.github.lopezjavier.appfitness.feature.workout

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.SessionSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-11 "Screen always on": the flag is set only while the live workout is shown and released afterwards. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class KeepScreenOnTest {
    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf<LiveSessionUiState>(LiveSessionUiState.Loading)
    private var show by mutableStateOf(true)
    private lateinit var view: View

    private fun showScreen() {
        rule.setContent {
            AppTheme {
                view = LocalView.current
                if (show) LiveSessionScreen(state, 0, {}, {})
            }
        }
        rule.waitForIdle()
    }

    private val active = LiveSessionUiState.Active("Torso", SESSION_START, emptyList())

    @Test
    fun whileTheSessionIsActive_theScreenStaysOn() {
        state = active
        showScreen()

        assertTrue(view.keepScreenOn)
    }

    @Test
    fun whileLoading_theScreenIsNotKeptOn() {
        showScreen()

        assertFalse(view.keepScreenOn)
    }

    @Test
    fun leavingTheScreen_releasesTheFlag() {
        state = active
        showScreen()

        show = false
        rule.waitForIdle()

        assertFalse(view.keepScreenOn)
    }

    @Test
    fun onTheSummary_theFlagIsReleased() {
        state = active
        showScreen()

        state = LiveSessionUiState.Finished(LiveSummary(SessionSummary(60, 0, emptyList()), emptyMap()))
        rule.waitForIdle()

        assertFalse(view.keepScreenOn)
    }
}
