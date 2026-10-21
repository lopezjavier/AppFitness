package io.github.lopezjavier.appfitness.feature.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
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

/** The flows that describe the day: setup, entries, collapsed sections, load failure and connectivity. */
private class DayInputs(
    val setup: Setup,
    val loaded: DayEntries?,
    val collapsed: Set<String>,
    val entriesFailed: Boolean,
    val online: Boolean,
)

/**
 * Diary tab (US-19, US-20): the day's meals, totals and progress against the goals. The visible day and the collapsed
 * sections live in [savedState]. The list and the totals come from one entries flow, so a repository update re-emits
 * both. The add button emits [DiaryEffect.AddToMeal]; tapping an entry opens its edit sheet; deleting one is immediate
 * (no undo). Writes need the server, so they are refused offline ([DiaryContent.isOffline]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList") // One dependency per port.
class DiaryViewModel(
    private val mealSlots: MealSlotRepository,
    private val foodDiary: FoodDiaryRepository,
    private val goalsRepository: NutritionGoalsRepository,
    private val buildDiaryDay: BuildDiaryDay,
    private val clock: Clock,
    private val connectivity: ConnectivityObserver,
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
    private val online = MutableStateFlow(true)

    private val effectChannel = Channel<DiaryEffect>(Channel.BUFFERED)
    val effects: Flow<DiaryEffect> = effectChannel.receiveAsFlow()
    private val writes = DiaryEntryWrites(foodDiary, { online.value }, { effectChannel.send(it) })

    private val dayEntries: Flow<DayEntries?> = date.flatMapLatest { day ->
        foodDiary.entries(day).map<List<FoodLogEntry>, DayEntries?> { DayEntries(day, it) }.onStart { emit(null) }
    }

    val uiState: StateFlow<DiaryUiState> = combine(setup, dayEntries, collapsed, entriesFailed, online, ::DayInputs)
        .map(::buildState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DiaryUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
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

            is DiaryEvent.AddToMeal,
            DiaryEvent.AddFood,
            DiaryEvent.OpenGoals,
            is DiaryEvent.EditEntry,
            is DiaryEvent.SaveSection,
            -> event.openEffect(date.value)?.let(effectChannel::trySend)

            is DiaryEvent.DeleteEntry -> visibleEntries().firstOrNull { it.id == event.entryId }?.let {
                viewModelScope.launch { writes.delete(it) }
            }
        }
    }

    private fun visibleEntries(): List<FoodLogEntry> =
        (uiState.value as? DiaryUiState.Content)?.content?.meals?.flatMap { it.entries }.orEmpty()

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

    private fun buildState(inputs: DayInputs): DiaryUiState = when {
        inputs.setup == Setup.Failed || (inputs.entriesFailed && inputs.loaded == null) -> DiaryUiState.Error
        inputs.setup !is Setup.Ready || inputs.loaded == null -> DiaryUiState.Loading
        else -> DiaryUiState.Content(content(inputs.setup, inputs.loaded, inputs.collapsed, inputs.online))
    }

    private fun content(
        setup: Setup.Ready,
        loaded: DayEntries,
        collapsedIds: Set<String>,
        online: Boolean,
    ): DiaryContent {
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
            isOffline = !online,
        )
    }
}

/** The navigation effect for the events that only open another screen, null for the rest. */
private fun DiaryEvent.openEffect(day: LocalDate): DiaryEffect? = when (this) {
    is DiaryEvent.AddToMeal -> DiaryEffect.AddToMeal(slotId, day)
    DiaryEvent.AddFood -> DiaryEffect.AddFood(day)
    DiaryEvent.OpenGoals -> DiaryEffect.OpenPreferences
    is DiaryEvent.EditEntry -> DiaryEffect.OpenEditEntry(entryId, day)
    is DiaryEvent.SaveSection -> DiaryEffect.OpenSaveTemplate(slotId, day)
    else -> null
}
