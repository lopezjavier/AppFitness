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
import io.github.lopezjavier.appfitness.domain.repository.MealDefaultsRepository
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.domain.usecase.DiaryTotals
import io.github.lopezjavier.appfitness.domain.usecase.GroupMealEntries
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val KEY_DATE = "diary_date"
private const val KEY_COLLAPSED = "diary_collapsed"
private const val KEY_EXPANDED_GROUPS = "diary_expanded_groups"
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

/** The rest of the state: the defaults failure, the expanded comida groups and the day picker's marked days. */
private class DayExtras(
    val defaultsFailed: Boolean,
    val expandedGroups: Set<String>,
    val pickerMarkedDays: Set<LocalDate>,
)

/**
 * Diary tab (US-19, US-20): the day's meals, totals and progress against the goals. The visible day and the collapsed
 * sections live in [savedState]. The list and the totals come from one entries flow, so a repository update re-emits
 * both. The add button emits [DiaryEffect.AddToMeal]; tapping an entry opens its edit sheet; deleting one is immediate
 * (no undo). Writes need the server, so they are refused offline ([DiaryContent.isOffline]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per port; one small function per concern.
class DiaryViewModel(
    private val mealSlots: MealSlotRepository,
    private val foodDiary: FoodDiaryRepository,
    private val goalsRepository: NutritionGoalsRepository,
    private val buildDiaryDay: BuildDiaryDay,
    private val clock: Clock,
    private val connectivity: ConnectivityObserver,
    private val defaults: MealDefaultsRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val groupMealEntries: GroupMealEntries = GroupMealEntries(),
) : ViewModel() {
    private val date = MutableStateFlow(savedState.get<String>(KEY_DATE)?.let(LocalDate::parse) ?: clock.today())
    private val collapsed = MutableStateFlow(
        savedState.get<String>(KEY_COLLAPSED)?.split(ID_SEPARATOR)?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
    )
    private val expandedGroups = MutableStateFlow(
        savedState.get<String>(KEY_EXPANDED_GROUPS)?.split(ID_SEPARATOR)?.filter { it.isNotEmpty() }?.toSet().orEmpty(),
    )
    private val setup = MutableStateFlow<Setup>(Setup.Loading)
    private val entriesFailed = MutableStateFlow(false)
    private var setupJob: Job? = null
    private var dayJob: Job? = null
    private val online = MutableStateFlow(true)
    private val defaultsFailed = MutableStateFlow(false)

    /** True once today's defaults were applied in this ViewModel's life (the server is idempotent anyway). */
    private var applyAttempted = false

    private val effectChannel = Channel<DiaryEffect>(Channel.BUFFERED)
    val effects: Flow<DiaryEffect> = effectChannel.receiveAsFlow()
    private val writes = DiaryEntryWrites(foodDiary, { online.value }, { effectChannel.send(it) })

    /** Entries being deleted: hidden at once. Transient on purpose (not in [savedState]); see [delete]. */
    private val hiddenIds = MutableStateFlow<Set<String>>(emptySet())

    private val dayEntries: Flow<DayEntries?> = date.flatMapLatest { day ->
        foodDiary.entries(day)
            .onEach { list ->
                val present = list.mapTo(mutableSetOf()) { it.id }
                hiddenIds.update { hidden -> hidden.filterTo(mutableSetOf()) { it in present } }
            }
            .map<List<FoodLogEntry>, DayEntries?> { DayEntries(day, it) }
            .onStart { emit(null) }
    }

    private val visibleDayEntries: Flow<DayEntries?> = combine(dayEntries, hiddenIds) { loaded, hidden ->
        loaded?.let { day -> DayEntries(day.date, day.entries.filterNot { it.id in hidden }) }
    }

    private val pickerMonth = MutableStateFlow<YearMonth?>(null)
    private val pickerMarkedDays = MutableStateFlow<Set<LocalDate>>(emptySet())

    private val inputs: Flow<DayInputs> = combine(
        setup,
        visibleDayEntries,
        collapsed,
        entriesFailed,
        online,
        ::DayInputs,
    )

    private val extras: Flow<DayExtras> = combine(defaultsFailed, expandedGroups, pickerMarkedDays, ::DayExtras)

    val uiState: StateFlow<DiaryUiState> = combine(inputs, extras, ::buildState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DiaryUiState.Loading)

    init {
        viewModelScope.launch { connectivity.isOnline.collect { online.value = it } }
        viewModelScope.launch { keepPickerMarksFresh() }
        loadSetup(silent = false)
        refreshDay(date.value)
    }

    /**
     * Loads the marked days of the picker's month; a new month or a change of the visible day's entries cancels the
     * running load and starts another. A failure leaves the marks empty: they are decoration.
     */
    private suspend fun keepPickerMarksFresh() {
        pickerMonth.filterNotNull().combine(visibleDayEntries) { month, _ -> month }.collectLatest { month ->
            val loaded = foodDiary.summaries(month.firstDay, month.lastDay).getOrNull()
            pickerMarkedDays.value = loaded?.map { it.date }?.toSet().orEmpty()
        }
    }

    fun onEvent(event: DiaryEvent) {
        when (event) {
            DiaryEvent.PreviousDay,
            DiaryEvent.NextDay,
            is DiaryEvent.GoToDay,
            DiaryEvent.GoToToday,
            -> navigate(event)

            is DiaryEvent.PickerMonthShown -> pickerMonth.value = event.month

            is DiaryEvent.ToggleSection -> toggle(event.slotId)

            is DiaryEvent.ToggleGroup -> toggleGroup(event.groupId)

            DiaryEvent.Retry -> retry()

            DiaryEvent.RetryDefaults -> retryDefaults()

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

            is DiaryEvent.DeleteEntry -> visibleEntries().firstOrNull { it.id == event.entryId }?.let(::delete)
        }
    }

    /** The day-changing events; never past today. */
    private fun navigate(event: DiaryEvent) {
        val today = clock.today()
        val target = when (event) {
            DiaryEvent.PreviousDay -> date.value.minus(1, DateTimeUnit.DAY)
            DiaryEvent.NextDay -> date.value.plus(1, DateTimeUnit.DAY)
            is DiaryEvent.GoToDay -> event.date
            DiaryEvent.GoToToday -> today
            else -> return
        }
        if (target <= today) showDay(target)
    }

    /**
     * Optimistic: the entry is hidden at once (a hidden entry is no longer visible, so a second delete finds nothing)
     * and returns if the server refuses. Offline it is refused without hiding. After a success it stays hidden until
     * a day emission no longer contains it.
     */
    private fun delete(entry: FoodLogEntry) {
        if (entry.id in hiddenIds.value) return
        val hide = online.value
        if (hide) hiddenIds.update { it + entry.id }
        viewModelScope.launch {
            if (!writes.delete(entry) && hide) hiddenIds.update { it - entry.id }
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

    private fun toggleGroup(groupId: String) {
        expandedGroups.update { if (groupId in it) it - groupId else it + groupId }
        savedState[KEY_EXPANDED_GROUPS] = expandedGroups.value.joinToString(ID_SEPARATOR)
    }

    private fun retry() {
        if (uiState.value != DiaryUiState.Error) return
        if (setup.value == Setup.Failed) loadSetup(silent = false)
        if (entriesFailed.value) {
            entriesFailed.value = false
            refreshDay(date.value)
        }
    }

    private fun retryDefaults() {
        if (!defaultsFailed.value || date.value != clock.today()) return
        applyAttempted = false
        refreshDay(date.value)
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
            if (day == clock.today() && !applyAttempted && online.value) applyDefaults(day)
            entriesFailed.value = foodDiary.refresh(day).isFailure
        }
    }

    /** Fills [day] (today) with the weekday's defaults; runs before the refresh so the new rows are in it. */
    private suspend fun applyDefaults(day: LocalDate) {
        val applied = defaults.applyForDate(day).isSuccess
        applyAttempted = applied
        defaultsFailed.value = !applied
    }

    private fun buildState(inputs: DayInputs, extras: DayExtras): DiaryUiState = when {
        inputs.setup == Setup.Failed || (inputs.entriesFailed && inputs.loaded == null) -> DiaryUiState.Error
        inputs.setup !is Setup.Ready || inputs.loaded == null -> DiaryUiState.Loading
        else -> DiaryUiState.Content(content(inputs.setup, inputs.loaded, inputs, extras))
    }

    private fun content(setup: Setup.Ready, loaded: DayEntries, inputs: DayInputs, extras: DayExtras): DiaryContent {
        val day = buildDiaryDay(loaded.date, setup.slots, loaded.entries, setup.goals)
        val remaining = day.goals?.let { DiaryTotals.remainingKcal(it.targetKcal, day.totals.kcal) }
        val today = clock.today()
        return DiaryContent(
            date = loaded.date,
            today = today,
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
                    rows = groupMealEntries(section.entries),
                )
            },
            dayTotals = day.totals,
            collapsedSlotIds = inputs.collapsed,
            expandedGroupIds = extras.expandedGroups,
            isOffline = !inputs.online,
            defaultsFailed = extras.defaultsFailed && loaded.date == today,
            isToday = loaded.date == today,
            pickerMarkedDays = extras.pickerMarkedDays,
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
