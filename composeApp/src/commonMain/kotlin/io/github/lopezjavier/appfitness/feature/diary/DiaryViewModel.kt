package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.DiaryTotals
import io.github.lopezjavier.appfitness.feature.workout.sessionDateLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val KEY_DATE = "diary_date"
private const val KEY_COLLAPSED = "diary_collapsed"
private const val ID_SEPARATOR = ","

/** The meals and goals the day view needs besides the entries. */
private sealed interface Setup {
    data object Loading : Setup

    data object Failed : Setup

    data class Ready(val slots: List<MealSlot>, val goals: NutritionGoals?) : Setup
}

/** The entries of [date], as last emitted by the repository. */
private class DayEntries(val date: LocalDate, val entries: List<FoodLogEntry>)

/**
 * Diary tab (US-19, US-20): the day's meals, totals and progress against the goals. The visible day and the collapsed
 * sections live in [savedState]. The list and the totals come from one entries flow, so a repository update re-emits
 * both. Reading only; the add button emits [DiaryEffect.AddToMeal].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList") // One dependency per port.
class DiaryViewModel(
    private val mealSlots: MealSlotRepository,
    private val foodDiary: FoodDiaryRepository,
    private val goalsRepository: NutritionGoalsRepository,
    private val buildDiaryDay: BuildDiaryDay,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val date = MutableStateFlow(savedState.get<String>(KEY_DATE)?.let(LocalDate::parse) ?: clock.today())
    private val collapsed = MutableStateFlow(
        savedState.get<String>(KEY_COLLAPSED)?.split(ID_SEPARATOR)?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
    )
    private val setup = MutableStateFlow<Setup>(Setup.Loading)
    private val entriesFailed = MutableStateFlow(false)
    private var setupJob: Job? = null
    private var dayJob: Job? = null

    private val effectChannel = Channel<DiaryEffect>(Channel.BUFFERED)
    val effects: Flow<DiaryEffect> = effectChannel.receiveAsFlow()

    private val dayEntries: Flow<DayEntries?> = date.flatMapLatest { day ->
        foodDiary.entries(day).map<List<FoodLogEntry>, DayEntries?> { DayEntries(day, it) }.onStart { emit(null) }
    }

    val uiState: StateFlow<DiaryUiState> = combine(setup, dayEntries, collapsed, entriesFailed, ::buildState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DiaryUiState.Loading)

    init {
        loadSetup(silent = false)
        refreshDay(date.value)
    }

    fun onEvent(event: DiaryEvent) {
        when (event) {
            DiaryEvent.PreviousDay -> showDay(date.value.minus(1, DateTimeUnit.DAY))

            DiaryEvent.NextDay -> if (date.value < clock.today()) showDay(date.value.plus(1, DateTimeUnit.DAY))

            is DiaryEvent.ToggleSection -> toggle(event.slotId)

            DiaryEvent.Retry -> retry()

            DiaryEvent.Refresh -> {
                loadSetup(silent = true)
                refreshDay(date.value)
            }

            is DiaryEvent.AddToMeal -> effectChannel.trySend(DiaryEffect.AddToMeal(event.slotId, date.value))

            DiaryEvent.AddFood -> effectChannel.trySend(DiaryEffect.AddFood(date.value))

            DiaryEvent.OpenGoals -> effectChannel.trySend(DiaryEffect.OpenPreferences)

            DiaryEvent.OpenMealSettings -> effectChannel.trySend(DiaryEffect.OpenMealSettings(date.value))
        }
    }

    private fun showDay(day: LocalDate) {
        date.value = day
        savedState[KEY_DATE] = day.toString()
        entriesFailed.value = false
        refreshDay(day)
    }

    private fun toggle(slotId: String) {
        collapsed.update { if (slotId in it) it - slotId else it + slotId }
        savedState[KEY_COLLAPSED] = collapsed.value.joinToString(ID_SEPARATOR)
    }

    private fun retry() {
        if (uiState.value != DiaryUiState.Error) return
        if (setup.value == Setup.Failed) loadSetup(silent = false)
        if (entriesFailed.value) {
            entriesFailed.value = false
            refreshDay(date.value)
        }
    }

    private fun loadSetup(silent: Boolean) {
        setupJob?.cancel()
        if (!silent) setup.value = Setup.Loading
        setupJob = viewModelScope.launch {
            val (slots, goals) = coroutineScope {
                val slots = async { mealSlots.listEnabled() }
                val goals = async { goalsRepository.getOwn() }
                slots.await() to goals.await()
            }
            val loadedSlots = slots.getOrNull()
            if (loadedSlots != null && goals.isSuccess) {
                setup.value = Setup.Ready(loadedSlots, goals.getOrNull())
            } else if (!silent) {
                setup.value = Setup.Failed
            }
        }
    }

    private fun refreshDay(day: LocalDate) {
        dayJob?.cancel()
        dayJob = viewModelScope.launch {
            entriesFailed.value = foodDiary.refresh(day).isFailure
        }
    }

    private fun buildState(
        setup: Setup,
        loaded: DayEntries?,
        collapsedIds: Set<String>,
        entriesFailed: Boolean,
    ): DiaryUiState = when {
        setup == Setup.Failed || (entriesFailed && loaded == null) -> DiaryUiState.Error
        setup !is Setup.Ready || loaded == null -> DiaryUiState.Loading
        else -> DiaryUiState.Content(content(setup, loaded, collapsedIds))
    }

    private fun content(setup: Setup.Ready, loaded: DayEntries, collapsedIds: Set<String>): DiaryContent {
        val day = buildDiaryDay(loaded.date, setup.slots, loaded.entries, setup.goals)
        val remaining = day.goals?.let { DiaryTotals.remainingKcal(it.targetKcal, day.totals.kcal) }
        val today = clock.today()
        return DiaryContent(
            date = loaded.date,
            label = sessionDateLabel(loaded.date, today),
            canGoNext = loaded.date < today,
            goals = day.goals?.let { DiaryGoalsState.Set(it, remaining ?: 0) } ?: DiaryGoalsState.NotSet,
            meals = day.meals.map { section ->
                val isEmpty = section.entries.isEmpty()
                MealSectionUi(
                    slot = section.slot,
                    entries = section.entries,
                    totals = section.totals,
                    isEmpty = isEmpty,
                    showRemainingHint = isEmpty && remaining != null && remaining > 0,
                    remainingKcal = remaining,
                )
            },
            dayTotals = day.totals,
            collapsedSlotIds = collapsedIds,
        )
    }
}
