package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.usecase.CalculateMacroTargets
import io.github.lopezjavier.appfitness.domain.usecase.DiaryHistorySummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

private const val KEY_MONTH = "history_month"
private const val KEY_DAY = "history_day"
private const val KEY_VIEW = "history_view"
private const val NO_DAY = ""
private const val WEEK_BACK_DAYS = 6

/** Everything fetched for one viewed month, plus the last-7-days window that may cross months. */
private class HistoryData(
    val month: YearMonth,
    val monthDays: List<DiaryDaySummary>,
    val weekDays: List<DiaryDaySummary>,
    val goals: DiaryGoals?,
)

private sealed interface HistoryLoad {
    data object Loading : HistoryLoad

    data object Failed : HistoryLoad

    data class Ready(val data: HistoryData) : HistoryLoad
}

/**
 * Diary history (US-24, read-only): a month calendar marking the days with entries, the summary of the selected day
 * and the week (last 7 days) and month figures, which average over logged days only. The viewed month, the selected
 * day and the panel live in [savedState]. A month change reloads that month; the week window and the goals are
 * fetched once and kept.
 */
class DiaryHistoryViewModel(
    private val foodDiary: FoodDiaryRepository,
    private val goalsRepository: NutritionGoalsRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val month = MutableStateFlow(
        savedState.get<String>(KEY_MONTH)?.let(YearMonth::parse) ?: clock.today().yearMonth,
    )
    private val selectedDay = MutableStateFlow(
        if (savedState.contains(KEY_DAY)) {
            savedState.get<String>(KEY_DAY)?.takeIf { it != NO_DAY }?.let(LocalDate::parse)
        } else {
            defaultSelection(month.value)
        },
    )
    private val view = MutableStateFlow(
        savedState.get<String>(KEY_VIEW)?.let { name -> DiaryHistoryView.entries.firstOrNull { it.name == name } }
            ?: DiaryHistoryView.Day,
    )
    private val load = MutableStateFlow<HistoryLoad>(HistoryLoad.Loading)
    private var loadJob: Job? = null
    private var weekCache: Pair<LocalDate, List<DiaryDaySummary>>? = null
    private var goalsCache: Result<DiaryGoals?>? = null

    private val effectChannel = Channel<DiaryHistoryEffect>(Channel.BUFFERED)
    val effects: Flow<DiaryHistoryEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<DiaryHistoryUiState> = combine(load, selectedDay, view, ::buildState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DiaryHistoryUiState.Loading)

    init {
        reload()
    }

    fun onEvent(event: DiaryHistoryEvent) {
        when (event) {
            is DiaryHistoryEvent.SelectDay -> select(event.date)

            DiaryHistoryEvent.PreviousMonth -> showMonth(month.value.minus(1, DateTimeUnit.MONTH))

            DiaryHistoryEvent.NextMonth -> if (month.value < clock.today().yearMonth) {
                showMonth(month.value.plus(1, DateTimeUnit.MONTH))
            }

            is DiaryHistoryEvent.SelectView -> {
                view.value = event.view
                savedState[KEY_VIEW] = event.view.name
            }

            DiaryHistoryEvent.OpenDay -> selectedDay.value?.let {
                effectChannel.trySend(DiaryHistoryEffect.OpenDiaryDay(it))
            }

            DiaryHistoryEvent.Retry -> if (load.value == HistoryLoad.Failed) reload()

            DiaryHistoryEvent.Refresh -> if (load.value is HistoryLoad.Ready) reload(silent = true)
        }
    }

    private fun select(date: LocalDate) {
        if (date.yearMonth != month.value || date > clock.today()) return
        setSelected(date)
    }

    private fun showMonth(target: YearMonth) {
        month.value = target
        savedState[KEY_MONTH] = target.toString()
        setSelected(defaultSelection(target))
        reload()
    }

    private fun setSelected(date: LocalDate?) {
        selectedDay.value = date
        savedState[KEY_DAY] = date?.toString() ?: NO_DAY
    }

    private fun defaultSelection(target: YearMonth): LocalDate? = clock.today().takeIf { it.yearMonth == target }

    private fun reload(silent: Boolean = false) {
        loadJob?.cancel()
        if (!silent) load.value = HistoryLoad.Loading else weekCache = null
        val target = month.value
        val today = clock.today()
        loadJob = viewModelScope.launch {
            val (monthResult, weekResult, goalsResult) = coroutineScope {
                val monthDays = async { foodDiary.summaries(target.firstDay, target.lastDay) }
                val weekDays = async { weekDays(today) }
                val goals = async { goals() }
                Triple(monthDays.await(), weekDays.await(), goals.await())
            }
            val monthDays = monthResult.getOrNull()
            val week = weekResult.getOrNull()
            if (monthDays == null || week == null) {
                // A failed silent refresh keeps what is shown.
                if (!silent) load.value = HistoryLoad.Failed
                return@launch
            }
            load.value = HistoryLoad.Ready(HistoryData(target, monthDays, week, goalsResult.getOrNull()))
        }
    }

    private suspend fun weekDays(today: LocalDate): Result<List<DiaryDaySummary>> {
        weekCache?.takeIf { it.first == today }?.let { return Result.success(it.second) }
        val from = today.minus(WEEK_BACK_DAYS, DateTimeUnit.DAY)
        return foodDiary.summaries(from, today).onSuccess { weekCache = today to it }
    }

    /** A failed read of the goals is not fatal: the history is shown without targets. */
    private suspend fun goals(): Result<DiaryGoals?> {
        goalsCache?.let { return it }
        val result = goalsRepository.getOwn().map { row ->
            row?.let {
                val targets = CalculateMacroTargets(it.targetKcal, it.split)
                DiaryGoals(it.targetKcal, targets.proteinG, targets.carbsG, targets.fatG)
            }
        }
        if (result.isSuccess) goalsCache = result
        return result
    }

    private fun buildState(load: HistoryLoad, selected: LocalDate?, view: DiaryHistoryView): DiaryHistoryUiState =
        when (load) {
            HistoryLoad.Loading -> DiaryHistoryUiState.Loading
            HistoryLoad.Failed -> DiaryHistoryUiState.Error
            is HistoryLoad.Ready -> DiaryHistoryUiState.Content(content(load.data, selected, view))
        }

    private fun content(data: HistoryData, selected: LocalDate?, view: DiaryHistoryView): DiaryHistoryContent {
        val today = clock.today()
        return DiaryHistoryContent(
            month = data.month,
            today = today,
            canGoNext = data.month < today.yearMonth,
            markedDays = data.monthDays.map { it.date }.toSet(),
            view = view,
            selectedDay = selected?.let {
                DiaryHistoryDay(it, DiaryHistorySummary.day(data.monthDays, it)?.totals, data.goals)
            },
            week = DiaryHistorySummary.week(data.weekDays, today),
            monthSummary = DiaryHistorySummary.month(data.monthDays, data.month),
            goals = data.goals,
        )
    }
}
