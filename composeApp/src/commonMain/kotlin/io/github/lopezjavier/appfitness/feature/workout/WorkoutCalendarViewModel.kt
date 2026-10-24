package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.TrainedSession
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.TrainingCalendar
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
private const val KEY_CHOICES = "calendar_choices_open"

/**
 * Training calendar (US-37): the days with a completed workout in month, year and multi-year views. All completed
 * sessions are loaded once, so any month navigated to is computed from the same map and an old workout opens by id
 * like any other. No streaks or rest days (ADR-52). View, month, year, selected day and the open picker live in
 * [savedState]; a malformed or future value falls back to the default.
 */
@Suppress("TooManyFunctions") // One small function per event.
class WorkoutCalendarViewModel(
    private val repository: WorkoutSessionRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private var days: Map<LocalDate, List<TrainedSession>> = emptyMap()
    private var terminal: WorkoutCalendarUiState? = WorkoutCalendarUiState.Loading

    private var view: WorkoutCalendarView = restoreView()
    private var month: YearMonth? = restoreMonth()
    private var year: Int? = restoreInt(KEY_YEAR)
    private var selectedDay: LocalDate? = restoreDay()
    private var choicesOpen: Boolean = savedState.get<Any>(KEY_CHOICES) as? Boolean ?: false

    private val _uiState = MutableStateFlow<WorkoutCalendarUiState>(WorkoutCalendarUiState.Loading)
    val uiState: StateFlow<WorkoutCalendarUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<WorkoutCalendarEffect>(Channel.BUFFERED)
    val effects: Flow<WorkoutCalendarEffect> = effectChannel.receiveAsFlow()

    init {
        load()
    }

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

            WorkoutCalendarEvent.DismissChoices -> {
                choicesOpen = false
                persist()
            }

            is WorkoutCalendarEvent.PickSession -> pick(event.sessionId)
        }
    }

    private fun load() {
        terminal = WorkoutCalendarUiState.Loading
        publish()
        viewModelScope.launch {
            repository.listCompletedSummaries().fold(
                onSuccess = ::applySessions,
                onFailure = { terminal = WorkoutCalendarUiState.Error },
            )
            publish()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            repository.listCompletedSummaries().onSuccess {
                applySessions(it)
                // A day that lost its workouts closes its picker.
                if (choicesOpen && currentChoices().isEmpty()) choicesOpen = false
                persist()
            }
        }
    }

    private fun applySessions(sessions: List<TrainedSession>) {
        days = TrainingCalendar.daysOf(sessions, zone)
        terminal = if (days.isEmpty()) WorkoutCalendarUiState.Empty else null
        val today = clock.today().yearMonth
        month = month?.takeIf { it <= today } ?: defaultMonth()
        year = year ?: month?.year
        if (selectedDay?.let { it > clock.today() } == true) selectedDay = null
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
        selectedDay = date
        if (sessions.size == 1) {
            choicesOpen = false
            effectChannel.trySend(WorkoutCalendarEffect.OpenSession(sessions.single().id))
        } else {
            choicesOpen = true
        }
        persist()
    }

    private fun pick(sessionId: String) {
        if (currentChoices().none { it.id == sessionId }) return
        choicesOpen = false
        effectChannel.trySend(WorkoutCalendarEffect.OpenSession(sessionId))
        persist()
    }

    private fun currentChoices(): List<TrainedSession> =
        if (choicesOpen) selectedDay?.let { days[it] }.orEmpty().takeIf { it.size > 1 }.orEmpty() else emptyList()

    private fun persist() {
        savedState[KEY_VIEW] = view.name
        savedState[KEY_MONTH] = month?.toString()
        savedState[KEY_YEAR] = year
        savedState[KEY_DAY] = selectedDay?.toString()
        savedState[KEY_CHOICES] = choicesOpen
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
            dayChoices = currentChoices(),
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
