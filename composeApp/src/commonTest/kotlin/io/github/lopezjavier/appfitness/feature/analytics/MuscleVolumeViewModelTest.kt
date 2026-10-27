package io.github.lopezjavier.appfitness.feature.analytics

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleVolumeRow
import io.github.lopezjavier.appfitness.domain.usecase.BuildMuscleVolumeHistory
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MuscleVolumeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val strength = FakeStrengthRepository()

    // MutableClock is on Monday 2026-10-19.
    private val monday = LocalDate(2026, 10, 19)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        MuscleVolumeViewModel(strength, MutableClock(), savedState)

    private fun MuscleVolumeViewModel.content() = assertIs<MuscleVolumeUiState.Content>(uiState.value)

    private fun row(group: MuscleGroup, volume: Double) = WeeklyMuscleVolumeRow(monday, group, volume)

    @Test
    fun startsLoadingThenShowsEveryGroupWithVolumeFromTheFirstSession() = runTest(dispatcher) {
        strength.volumeRows = listOf(
            WeeklyMuscleVolumeRow(LocalDate(2026, 10, 5), MuscleGroup.Chest, 5400.0),
            row(MuscleGroup.Back, 3000.0),
        )
        val model = viewModel()
        assertEquals(MuscleVolumeUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals(3, content.history.weeks.size)
        assertEquals(listOf(MuscleGroup.Chest, MuscleGroup.Back), content.history.series.keys.toList())
        assertEquals(listOf(5400.0, 0.0, 0.0), content.history.series.getValue(MuscleGroup.Chest))
        assertEquals(null, content.selected)
        assertEquals(listOf(BuildMuscleVolumeHistory.FIRST_QUERY_DATE), strength.volumeFromArgs)
    }

    @Test
    fun firstSessionLongAgo_isCappedAtEightWeeks() = runTest(dispatcher) {
        strength.volumeRows = listOf(
            WeeklyMuscleVolumeRow(LocalDate(2026, 6, 1), MuscleGroup.Chest, 100.0),
            row(MuscleGroup.Chest, 50.0),
        )
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(8, model.content().history.weeks.size)
    }

    @Test
    fun withoutVolumeInTheWindow_isEmpty() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(MuscleVolumeUiState.Empty, model.uiState.value)
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() = runTest(dispatcher) {
        strength.volumeFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(MuscleVolumeUiState.Error, model.uiState.value)

        strength.volumeFailure = null
        strength.volumeRows = listOf(row(MuscleGroup.Chest, 100.0))
        model.onEvent(MuscleVolumeEvent.Retry)
        assertEquals(MuscleVolumeUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        model.content()
    }

    @Test
    fun selectGroupIsolatesItAndNullReturnsToAll() = runTest(dispatcher) {
        strength.volumeRows = listOf(row(MuscleGroup.Chest, 5400.0), row(MuscleGroup.Back, 3000.0))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back))
        assertEquals(MuscleGroup.Back, model.content().selected)

        model.onEvent(MuscleVolumeEvent.SelectGroup(null))
        assertEquals(null, model.content().selected)
    }

    @Test
    fun selectionIsKeptWhenReloadingAndTheGroupStillHasVolume() = runTest(dispatcher) {
        strength.volumeRows = listOf(row(MuscleGroup.Chest, 100.0), row(MuscleGroup.Back, 100.0))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back))

        model.onEvent(MuscleVolumeEvent.Retry)
        advanceUntilIdle()

        assertEquals(MuscleGroup.Back, model.content().selected)
    }

    @Test
    fun selectionSurvivesRecreationFromTheSameSavedState() = runTest(dispatcher) {
        strength.volumeRows = listOf(row(MuscleGroup.Chest, 100.0), row(MuscleGroup.Back, 100.0))
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back))

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(MuscleGroup.Back, second.content().selected)
    }

    @Test
    fun clearingTheSelectionIsAlsoRestored() = runTest(dispatcher) {
        strength.volumeRows = listOf(row(MuscleGroup.Chest, 100.0), row(MuscleGroup.Back, 100.0))
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back))
        first.onEvent(MuscleVolumeEvent.SelectGroup(null))

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(null, second.content().selected)
    }

    @Test
    fun selectionResetsWhenTheGroupDisappearsOnReload() = runTest(dispatcher) {
        strength.volumeRows = listOf(row(MuscleGroup.Chest, 100.0), row(MuscleGroup.Back, 100.0))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back))

        strength.volumeRows = listOf(row(MuscleGroup.Chest, 100.0))
        model.onEvent(MuscleVolumeEvent.Retry)
        advanceUntilIdle()

        assertEquals(null, model.content().selected)
    }

    @Test
    fun selectingAGroupWithoutVolumeIsIgnored() = runTest(dispatcher) {
        strength.volumeRows = listOf(row(MuscleGroup.Chest, 100.0))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back))

        assertEquals(null, model.content().selected)
    }
}
