package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.TrainedSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.repository.warmUpIncludedOrDefault
import io.github.lopezjavier.appfitness.domain.usecase.ComputeSessionStats
import io.github.lopezjavier.appfitness.domain.usecase.TrainingCalendar
import io.github.lopezjavier.appfitness.domain.usecase.perHandIdsOrDefault
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

private const val KEY_VIEW = "calendar_view"
private const val KEY_MONTH = "calendar_month"
private const val KEY_YEAR = "calendar_year"
private const val KEY_DAY = "calendar_selected_day"

/**
 * Training calendar (US-37): the days with a completed workout in month, year and multi-year views. All completed
 * sessions are loaded once, so any month navigated to is computed from the same map and an old workout opens by id
 * like any other. No streaks or rest days (ADR-52). A day tap only selects it and loads one summary card per workout
 * (name, duration, volume); the detail opens from a card tap ([WorkoutCalendarEvent.OpenSession]). View, month, year
 * and selected day live in [savedState]; a malformed, future or workout-less value falls back to the default.
 */
@Suppress("TooManyFunctions") // One small function per event.
class WorkoutCalendarViewModel(
    private val repository: WorkoutSessionRepository,
    private val preferences: TrainingPreferencesRepository,
    private val clock: Clock,
    private val bodyMeasurements: BodyMeasurementRepository,
    private val exercises: ExerciseRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private var days: Map<LocalDate, List<TrainedSession>> = emptyMap()
    private var terminal: WorkoutCalendarUiState? = WorkoutCalendarUiState.Loading

    private var view: WorkoutCalendarView = restoreView()
    private var month: YearMonth? = restoreMonth()
    private var year: Int? = restoreInt(KEY_YEAR)
    private var selectedDay: LocalDate? = restoreDay()
    private var dayCards: DayCardsState = DayCardsState.None
    private var cardsJob: Job? = null

    private val _uiState = MutableStateFlow<WorkoutCalendarUiState>(WorkoutCalendarUiState.Loading)
    val uiState: StateFlow<WorkoutCalendarUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<WorkoutCalendarEffect>(Channel.BUFFERED)
    val effects: Flow<WorkoutCalendarEffect> = effectChannel.receiveAsFlow()

    init {
        load()
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: WorkoutCalendarEvent) {
        when (event) {
            WorkoutCalendarEvent.Retry -> if (terminal == WorkoutCalendarUiState.Error) load()

            WorkoutCalendarEvent.Refresh -> if (terminal != WorkoutCalendarUiState.Loading) refresh()

            is WorkoutCalendarEvent.SelectView -> {
                view = event.view
                persist()
            }

            WorkoutCalendarEvent.PreviousMonth -> shiftMonth(-1)

            WorkoutCalendarEvent.NextMonth -> if (currentMonth() < clock.today().yearMonth) shiftMonth(1)

            is WorkoutCalendarEvent.SelectMonth -> {
                month = event.month
                year = event.month.year
                view = WorkoutCalendarView.Month
                persist()
            }

            is WorkoutCalendarEvent.SelectYear -> {
                year = event.year
                view = WorkoutCalendarView.Year
                persist()
            }

            is WorkoutCalendarEvent.SelectDay -> selectDay(event.date)

            is WorkoutCalendarEvent.OpenSession ->
                if (selectedDayIds().contains(event.sessionId)) {
                    effectChannel.trySend(WorkoutCalendarEffect.OpenSession(event.sessionId))
                }

            WorkoutCalendarEvent.RetryDayCards -> if (dayCards == DayCardsState.Error) loadCards(showLoading = true)
        }
    }

    private fun load() {
        terminal = WorkoutCalendarUiState.Loading
        publish()
        viewModelScope.launch {
            repository.listCompletedSummaries().fold(
                onSuccess = {
                    applySessions(it)
                    persist()
                    loadCards(showLoading = true)
                },
                onFailure = {
                    terminal = WorkoutCalendarUiState.Error
                    publish()
                },
            )
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            repository.listCompletedSummaries().onSuccess {
                applySessions(it)
                persist()
                loadCards(showLoading = false)
            }
        }
    }

    private fun applySessions(sessions: List<TrainedSession>) {
        days = TrainingCalendar.daysOf(sessions, zone)
        terminal = if (days.isEmpty()) WorkoutCalendarUiState.Empty else null
        val today = clock.today().yearMonth
        month = month?.takeIf { it <= today } ?: defaultMonth()
        year = year ?: month?.year
        // A restored or refreshed selection survives only on a past day that still has workouts.
        if (selectedDay?.let { it > clock.today() || days[it].isNullOrEmpty() } == true) selectedDay = null
    }

    private fun selectedDayIds(): List<String> = selectedDay?.let { days[it] }.orEmpty().map { it.id }

    /** Loads the cards of [selectedDay]. A failed background refresh keeps the cards that are shown. */
    private fun loadCards(showLoading: Boolean) {
        cardsJob?.cancel()
        val day = selectedDay
        if (day == null) {
            dayCards = DayCardsState.None
            publish()
            return
        }
        val sessions = days[day].orEmpty().sortedBy { it.startedAt }
        if (showLoading || dayCards !is DayCardsState.Loaded) {
            dayCards = DayCardsState.Loading
            publish()
        }
        cardsJob = viewModelScope.launch {
            val includeWarmUp = preferences.warmUpIncludedOrDefault()
            val bodyWeightKg = bodyMeasurements.getLatestWeight().getOrNull()?.weightKg
            val perHandIds = exercises.perHandIdsOrDefault()
            val rows = mutableListOf<HistoryRow>()
            for (summary in sessions) {
                val session = repository.getSession(summary.id).getOrNull()
                if (session == null) {
                    if (dayCards !is DayCardsState.Loaded || showLoading) dayCards = DayCardsState.Error
                    publish()
                    return@launch
                }
                rows += session.toRow(includeWarmUp, bodyWeightKg, perHandIds)
            }
            dayCards = DayCardsState.Loaded(rows)
            publish()
        }
    }

    private fun WorkoutSession.toRow(
        includeWarmUp: Boolean,
        bodyWeightKg: Double?,
        perHandIds: Set<String>,
    ): HistoryRow {
        val stats = ComputeSessionStats(this, includeWarmUp, bodyWeightKg, perHandIds)
        return HistoryRow(
            id = id,
            label = sessionDateLabel(sessionLocalDate(startedAt, zone), clock.today()),
            name = name,
            durationSeconds = stats.durationSeconds,
            volumeKg = stats.volumeKg,
            completedSets = stats.completedSets,
        )
    }

    private fun defaultMonth(): YearMonth = days.keys.maxOrNull()?.yearMonth ?: clock.today().yearMonth

    private fun currentMonth(): YearMonth = month ?: defaultMonth()

    private fun shiftMonth(by: Int) {
        val target = if (by <
            0
        ) {
            currentMonth().minus(1, DateTimeUnit.MONTH)
        } else {
            currentMonth().plus(1, DateTimeUnit.MONTH)
        }
        month = target
        year = target.year
        persist()
    }

    private fun selectDay(date: LocalDate) {
        val sessions = days[date].orEmpty()
        if (sessions.isEmpty() || date > clock.today()) return
        val alreadyLoaded = selectedDay == date && dayCards is DayCardsState.Loaded
        selectedDay = date
        persist()
        if (!alreadyLoaded) loadCards(showLoading = true)
    }

    private fun persist() {
        savedState[KEY_VIEW] = view.name
        savedState[KEY_MONTH] = month?.toString()
        savedState[KEY_YEAR] = year
        savedState[KEY_DAY] = selectedDay?.toString()
        publish()
    }

    private fun publish() {
        _uiState.value = terminal ?: render()
    }

    private fun render(): WorkoutCalendarUiState {
        val shownMonth = currentMonth()
        val shownYear = year ?: shownMonth.year
        return WorkoutCalendarUiState.Content(
            view = view,
            month = shownMonth,
            year = shownYear,
            markedDays = days.keys.filter { it.yearMonth == shownMonth }.toSet(),
            dayCounts = days.filterKeys { it.yearMonth == shownMonth }.mapValues { it.value.size },
            selectedDay = selectedDay,
            yearCounts = TrainingCalendar.yearCounts(days),
            monthCounts = TrainingCalendar.monthCounts(days, shownYear),
            canGoNext = shownMonth < clock.today().yearMonth,
            today = clock.today(),
            dayCards = if (selectedDay == null) DayCardsState.None else dayCards,
        )
    }

    private fun restoreView(): WorkoutCalendarView = (savedState.get<Any>(KEY_VIEW) as? String)
        ?.let { name -> WorkoutCalendarView.entries.firstOrNull { it.name == name } }
        ?: WorkoutCalendarView.Month

    private fun restoreMonth(): YearMonth? =
        (savedState.get<Any>(KEY_MONTH) as? String)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

    private fun restoreDay(): LocalDate? =
        (savedState.get<Any>(KEY_DAY) as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun restoreInt(key: String): Int? = savedState.get<Any>(key) as? Int
}
