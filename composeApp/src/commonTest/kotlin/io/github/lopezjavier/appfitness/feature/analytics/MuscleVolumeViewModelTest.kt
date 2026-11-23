package io.github.lopezjavier.appfitness.feature.analytics

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleSets
import io.github.lopezjavier.appfitness.feature.analytics.stats.FakeTrainingStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MuscleVolumeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stats = FakeTrainingStatsRepository()

    // Eight consecutive Mondays ending on 2026-10-19.
    private val firstMonday = LocalDate(2026, 8, 31)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = MuscleVolumeViewModel(stats, savedState)

    private fun MuscleVolumeViewModel.content() = assertIs<MuscleVolumeUiState.Content>(uiState.value)

    /** Eight weeks where only the current (last) one has [groups]. */
    private fun weeks(vararg groups: MuscleGroupSets) = List(8) { i ->
        WeeklyMuscleSets(firstMonday.plus(i, DateTimeUnit.WEEK), if (i == 7) groups.toList() else emptyList())
    }

    private fun sets(group: MuscleGroup, value: Double) = MuscleGroupSets(group, value)

    @Test
    fun startsLoadingThenShowsEveryGroupWithSetsOverEightWeeks() = runTest(dispatcher) {
        stats.weeklyMuscleSetsResult = weeks(sets(MuscleGroup.Back, 6.0), sets(MuscleGroup.Chest, 4.5))
        val model = viewModel()
        assertEquals(MuscleVolumeUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals(8, content.history.weeks.size)
        assertEquals(listOf(MuscleGroup.Chest, MuscleGroup.Back), content.history.series.keys.toList())
        assertEquals(4.5, content.history.series.getValue(MuscleGroup.Chest).last())
        assertEquals(null, content.selected)
        assertEquals(listOf(8), stats.weeklyMuscleSetsCalls)
    }

    @Test
    fun withoutSetsInTheWindow_isEmpty() = runTest(dispatcher) {
        stats.weeklyMuscleSetsResult = weeks()
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(MuscleVolumeUiState.Empty, model.uiState.value)
    }

    @Test
    fun failureShowsErrorAndRetryRecovers() = runTest(dispatcher) {
        stats.weeklyMuscleSetsFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(MuscleVolumeUiState.Error, model.uiState.value)

        stats.weeklyMuscleSetsFailure = null
        stats.weeklyMuscleSetsResult = weeks(sets(MuscleGroup.Chest, 3.0))
        model.onEvent(MuscleVolumeEvent.Retry)
        assertEquals(MuscleVolumeUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        model.content()
    }

    @Test
    fun selectGroupIsolatesItAndNullReturnsToAll() = runTest(dispatcher) {
        stats.weeklyMuscleSetsResult = weeks(sets(MuscleGroup.Chest, 5.0), sets(MuscleGroup.Back, 3.0))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back))
        assertEquals(MuscleGroup.Back, model.content().selected)

        model.onEvent(MuscleVolumeEvent.SelectGroup(null))
        assertEquals(null, model.content().selected)
    }

    @Test
    fun selectionIsKeptWhenReloadingAndTheGroupStillHasSets() = runTest(dispatcher) {
        stats.weeklyMuscleSetsResult = weeks(sets(MuscleGroup.Chest, 1.0), sets(MuscleGroup.Back, 1.0))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back))

        model.onEvent(MuscleVolumeEvent.Retry)
        advanceUntilIdle()

        assertEquals(MuscleGroup.Back, model.content().selected)
    }

    @Test
    fun selectionSurvivesRecreationFromTheSameSavedState() = runTest(dispatcher) {
        stats.weeklyMuscleSetsResult = weeks(sets(MuscleGroup.Chest, 1.0), sets(MuscleGroup.Back, 1.0))
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
        stats.weeklyMuscleSetsResult = weeks(sets(MuscleGroup.Chest, 1.0), sets(MuscleGroup.Back, 1.0))
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
        stats.weeklyMuscleSetsResult = weeks(sets(MuscleGroup.Chest, 1.0), sets(MuscleGroup.Back, 1.0))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back))

        stats.weeklyMuscleSetsResult = weeks(sets(MuscleGroup.Chest, 1.0))
        model.onEvent(MuscleVolumeEvent.Retry)
        advanceUntilIdle()

        assertEquals(null, model.content().selected)
    }

    @Test
    fun selectingAGroupWithoutSetsIsIgnored() = runTest(dispatcher) {
        stats.weeklyMuscleSetsResult = weeks(sets(MuscleGroup.Chest, 1.0))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(MuscleVolumeEvent.SelectGroup(MuscleGroup.Back))

        assertEquals(null, model.content().selected)
    }
}
