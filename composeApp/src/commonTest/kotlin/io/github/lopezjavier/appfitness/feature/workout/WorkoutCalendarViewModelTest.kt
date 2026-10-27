package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutCalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeWorkoutSessionRepository()

    // The fake clock's day is 2026-10-19.
    private val today = LocalDate(2026, 10, 19)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench")))

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = WorkoutCalendarViewModel(
        repository,
        FakeTrainingPreferencesRepository(),
        MutableClock(),
        FakeWeightRepository(),
        exercises,
        savedState,
        TimeZone.UTC,
    )

    private fun WorkoutCalendarViewModel.content() = assertIs<WorkoutCalendarUiState.Content>(uiState.value)

    private fun day(daysAgo: Int) = completedSession("s$daysAgo", daysAgo).startedAt.toLocalDateTime(TimeZone.UTC).date

    @Test
    fun startsLoadingThenMarksTheTrainedDaysOfTheLatestSessionMonth() = runTest(dispatcher) {
        repository.completed += listOf(completedSession("a", 0), completedSession("b", 2))
        val model = viewModel()
        assertEquals(WorkoutCalendarUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals(WorkoutCalendarView.Month, content.view)
        assertEquals(YearMonth(2026, 10), content.month)
        assertEquals(setOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 17)), content.markedDays)
        assertEquals(false, content.canGoNext)
    }

    @Test
    fun withNoSessionsTheCalendarStillShowsWithoutMarks() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(emptySet(), model.content().markedDays)
    }

    @Test
    fun aFailedLoadIsAnErrorAndRetryRecovers() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        repository.summariesFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(WorkoutCalendarUiState.Error, model.uiState.value)

        repository.summariesFailure = null
        model.onEvent(WorkoutCalendarEvent.Retry)
        advanceUntilIdle()

        assertIs<WorkoutCalendarUiState.Content>(model.uiState.value)
    }

    @Test
    fun aWorkoutTwoYearsOlderOpensFromItsMonthViaTheEffect() = runTest(dispatcher) {
        repository.completed += listOf(completedSession("new", 0), completedSession("old", 800))
        val model = viewModel()
        advanceUntilIdle()
        val oldDay = day(800)

        model.onEvent(WorkoutCalendarEvent.SelectYear(oldDay.year))
        model.onEvent(WorkoutCalendarEvent.SelectMonth(YearMonth(oldDay.year, oldDay.month)))
        advanceUntilIdle()
        assertEquals(WorkoutCalendarView.Month, model.content().view)
        assertEquals(setOf(oldDay), model.content().markedDays)

        model.onEvent(WorkoutCalendarEvent.SelectDay(oldDay))
        advanceUntilIdle()
        model.effects.test {
            model.onEvent(WorkoutCalendarEvent.OpenSession("old"))
            assertEquals(WorkoutCalendarEffect.OpenSession("old"), awaitItem())
        }
    }

    @Test
    fun navigatingBeforeTheEarliestSessionShowsAnEmptyGridWithoutClamping() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()

        repeat(14) { model.onEvent(WorkoutCalendarEvent.PreviousMonth) }

        assertEquals(YearMonth(2025, 8), model.content().month)
        assertTrue(model.content().markedDays.isEmpty())
        assertTrue(model.content().canGoNext)
    }

    @Test
    fun nextMonthIsIgnoredAtTheCurrentMonth() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(WorkoutCalendarEvent.NextMonth)

        assertEquals(YearMonth(2026, 10), model.content().month)
    }

    private fun WorkoutCalendarViewModel.rows() = assertIs<DayCardsState.Loaded>(content().dayCards).rows

    @Test
    fun tappingADayWithOneWorkoutSelectsItAndShowsItsCardWithoutNavigating() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0, name = "Pierna")
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(WorkoutCalendarEvent.SelectDay(today))
            assertEquals(DayCardsState.Loading, model.content().dayCards)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(today, model.content().selectedDay)
        val card = model.rows().single()
        assertEquals("a", card.id)
        assertEquals("Pierna", card.name)
        assertEquals(52 * 60L, card.durationSeconds)
        assertEquals(1400.0, card.volumeKg)
    }

    @Test
    fun tappingADayWithTwoWorkoutsShowsTwoCardsOrderedByStart() = runTest(dispatcher) {
        repository.completed += listOf(
            completedSession("late", 0, hourOffset = 8),
            completedSession("early", 0),
        )
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(2, model.content().dayCounts.getValue(today))

        model.onEvent(WorkoutCalendarEvent.SelectDay(today))
        advanceUntilIdle()

        assertEquals(listOf("early", "late"), model.rows().map { it.id })
    }

    @Test
    fun openSessionFromACardEmitsTheEffect_andAnIdOutsideTheDayIsIgnored() = runTest(dispatcher) {
        repository.completed += listOf(completedSession("a", 0), completedSession("other", 2))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(WorkoutCalendarEvent.SelectDay(today))
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(WorkoutCalendarEvent.OpenSession("other"))
            expectNoEvents()
            model.onEvent(WorkoutCalendarEvent.OpenSession("a"))
            assertEquals(WorkoutCalendarEffect.OpenSession("a"), awaitItem())
        }
    }

    @Test
    fun tappingAFutureDayDoesNothing() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(WorkoutCalendarEvent.SelectDay(LocalDate(2026, 10, 25)))
        advanceUntilIdle()

        assertEquals(null, model.content().selectedDay)
        assertEquals(DayCardsState.None, model.content().dayCards)
        assertEquals(false, model.content().canAddToSelectedDay)
    }

    @Test
    fun aPastDayWithoutWorkoutsCanBeSelectedAndAddedTo() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(WorkoutCalendarEvent.SelectDay(LocalDate(2026, 10, 5)))
        advanceUntilIdle()

        assertEquals(LocalDate(2026, 10, 5), model.content().selectedDay)
        assertEquals(DayCardsState.Loaded(emptyList()), model.content().dayCards)
        assertTrue(model.content().canAddToSelectedDay)
    }

    @Test
    fun todayCanBeAddedTo() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(WorkoutCalendarEvent.SelectDay(today))

        assertTrue(model.content().canAddToSelectedDay)
    }

    @Test
    fun addWorkoutEmitsTheSelectedDay() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(WorkoutCalendarEvent.SelectDay(LocalDate(2026, 10, 5)))
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(WorkoutCalendarEvent.AddWorkout)

            assertEquals(WorkoutCalendarEffect.AddWorkout(LocalDate(2026, 10, 5)), awaitItem())
        }
    }

    @Test
    fun addWorkoutWithoutASelectedDayDoesNothing() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(WorkoutCalendarEvent.AddWorkout)
            expectNoEvents()
        }
    }

    @Test
    fun tappingTheSameDayTwiceKeepsItSelectedWithItsCards() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(WorkoutCalendarEvent.SelectDay(today))
        advanceUntilIdle()
        model.onEvent(WorkoutCalendarEvent.SelectDay(today))
        advanceUntilIdle()

        assertEquals(today, model.content().selectedDay)
        assertEquals(listOf("a"), model.rows().map { it.id })
    }

    @Test
    fun aCardLoadFailureShowsTheErrorAndRetryRecovers() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()
        repository.getSessionFailure = AppError.Network

        model.onEvent(WorkoutCalendarEvent.SelectDay(today))
        advanceUntilIdle()
        assertEquals(DayCardsState.Error, model.content().dayCards)

        repository.getSessionFailure = null
        model.onEvent(WorkoutCalendarEvent.RetryDayCards)
        advanceUntilIdle()

        assertEquals(listOf("a"), model.rows().map { it.id })
    }

    @Test
    fun refreshDropsADeletedWorkoutsCard_andKeepsTheSelectedDayWhenItEmpties() = runTest(dispatcher) {
        repository.completed += listOf(completedSession("a", 0), completedSession("b", 0, hourOffset = 3))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(WorkoutCalendarEvent.SelectDay(today))
        advanceUntilIdle()

        repository.completed.removeAll { it.id == "b" }
        model.onEvent(WorkoutCalendarEvent.Refresh)
        advanceUntilIdle()
        assertEquals(listOf("a"), model.rows().map { it.id })

        repository.completed.removeAll { it.id == "a" }
        repository.completed += completedSession("c", 2)
        model.onEvent(WorkoutCalendarEvent.Refresh)
        advanceUntilIdle()
        assertEquals(today, model.content().selectedDay)
        assertEquals(DayCardsState.Loaded(emptyList()), model.content().dayCards)
    }

    @Test
    fun yearAndMultiYearCountsFeedTheViews() = runTest(dispatcher) {
        repository.completed += listOf(completedSession("a", 0), completedSession("b", 1), completedSession("old", 800))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(WorkoutCalendarEvent.SelectView(WorkoutCalendarView.Year))

        val content = model.content()
        assertEquals(WorkoutCalendarView.Year, content.view)
        assertEquals(2026, content.year)
        assertEquals(mapOf(10 to 2), content.monthCounts)
        assertEquals(mapOf(day(800).year to 1, 2026 to 2), content.yearCounts)
    }

    @Test
    fun selectingAYearShowsItsMonths() = runTest(dispatcher) {
        repository.completed += listOf(completedSession("a", 0), completedSession("old", 800))
        val model = viewModel()
        advanceUntilIdle()
        val oldYear = day(800).year

        model.onEvent(WorkoutCalendarEvent.SelectView(WorkoutCalendarView.MultiYear))
        model.onEvent(WorkoutCalendarEvent.SelectYear(oldYear))

        assertEquals(WorkoutCalendarView.Year, model.content().view)
        assertEquals(oldYear, model.content().year)
        assertEquals(mapOf(day(800).month.ordinal + 1 to 1), model.content().monthCounts)
    }

    @Test
    fun viewMonthYearAndSelectedDayWithItsCardsSurviveARecreatedViewModel() = runTest(dispatcher) {
        repository.completed += listOf(
            completedSession("late", 0, hourOffset = 8),
            completedSession("early", 0),
            completedSession("old", 800),
        )
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onEvent(WorkoutCalendarEvent.PreviousMonth)
        first.onEvent(WorkoutCalendarEvent.NextMonth)
        first.onEvent(WorkoutCalendarEvent.SelectDay(today))
        first.onEvent(WorkoutCalendarEvent.SelectView(WorkoutCalendarView.Year))

        val second = viewModel(handle)
        advanceUntilIdle()

        val content = second.content()
        assertEquals(WorkoutCalendarView.Year, content.view)
        assertEquals(YearMonth(2026, 10), content.month)
        assertEquals(2026, content.year)
        assertEquals(today, content.selectedDay)
        assertEquals(listOf("early", "late"), second.rows().map { it.id })
    }

    @Test
    fun aRecreatedMonthViewKeepsAnOlderMonth() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        advanceUntilIdle()
        first.onEvent(WorkoutCalendarEvent.PreviousMonth)

        val second = viewModel(handle)
        advanceUntilIdle()

        assertEquals(YearMonth(2026, 9), second.content().month)
    }

    @Test
    fun malformedOrFutureSavedValuesFallBackToDefaults() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val handle = SavedStateHandle(
            mapOf(
                "calendar_view" to "Decade",
                "calendar_month" to "banana",
                "calendar_year" to "x",
                "calendar_selected_day" to "2026-13-45",
            ),
        )

        val model = viewModel(handle)
        advanceUntilIdle()

        val content = model.content()
        assertEquals(WorkoutCalendarView.Month, content.view)
        assertEquals(YearMonth(2026, 10), content.month)
        assertEquals(2026, content.year)
        assertEquals(null, content.selectedDay)
        assertEquals(DayCardsState.None, content.dayCards)
    }

    @Test
    fun aSavedMonthInTheFutureFallsBackToTheDefaultMonth() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val handle = SavedStateHandle(mapOf("calendar_month" to "2027-03"))

        val model = viewModel(handle)
        advanceUntilIdle()

        assertEquals(YearMonth(2026, 10), model.content().month)
    }

    @Test
    fun refreshShowsAWorkoutSavedMeanwhileWithoutAFlashOfLoading() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()

        repository.completed += completedSession("b", 2)
        model.onEvent(WorkoutCalendarEvent.Refresh)
        assertIs<WorkoutCalendarUiState.Content>(model.uiState.value)
        advanceUntilIdle()

        assertEquals(setOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 17)), model.content().markedDays)
    }

    @Test
    fun refreshRemovesADeletedWorkoutFromAnOlderMonth() = runTest(dispatcher) {
        repository.completed += listOf(completedSession("a", 0), completedSession("old", 60))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(WorkoutCalendarEvent.PreviousMonth)
        model.onEvent(WorkoutCalendarEvent.PreviousMonth)
        assertEquals(setOf(day(60)), model.content().markedDays)

        repository.completed.removeAll { it.id == "old" }
        model.onEvent(WorkoutCalendarEvent.Refresh)
        advanceUntilIdle()

        assertEquals(emptySet(), model.content().markedDays)
    }

    @Test
    fun refreshWhenTheLastWorkoutWasDeletedKeepsTheCalendarWithoutMarks() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()

        repository.completed.clear()
        model.onEvent(WorkoutCalendarEvent.Refresh)
        advanceUntilIdle()

        assertEquals(emptySet(), model.content().markedDays)
    }

    @Test
    fun aFailedRefreshKeepsWhatIsShown() = runTest(dispatcher) {
        repository.completed += completedSession("a", 0)
        val model = viewModel()
        advanceUntilIdle()

        repository.summariesFailure = AppError.Network
        model.onEvent(WorkoutCalendarEvent.Refresh)
        advanceUntilIdle()

        assertEquals(setOf(LocalDate(2026, 10, 19)), model.content().markedDays)
    }
}
