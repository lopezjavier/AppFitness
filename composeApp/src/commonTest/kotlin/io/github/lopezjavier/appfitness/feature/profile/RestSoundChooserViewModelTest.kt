package io.github.lopezjavier.appfitness.feature.profile

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.model.RestSound
import io.github.lopezjavier.appfitness.feature.workout.FakeAlertSound
import io.github.lopezjavier.appfitness.feature.workout.FakeRestAlertPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RestSoundChooserViewModelTest {
    private val preferences = FakeRestAlertPreferences()
    private val alertSound = FakeAlertSound()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun listsEveryCandidate_withTheSystemSoundSelectedByDefault() = runTest {
        val vm = RestSoundChooserViewModel(preferences, alertSound)

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(RestSound.entries.toList(), state.sounds)
            assertEquals(RestSound.SYSTEM, state.selected)
        }
    }

    @Test
    fun selecting_previewsAndSavesTheSound() = runTest {
        val vm = RestSoundChooserViewModel(preferences, alertSound)

        vm.uiState.test {
            awaitItem()
            vm.select(RestSound.CHIME)

            assertEquals(RestSound.CHIME, expectMostRecentItem().selected)
        }
        assertEquals(listOf(RestSound.CHIME), alertSound.previews)
        assertEquals(RestSound.CHIME, preferences.current)
    }

    @Test
    fun aSavedChoice_isShownByANewViewModel() = runTest {
        preferences.setSound(RestSound.DOUBLE_BEEP)

        RestSoundChooserViewModel(preferences, alertSound).uiState.test {
            assertEquals(RestSound.DOUBLE_BEEP, awaitItem().selected)
        }
    }
}
