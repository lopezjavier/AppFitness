package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesHubViewModelTest {
    private val training = FakeTrainingPreferencesRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun noIntentionChosen_hasNoValue() = runTest {
        val viewModel = PreferencesHubViewModel(training)
        advanceUntilIdle()

        assertNull(viewModel.intention.value)
    }

    @Test
    fun theChosenIntention_isExposed() = runTest {
        training.result = Result.success(TrainingPreferences(intention = TrainingIntention.Strength))
        val viewModel = PreferencesHubViewModel(training)

        assertNull(viewModel.intention.value, "no value while loading")
        advanceUntilIdle()

        assertEquals(TrainingIntention.Strength, viewModel.intention.value)
    }

    @Test
    fun reload_picksUpAChangeMadeInTheEditor() = runTest {
        val viewModel = PreferencesHubViewModel(training)
        advanceUntilIdle()
        training.result = Result.success(TrainingPreferences(intention = TrainingIntention.Muscle))

        viewModel.reload()
        advanceUntilIdle()

        assertEquals(TrainingIntention.Muscle, viewModel.intention.value)
    }

    @Test
    fun reload_afterClearingTheIntention_hasNoValue() = runTest {
        training.result = Result.success(TrainingPreferences(intention = TrainingIntention.Muscle))
        val viewModel = PreferencesHubViewModel(training)
        advanceUntilIdle()
        training.result = Result.success(TrainingPreferences())

        viewModel.reload()
        advanceUntilIdle()

        assertNull(viewModel.intention.value)
    }

    @Test
    fun readFailure_givesNoValue() = runTest {
        training.result = Result.failure(AppException(AppError.Network))

        val viewModel = PreferencesHubViewModel(training)
        advanceUntilIdle()

        assertNull(viewModel.intention.value)
    }
}
