package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderStart
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderState
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderStateCodec
import io.github.lopezjavier.appfitness.domain.coach.builder.CardioTargets
import io.github.lopezjavier.appfitness.domain.coach.builder.GroupPriority
import io.github.lopezjavier.appfitness.domain.coach.builder.NextBlockContext
import io.github.lopezjavier.appfitness.domain.coach.builder.NextBlockSeed
import io.github.lopezjavier.appfitness.domain.coach.builder.NextBlockTargets
import io.github.lopezjavier.appfitness.domain.coach.builder.Pick
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerContext
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType
import io.github.lopezjavier.appfitness.domain.coach.builder.SectionPrescription
import io.github.lopezjavier.appfitness.domain.coach.builder.Slot
import io.github.lopezjavier.appfitness.domain.coach.builder.SlotEligibility
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.FavoriteExercisesRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.LoadNextBlockContextUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val KEY_STEP = "block_builder_step"
private const val KEY_SPLIT = "block_builder_split"
private const val KEY_TYPES = "block_builder_types"
private const val KEY_OPEN_DAY = "block_builder_open_day"
private const val KEY_STATE = "block_builder_state"
private const val KEY_SELECTED_DAY = "block_builder_selected_day"
private const val KEY_OPEN_SLOT = "block_builder_open_slot"
private const val KEY_DISCARD = "block_builder_discard"
private const val KEY_NEXT_MODE = "block_builder_next_mode"
private const val KEY_PRIORITIES = "block_builder_priorities"
private const val KEY_SUGGESTIONS = "block_builder_suggestions"

private const val SLOT_PARTS = 3
private const val STEP_SPLITS = "splits"
private const val STEP_TYPES = "types"
private const val STEP_BUILDER = "builder"
private const val STEP_TARGETS = "targets"
private const val PRIORITY_PARTS = 2

/** What the builder reads once: the user's training choices, the catalogue, the history and the cardio target. */
@Suppress("LongParameterList") // Everything the builder reads once.
private data class Inputs(
    val days: Int,
    val intention: TrainingIntention?,
    val sessionMinutes: Int,
    val equipment: List<Equipment>,
    val catalogue: List<CatalogExercise>,
    val favouriteIds: Set<String>,
    val builder: BuilderInputs,
    val cardioTargetMinWeek: Int,
)

/**
 * "Desde cero" (US-72, US-93): split, type per day and builder in one ViewModel, with no model call; the only write
 * is [BlockBuilderEvent.Save]. Step, picks and open sheets live in [savedState] (ADR-40); the inputs are re-read.
 */
@Suppress("TooManyFunctions", "LongParameterList") // One small handler per event; the reads the builder needs.
class BlockBuilderViewModel(
    private val planId: String,
    private val builder: BuildBlockUseCase,
    private val catalog: LoadExerciseCatalog,
    private val favourites: FavoriteExercisesRepository,
    private val exercises: ExerciseRepository,
    private val preferences: TrainingPreferencesRepository,
    private val plans: CoachPlanRepository,
    private val clock: Clock,
    /** The plan whose review just closed: with [nextBlock] the builder opens in next-block mode (US-102). */
    private val previousPlanId: String? = null,
    private val nextBlock: LoadNextBlockContextUseCase? = null,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<BlockBuilderUiState>(BlockBuilderUiState.Loading)
    val uiState: StateFlow<BlockBuilderUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<BlockBuilderEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var inputs: Inputs? = null

    private val nextMode = previousPlanId != null && nextBlock != null
    private var context: NextBlockContext? = null
    private var choices: Map<MuscleGroup, GroupPriority> =
        savedState.get<String>(KEY_PRIORITIES)?.let(::decodeChoices).orEmpty()
    private var accepted: Set<MuscleGroup> =
        savedState.get<String>(KEY_SUGGESTIONS)?.let(::decodeGroups).orEmpty()

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        load()
    }

    @Suppress("CyclomaticComplexMethod") // One branch per event.
    fun onEvent(event: BlockBuilderEvent) {
        when (event) {
            is BlockBuilderEvent.PickSplit -> pickSplit(event.index)
            is BlockBuilderEvent.OpenDay -> openDay(event.day)
            is BlockBuilderEvent.PickType -> pickType(event.type)
            BlockBuilderEvent.Continue -> next()
            is BlockBuilderEvent.SetPriority -> setPriority(event.group, event.priority)
            is BlockBuilderEvent.ApplySuggestion -> applySuggestion(event.group)
            BlockBuilderEvent.ContinueFromTargets -> continueFromTargets()
            is BlockBuilderEvent.SelectDay -> selectDay(event.day)
            is BlockBuilderEvent.OpenSlot -> openSlot(event.slot)
            is BlockBuilderEvent.PickExercise -> pickExercise(event.exerciseId)
            is BlockBuilderEvent.CreateCustomExercise -> createCustom(event)
            is BlockBuilderEvent.ClearSlot -> edit { it.clear(event.day, event.index) }
            is BlockBuilderEvent.AddExtra -> addExtra(event.day)
            is BlockBuilderEvent.RemoveExtra -> edit { it.removeExtra(event.day, event.index) }
            is BlockBuilderEvent.SwapCardio -> swapCardio(event.exerciseId)
            BlockBuilderEvent.Save -> save()
            BlockBuilderEvent.Back -> back()
            BlockBuilderEvent.ConfirmDiscard -> effectChannel.trySend(BlockBuilderEffect.Close)
            BlockBuilderEvent.CancelDiscard -> updateBuilder { it.copy(discardDialog = false) }
            BlockBuilderEvent.Retry -> if (_uiState.value is BlockBuilderUiState.Failed) load()
        }
    }

    // region loading and restoring

    private fun load() {
        _uiState.value = BlockBuilderUiState.Loading
        viewModelScope.launch {
            val read = readInputs()
            read.onSuccess {
                inputs = it
                _uiState.value = restore(it)
            }.onFailure {
                _uiState.value = BlockBuilderUiState.Failed(offline = it.isOfflineFailure())
            }
        }
    }

    @Suppress("ReturnCount") // One early exit per failed read.
    private suspend fun readInputs(): Result<Inputs> {
        val prefs = preferences.get().getOrElse { return Result.failure(it) }
        val days = prefs.daysPerWeek?.coerceIn(SplitCatalog.MIN_DAYS, SplitCatalog.MAX_DAYS)
        val minutes = prefs.sessionMinutes
        // "Antes de empezar" checks these first; a missing value here is a stale screen, shown as a plain failure.
        if (days == null || minutes == null) {
            return Result.failure(IllegalStateException("Training days or minutes missing"))
        }
        val exercises = catalog().getOrElse { return Result.failure(it) }
        favourites.refresh()
        val built = builder.loadInputs(today()).getOrElse { return Result.failure(it) }
        val plan = plans.currentPlan().getOrElse { return Result.failure(it) }
        if (previousPlanId != null && nextBlock != null) {
            context = nextBlock(previousPlanId).getOrElse { return Result.failure(it) }
        }
        return Result.success(
            Inputs(
                days = days,
                intention = prefs.intention,
                sessionMinutes = minutes,
                equipment = prefs.equipment.sortedBy { it.ordinal },
                catalogue = exercises,
                favouriteIds = favourites.favoriteIds.value,
                builder = built,
                cardioTargetMinWeek = CardioTargets.forPlan(plan),
            ),
        )
    }

    private fun restore(inputs: Inputs): BlockBuilderUiState {
        val splits = builder.splitsFor(inputs.days)
        val splitIndex = (savedState.get<Int>(KEY_SPLIT) ?: 0).coerceIn(0, splits.lastIndex)
        val types = savedState.get<String>(KEY_TYPES)?.let(::decodeTypes)
            ?.takeIf { it.size == inputs.days }
            ?.takeIf { saved -> saved.all { it in splits[splitIndex].family.allowedTypes } }
        val saved = savedState.get<String>(KEY_STATE)?.let(BuilderStateCodec::decode)
        return when {
            savedState.get<String>(KEY_STEP) == STEP_BUILDER && saved != null ->
                builderUi(
                    saved,
                    savedState.get<Int>(KEY_SELECTED_DAY) ?: 0,
                    decodeSlot(savedState.get<String>(KEY_OPEN_SLOT)),
                ).copy(discardDialog = savedState.get<Boolean>(KEY_DISCARD) == true)

            savedState.get<String>(KEY_STEP) == STEP_TYPES && types != null ->
                typesUi(splitIndex, types, savedState.get<Int>(KEY_OPEN_DAY)?.takeIf { it in types.indices })

            nextMode && context != null && savedState.get<String>(KEY_STEP).let { it == null || it == STEP_TARGETS } ->
                targetsUi()

            else -> BlockBuilderUiState.Splits(inputs.days, splits, splitIndex)
        }.also { persist(it) }
    }

    // endregion

    // region splits and types

    private fun pickSplit(index: Int) {
        val state = _uiState.value as? BlockBuilderUiState.Splits ?: return
        if (index !in state.splits.indices) return
        set(state.copy(selectedIndex = index))
    }

    private fun typesUi(splitIndex: Int, chosen: List<RoutineType>, openDay: Int?): BlockBuilderUiState.Types {
        val data = checkNotNull(inputs)
        val split = builder.splitsFor(data.days)[splitIndex]
        val options = chosen.indices.map { builder.dayOptions(split, chosen, it, data.intention) }
        val start = startBuilder(data, chosen)
        val noFit = (start as? BuilderStart.NoFit)?.let {
            NoFitInfo(it.dayIndex, it.fit.minutesNeeded, it.fit.limitMinutes)
        }
        return BlockBuilderUiState.Types(split, chosen, options, openDay, noFit)
    }

    private fun startBuilder(data: Inputs, types: List<RoutineType>): BuilderStart = builder.start(
        types,
        data.intention,
        data.sessionMinutes,
        data.equipment,
        data.cardioTargetMinWeek,
        data.builder,
        data.catalogue,
        currentTargets(),
    )

    // region next block

    private fun targetResult(): NextBlockTargets.Result? = context?.let {
        NextBlockTargets.compute(it.baseSets, choices, it.adherencePct, it.cut, it.age, accepted)
    }

    private fun currentTargets(): Map<MuscleGroup, Int> = targetResult()?.targets.orEmpty()

    private fun targetsUi(): BlockBuilderUiState.Targets {
        val ctx = checkNotNull(context)
        val result = checkNotNull(targetResult())
        val rows = result.rows.map { row ->
            val applied = row.group in accepted
            val offered = !applied && row.choice != GroupPriority.Maintain && ctx.suggestion(row.group, row.after)
            val suggestion = when {
                applied -> SuggestionState.Applied
                offered -> SuggestionState.Offered
                else -> null
            }
            GroupTargetRow(row.group, row.choice, row.before, row.after, row.blocked, suggestion)
        }
        val seeded = NextBlockSeed.types(ctx.previousBlock)
        val noFit = seeded?.let { startBuilder(checkNotNull(inputs), it) as? BuilderStart.NoFit }?.let {
            NoFitInfo(it.dayIndex, it.fit.minutesNeeded, it.fit.limitMinutes)
        }
        return BlockBuilderUiState.Targets(rows, ctx.adherencePct, ctx.cut, noFit)
    }

    @Suppress("ReturnCount") // Guards for another step, an unknown group or a fourth "Priorizar".
    private fun setPriority(group: MuscleGroup, priority: GroupPriority) {
        val state = _uiState.value as? BlockBuilderUiState.Targets ?: return
        val row = state.rows.firstOrNull { it.group == group } ?: return
        val fourth = priority == GroupPriority.Prioritise && row.choice != GroupPriority.Prioritise &&
            state.prioritisedCount >= NextBlockTargets.MAX_PRIORITISED
        if (fourth) return
        if (priority == GroupPriority.Maintain) accepted = accepted - group
        choices = if (priority == GroupPriority.None) choices - group else choices + (group to priority)
        set(targetsUi())
    }

    private fun applySuggestion(group: MuscleGroup) {
        val state = _uiState.value as? BlockBuilderUiState.Targets ?: return
        when (state.rows.firstOrNull { it.group == group }?.suggestion) {
            SuggestionState.Applied -> accepted = accepted - group

            SuggestionState.Offered -> {
                accepted = accepted + group
                effectChannel.trySend(BlockBuilderEffect.Confirm)
            }

            null -> return
        }
        set(targetsUi())
    }

    @Suppress("ReturnCount") // Guards for another step, a routine that does not fit or missing inputs.
    private fun continueFromTargets() {
        val state = _uiState.value as? BlockBuilderUiState.Targets ?: return
        if (!state.canContinue) return
        val data = inputs ?: return
        val ctx = context ?: return
        val types = NextBlockSeed.types(ctx.previousBlock)
        if (types == null) {
            set(BlockBuilderUiState.Splits(data.days, builder.splitsFor(data.days), 0))
            return
        }
        val start = startBuilder(data, types) as? BuilderStart.Ready ?: return
        val pickerContext = PickerContext(data.equipment.toSet(), data.builder.history, favourites.favoriteIds.value)
        val seeded = NextBlockSeed.apply(start.state, ctx.previousBlock, data.catalogue, pickerContext)
        set(builderUi(seeded, 0, null))
    }

    // endregion

    private fun openDay(day: Int?) {
        val state = _uiState.value as? BlockBuilderUiState.Types ?: return
        if (day != null && day !in state.chosen.indices) return
        set(state.copy(openDay = day))
    }

    @Suppress("ReturnCount") // Guards for a missing sheet or a disabled type.
    private fun pickType(type: RoutineType) {
        val state = _uiState.value as? BlockBuilderUiState.Types ?: return
        val day = state.openDay ?: return
        if (state.options[day].none { it.type == type && it.enabled }) return
        val chosen = state.chosen.toMutableList().also { it[day] = type }
        val index = builder.splitsFor(checkNotNull(inputs).days).indexOf(state.split)
        set(typesUi(index, chosen, openDay = null))
    }

    @Suppress("ReturnCount") // Guards for missing inputs or a routine that does not fit.
    private fun next() {
        val data = inputs ?: return
        when (val state = _uiState.value) {
            is BlockBuilderUiState.Splits -> {
                val split = state.splits[state.selectedIndex]
                set(typesUi(state.selectedIndex, split.types, openDay = null))
            }

            is BlockBuilderUiState.Types -> {
                if (!state.canContinue) return
                val start = startBuilder(data, state.chosen) as? BuilderStart.Ready ?: return
                set(builderUi(start.state, 0, null))
            }

            else -> Unit
        }
    }

    // endregion

    // region builder

    private fun builderState(): BuilderState? = (_uiState.value as? BlockBuilderUiState.Builder)?.state

    private fun builderUi(
        state: BuilderState,
        selectedDay: Int,
        openSlot: SlotRef?,
        previous: BlockBuilderUiState.Builder? = null,
    ): BlockBuilderUiState.Builder {
        val data = checkNotNull(inputs)
        val day = selectedDay.coerceIn(0, state.days.lastIndex)
        val slot = openSlot?.takeIf { it.day in state.days.indices }
        val byId = data.catalogue.associateBy { it.id }
        return BlockBuilderUiState.Builder(
            state = state,
            selectedDay = day,
            openSlot = slot,
            pickerEntries = slot?.let { pickerFor(state, it, data) }.orEmpty(),
            cardioOptions = data.builder.cardioOptions.mapNotNull { byId[it] },
            cardioExercise = state.cardioExerciseId?.let { byId[it] },
            exercises = byId,
            saving = previous?.saving ?: false,
            saveError = previous?.saveError,
            discardDialog = previous?.discardDialog ?: false,
            favouriteIds = favourites.favoriteIds.value,
            stalledExerciseIds = context?.stalledLifts.orEmpty(),
            savedPlanId = previous?.savedPlanId,
            validPatterns = slot?.let { slotOf(state, it) }?.let { SlotEligibility.validPatterns(it.kind) }.orEmpty(),
        )
    }

    private fun slotOf(state: BuilderState, ref: SlotRef): Slot? {
        val day = state.days[ref.day]
        return if (ref.extra) {
            SectionPrescription.extraSlot(day.type.groups.first(), state.intention)
        } else {
            day.slots.getOrNull(ref.index)?.slot
        }
    }

    private fun pickerFor(state: BuilderState, ref: SlotRef, data: Inputs): List<PickerEntry> {
        val slot = slotOf(state, ref) ?: return emptyList()
        val day = state.days[ref.day]
        val own = if (ref.extra) null else day.slots[ref.index].pick?.exerciseId
        val used = (day.slots + day.extras).mapNotNull { it.pick?.exerciseId }.toSet() - setOfNotNull(own)
        val ctx = PickerContext(data.equipment.toSet(), data.builder.history, favourites.favoriteIds.value)
        return builder.eligible(slot, data.catalogue, ctx, used)
    }

    private fun updateBuilder(change: (BlockBuilderUiState.Builder) -> BlockBuilderUiState.Builder) {
        val current = _uiState.value as? BlockBuilderUiState.Builder ?: return
        set(change(current))
    }

    private fun selectDay(day: Int) = updateBuilder { it.copy(selectedDay = day.coerceIn(0, it.state.days.lastIndex)) }

    private fun openSlot(slot: SlotRef?) {
        val current = _uiState.value as? BlockBuilderUiState.Builder ?: return
        if (slot != null && (slot.day !in current.state.days.indices || slotOf(current.state, slot) == null)) return
        set(builderUi(current.state, current.selectedDay, slot, current))
    }

    /** Applies a change to the state; the score, minutes and picker follow. A change clears a failed save. */
    private fun edit(change: (BuilderState) -> BuilderState) {
        val current = _uiState.value as? BlockBuilderUiState.Builder ?: return
        if (current.saving) return
        set(builderUi(change(current.state), current.selectedDay, current.openSlot, current.copy(saveError = null)))
    }

    @Suppress("ReturnCount") // Guards for a closed picker, a save in flight or a disabled row.
    private fun pickExercise(exerciseId: String) {
        val current = _uiState.value as? BlockBuilderUiState.Builder ?: return
        val ref = current.openSlot ?: return
        if (current.saving) return
        val entry = current.pickerEntries.firstOrNull { it.exercise.id == exerciseId } ?: return
        if (entry.disabled) return
        applyPick(current, ref, entry.exercise.exercise)
    }

    private fun applyPick(current: BlockBuilderUiState.Builder, ref: SlotRef, exercise: Exercise) {
        val data = checkNotNull(inputs)
        val exerciseId = exercise.id
        val slot = slotOf(current.state, ref) ?: return
        val pickSlot = if (ref.extra) {
            SectionPrescription.extraSlot(
                exercise.muscleGroup,
                current.state.intention,
            )
        } else {
            slot
        }
        val pick = Pick.of(exercise, pickSlot, data.builder.history[exerciseId])
        val next = if (ref.extra) {
            current.state.addExtra(ref.day, pick)
        } else {
            current.state.pick(ref.day, ref.index, pick)
        }
        set(builderUi(next, current.selectedDay, null, current.copy(saveError = null)))
        effectChannel.trySend(BlockBuilderEffect.Confirm)
    }

    @Suppress("ReturnCount") // Guards for a closed picker, a save in flight or an invalid pattern.
    private fun createCustom(event: BlockBuilderEvent.CreateCustomExercise) {
        val current = _uiState.value as? BlockBuilderUiState.Builder ?: return
        val ref = current.openSlot ?: return
        if (current.saving || event.name.isBlank()) return
        val pattern = event.movementPattern ?: return
        if (pattern !in current.validPatterns) return
        val slot = slotOf(current.state, ref) ?: return
        set(current.copy(createFailed = false))
        viewModelScope.launch {
            val created = exercises.createCustom(
                name = event.name.trim(),
                muscleGroup = event.muscleGroup ?: slot.group,
                equipment = event.equipment,
                perHand = event.perHand,
                movementPattern = pattern,
            )
            val exercise = created.getOrNull()
            val reloaded = if (exercise != null) catalog().getOrNull() else null
            val after = _uiState.value as? BlockBuilderUiState.Builder ?: return@launch
            if (exercise == null || after.openSlot != ref) {
                set(after.copy(createFailed = exercise == null))
                return@launch
            }
            reloaded?.let { list -> inputs = inputs?.copy(catalogue = list) }
            applyPick(builderUi(after.state, after.selectedDay, ref, after), ref, exercise)
        }
    }

    private fun addExtra(day: Int) {
        val extras = builderState()?.days?.getOrNull(day)?.extras ?: return
        openSlot(SlotRef(day, extras.size, extra = true))
    }

    private fun swapCardio(exerciseId: String) {
        val current = _uiState.value as? BlockBuilderUiState.Builder ?: return
        if (current.cardioOptions.none { it.id == exerciseId }) return
        edit { it.withCardio(exerciseId) }
    }

    private fun save() {
        val current = _uiState.value as? BlockBuilderUiState.Builder ?: return
        if (!current.canSave) return
        set(current.copy(saving = true, saveError = null))
        viewModelScope.launch {
            val result = builder.save(planId, current.state, previousPlanId = previousPlanId)
            if (result == null) {
                updateBuilder { it.copy(saving = false) }
                return@launch
            }
            result.onSuccess { saved ->
                updateBuilder { it.copy(saving = false, savedPlanId = saved.planId ?: planId) }
                effectChannel.send(BlockBuilderEffect.Confirm)
                effectChannel.send(BlockBuilderEffect.Saved)
            }.onFailure { failure ->
                val error = failure.toBuilderSaveError()
                updateBuilder { it.copy(saving = false, saveError = error) }
                effectChannel.send(BlockBuilderEffect.Reject)
            }
        }
    }

    private fun hasPicks(state: BuilderState) = state.days.any { d -> (d.slots + d.extras).any { it.pick != null } }

    private fun back() {
        val data = inputs
        when (val state = _uiState.value) {
            is BlockBuilderUiState.Builder -> when {
                state.discardDialog -> set(state.copy(discardDialog = false))
                state.openSlot != null -> set(builderUi(state.state, state.selectedDay, null, state))
                hasPicks(state.state) -> set(state.copy(discardDialog = true))
                nextMode && context != null -> set(targetsUi())
                else -> set(typesUi(savedState.get<Int>(KEY_SPLIT) ?: 0, state.state.days.map { it.type }, null))
            }

            is BlockBuilderUiState.Types -> when {
                state.openDay != null -> set(state.copy(openDay = null))

                data != null -> {
                    val index = savedState.get<Int>(KEY_SPLIT) ?: 0
                    set(BlockBuilderUiState.Splits(data.days, builder.splitsFor(data.days), index))
                }
            }

            else -> effectChannel.trySend(BlockBuilderEffect.Close)
        }
    }

    // endregion

    // region persistence

    private fun set(state: BlockBuilderUiState) {
        _uiState.value = state
        persist(state)
    }

    private fun persist(state: BlockBuilderUiState) {
        savedState.remove<String>(KEY_OPEN_SLOT)
        savedState.remove<Int>(KEY_OPEN_DAY)
        savedState.remove<Boolean>(KEY_DISCARD)
        if (nextMode) {
            savedState[KEY_NEXT_MODE] = true
            savedState[KEY_PRIORITIES] = choices.entries.joinToString(",") { "${it.key.name}:${it.value.name}" }
            savedState[KEY_SUGGESTIONS] = accepted.joinToString(",") { it.name }
        }
        when (state) {
            is BlockBuilderUiState.Targets -> savedState[KEY_STEP] = STEP_TARGETS

            is BlockBuilderUiState.Splits -> {
                savedState[KEY_STEP] = STEP_SPLITS
                savedState[KEY_SPLIT] = state.selectedIndex
            }

            is BlockBuilderUiState.Types -> {
                savedState[KEY_STEP] = STEP_TYPES
                savedState[KEY_SPLIT] = checkNotNull(inputs).let { builder.splitsFor(it.days).indexOf(state.split) }
                savedState[KEY_TYPES] = state.chosen.joinToString(",") { it.name }
                state.openDay?.let { savedState[KEY_OPEN_DAY] = it }
            }

            is BlockBuilderUiState.Builder -> {
                savedState[KEY_STEP] = STEP_BUILDER
                savedState[KEY_STATE] = BuilderStateCodec.encode(state.state)
                savedState[KEY_SELECTED_DAY] = state.selectedDay
                state.openSlot?.let { savedState[KEY_OPEN_SLOT] = "${it.day}:${it.index}:${it.extra}" }
                if (state.discardDialog) savedState[KEY_DISCARD] = true
            }

            else -> Unit
        }
    }

    // A saved name that is not a RoutineType (a renamed type after an app update) decodes to null, so restore falls
    // back to the splits step instead of crashing on a stale saved state.
    private fun decodeTypes(raw: String): List<RoutineType>? = runCatching {
        raw.split(",").map { name -> RoutineType.valueOf(name) }
    }.getOrNull()

    // A saved group or priority that no longer exists decodes to nothing, like a stale type above.
    private fun decodeChoices(raw: String): Map<MuscleGroup, GroupPriority> = raw.split(",").mapNotNull { part ->
        val bits = part.split(":").takeIf { it.size == PRIORITY_PARTS } ?: return@mapNotNull null
        val group = MuscleGroup.entries.firstOrNull { it.name == bits[0] }
        val priority = GroupPriority.entries.firstOrNull { it.name == bits[1] && it != GroupPriority.None }
        if (group == null || priority == null) null else group to priority
    }.toMap()

    private fun decodeGroups(raw: String): Set<MuscleGroup> =
        raw.split(",").mapNotNull { name -> MuscleGroup.entries.firstOrNull { it.name == name } }.toSet()

    private fun decodeSlot(raw: String?): SlotRef? = raw?.split(":")?.takeIf { it.size == SLOT_PARTS }?.let {
        val day = it[0].toIntOrNull()
        val index = it[1].toIntOrNull()
        if (day == null || index == null) null else SlotRef(day, index, it[2] == "true")
    }

    // endregion
}
