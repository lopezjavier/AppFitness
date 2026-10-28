package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.RestSound
import io.github.lopezjavier.appfitness.feature.workout.FakeAlertSound
import io.github.lopezjavier.appfitness.feature.workout.FakeRestAlertPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** TEMPORARY (T-146, removed by T-148): a tap previews and saves the candidate; the next alert uses it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
class RestSoundChooserFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val preferences = FakeRestAlertPreferences()
    private val alertSound = FakeAlertSound()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun tappingACandidate_previewsItAndSavesItAsTheCurrentSound() {
        val viewModel = RestSoundChooserViewModel(preferences, alertSound)
        rule.setContent { AppTheme { RestSoundChooserRoot(onBack = {}, viewModel = viewModel) } }

        rule.onNodeWithTag("rest_sound_chooser_row_chime").performClick()
        rule.waitForIdle()

        assertEquals(listOf(RestSound.CHIME), alertSound.previews)
        assertEquals(RestSound.CHIME, preferences.current)
    }
}
