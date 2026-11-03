package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.domain.model.ExerciseNames
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.FavoriteExercisesRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.usecase.EquipmentFilter
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.FavoritesFirst
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.SimilarExerciseRow
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KEY_WEEK = "block_week"
private const val KEY_REASON = "block_reason_exercise"
private const val KEY_REPLACEMENTS = "block_replacements"
private const val KEY_REPLACE_SLOT = "block_replace_slot"
private const val REPLACEMENT_PARTS = 3

/**
 * The block preview (US-72). The proposal is always reloaded from the stored rows, so a finished stream survives
 * process death. It never calls the coach: the paid calls belong to [BlockSplitsViewModel], and a preview with no
 * stored block shows [BlockPreviewUiState.Failed]. [savedState] remembers the selected week, the open reason sheet
 * and the pending swaps. Leaving the screen saves nothing: only [BlockPreviewEvent.Save] does.
 */
@Suppress("LongParameterList", "TooManyFunctions", "ReturnCount") // One small guarded handler per event.
class BlockPreviewViewModel(
    private val planId: String,
    private val blocks: CoachBlockRepository,
    private val exercises: ExerciseRepository,
    private val library: ExerciseLibrary,
    private val preferences: TrainingPreferencesRepository,
    private val favorites: FavoriteExercisesRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val exerciseNames: ExerciseNameResolver = ExerciseNameResolver(exercises, library),
) : ViewModel() {
    private val _uiState = MutableStateFlow<BlockPreviewUiState>(BlockPreviewUiState.Loading)
    val uiState: StateFlow<BlockPreviewUiState> = _uiState.asStateFlow()

    private var alternativesJob: Job? = null
    private var prefetchJob: Job? = null

    // Alternative ids by the coach's exercise id; an exercise whose read failed is absent.
    private val alternativeIds = mutableMapOf<String, Set<String>>()
    private var prefetched = false

    // A block that could not be shown because its names did not resolve: Retry shows it again without asking the coach.
    private var unnamedBlock: CoachBlock? = null

    private val effectChannel = Channel<BlockPreviewEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { exerciseNames.observeChanges().collect(::renameExercises) }
        viewModelScope.launch {
            val stored = blocks.currentBlock(planId)
            val block = stored.getOrNull()
            when {
                stored.isFailure ->
                    _uiState.value =
                        BlockPreviewUiState.Failed(planFailureKindOf(stored.exceptionOrNull()))

                block != null -> showBlock(block, restore = true)

                // No stored block: never asks (and bills) from here; Retry only reloads.
                else -> _uiState.value = BlockPreviewUiState.Failed(PlanFailureKind.Interrupted)
            }
        }
    }

    fun onEvent(event: BlockPreviewEvent) {
        when (event) {
            BlockPreviewEvent.Retry -> if (_uiState.value is BlockPreviewUiState.Failed) retry()
            is BlockPreviewEvent.SelectWeek -> selectWeek(event.week)
            is BlockPreviewEvent.OpenReason -> setReason(event.key)
            BlockPreviewEvent.CloseReason -> setReason(null)
            BlockPreviewEvent.Save -> save()
            is BlockPreviewEvent.OpenReplace -> openReplace(event.sessionIndex, event.position)
            BlockPreviewEvent.LoadAlternatives -> loadAlternatives()
            is BlockPreviewEvent.PickReplacement -> pickReplacement(event.exerciseId)
            BlockPreviewEvent.ClearReplacement -> clearReplacement()
            BlockPreviewEvent.CloseReplace -> setReplaceSlot(null)
        }
    }

    private fun openReplace(sessionIndex: Int, position: Int) {
        val preview = _uiState.value as? BlockPreviewUiState.Preview ?: return
        val session = preview.block.sessions.firstOrNull { it.sessionIndex == sessionIndex } ?: return
        if (position !in session.exercises.indices || preview.saving) return
        setReplaceSlot(replaceSlotKey(sessionIndex, position))
        loadAlternatives()
    }

    private fun setReplaceSlot(slot: String?) {
        alternativesJob?.cancel()
        if (slot == null) savedState.remove<String>(KEY_REPLACE_SLOT) else savedState[KEY_REPLACE_SLOT] = slot
        _uiState.update { state ->
            if (state is BlockPreviewUiState.Preview) {
                state.copy(replaceSlot = slot, alternatives = AlternativesState.Loading)
            } else {
                state
            }
        }
    }

    /** Reads the alternatives of the coach's exercise at the open slot (a read only; nothing is written). */
    private fun loadAlternatives() {
        val preview = _uiState.value as? BlockPreviewUiState.Preview ?: return
        val slot = slotOf(preview.replaceSlot) ?: return
        val original = preview.block.sessions.firstOrNull { it.sessionIndex == slot.first }
            ?.exercises?.getOrNull(slot.second) ?: return
        _uiState.value = preview.copy(alternatives = AlternativesState.Loading)
        alternativesJob?.cancel()
        alternativesJob = viewModelScope.launch {
            // Preferences are read on every open (never cached), so a change in "Mi entrenamiento" applies at once.
            // Best effort: when offline the last known favourites apply.
            favorites.refresh()
            val selection = selectedEquipment()
            val result = if (selection.isSuccess) exercises.alternatives(original.exerciseId) else null
            val failure = selection.exceptionOrNull() ?: result?.exceptionOrNull()
            val state = if (failure != null) {
                if ((failure as? AppException)?.error == AppError.Network) {
                    AlternativesState.Offline
                } else {
                    AlternativesState.Error
                }
            } else {
                val allowed = EquipmentFilter.filter(result?.getOrNull().orEmpty(), selection.getOrNull())
                alternativesState(allowed, slot)
            }
            _uiState.update { current ->
                if (current is BlockPreviewUiState.Preview &&
                    current.replaceSlot == replaceSlotKey(slot.first, slot.second)
                ) {
                    current.copy(alternatives = state)
                } else {
                    current
                }
            }
        }
    }

    /** The user's current equipment (null = everything), read fresh from the preferences. */
    private suspend fun selectedEquipment(): Result<Set<Equipment>?> =
        preferences.get().map { EquipmentFilter.selectedFrom(it) }

    /** Named alternatives that are not already elsewhere in the workout (the server refuses duplicates). */
    private suspend fun alternativesState(
        alternatives: List<ExerciseAlternative>,
        slot: Pair<Int, Int>,
    ): AlternativesState {
        val preview = _uiState.value as? BlockPreviewUiState.Preview ?: return AlternativesState.Error
        val session = preview.sessions.firstOrNull { it.sessionIndex == slot.first }
        val others = session?.exercises?.filterIndexed { position, _ -> position != slot.second }
            ?.map { it.exerciseId }.orEmpty().toSet()
        val candidates = alternatives.filter { it.exercise.id !in others }
        if (candidates.isEmpty()) return AlternativesState.Empty
        val named = catalog().getOrElse { return AlternativesState.Error }.associateBy { it.id }
        val starred = favorites.favoriteIds.value
        val ranked = candidates.mapNotNull { alt ->
            named[alt.exercise.id]?.let { SimilarExerciseRow(it, alt.similarity, it.id in starred) }
        }
            .sortedWith(
                // Any cardio counts the same minutes: no ranking, alphabetical.
                if (preview.isCardioSwap) {
                    compareBy { it.exercise.name }
                } else {
                    compareByDescending<SimilarExerciseRow> { it.similarity }.thenBy { it.exercise.name }
                },
            )
        val rows = FavoritesFirst(ranked, starred) { it.exercise.id }
        return if (rows.isEmpty()) AlternativesState.Empty else AlternativesState.Content(rows)
    }

    private fun pickReplacement(exerciseId: String) {
        val preview = _uiState.value as? BlockPreviewUiState.Preview ?: return
        val slot = preview.replaceSlot ?: return
        val row = (preview.alternatives as? AlternativesState.Content)?.rows
            ?.firstOrNull { it.exercise.id == exerciseId } ?: return
        val chosen = row.exercise.exercise
        val replacements = preview.replacements + (slot to Replacement(chosen.id, chosen.key, chosen.equipment))
        applyReplacements(preview, replacements, slot = null)
    }

    private fun clearReplacement() {
        val preview = _uiState.value as? BlockPreviewUiState.Preview ?: return
        val slot = preview.replaceSlot ?: return
        applyReplacements(preview, preview.replacements - slot, slot = null)
    }

    private fun applyReplacements(
        preview: BlockPreviewUiState.Preview,
        replacements: Map<String, Replacement>,
        slot: String?,
    ) {
        alternativesJob?.cancel()
        saveReplacementsState(replacements, slot)
        _uiState.value = preview.copy(
            replacements = replacements,
            replaceSlot = slot,
            alternatives = AlternativesState.Loading,
        ).withSwappable()
    }

    private fun saveReplacementsState(replacements: Map<String, Replacement>, slot: String?) {
        if (replacements.isEmpty()) {
            savedState.remove<String>(KEY_REPLACEMENTS)
        } else {
            savedState[KEY_REPLACEMENTS] =
                replacements.map { (key, value) -> "${key.replace('/', '|')}|${value.exerciseId}" }
                    .joinToString(",")
        }
        if (slot == null) savedState.remove<String>(KEY_REPLACE_SLOT) else savedState[KEY_REPLACE_SLOT] = slot
    }

    private fun clearReplacementsState() {
        savedState.remove<String>(KEY_REPLACEMENTS)
        savedState.remove<String>(KEY_REPLACE_SLOT)
    }

    private suspend fun catalog() = LoadExerciseCatalog(exercises, library)()

    /** Rebuilds the swaps kept in [savedState] against the block; one that no longer fits is dropped. */
    private suspend fun restoredReplacements(block: CoachBlock, names: Map<String, String>): Map<String, Replacement> {
        val stored = savedState.get<String>(KEY_REPLACEMENTS)?.split(',').orEmpty().filter { it.isNotEmpty() }
        if (stored.isEmpty()) return emptyMap()
        val byId = exercises.getAll().getOrNull().orEmpty().associateBy { it.id }
        // A swap whose equipment is no longer selected is dropped; unreadable preferences keep it (the server checks).
        val selected = selectedEquipment().getOrNull()
        val restored = stored.mapNotNull { entry ->
            val (session, position, id) = entry.split('|').takeIf { it.size == REPLACEMENT_PARTS }
                ?: return@mapNotNull null
            val sessionIndex = session.toIntOrNull() ?: return@mapNotNull null
            val index = position.toIntOrNull() ?: return@mapNotNull null
            val fits = block.sessions.any { it.sessionIndex == sessionIndex && index in it.exercises.indices }
            val exercise = byId[id]?.takeIf { fits && id in names && EquipmentFilter.allows(it, selected) }
                ?: return@mapNotNull null
            replaceSlotKey(sessionIndex, index) to Replacement(exercise.id, exercise.key, exercise.equipment)
        }.toMap()
        saveReplacementsState(restored, savedState.get<String>(KEY_REPLACE_SLOT))
        return restored
    }

    private fun slotOf(slot: String?): Pair<Int, Int>? {
        val parts = slot?.split('/') ?: return null
        val session = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val position = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return session to position
    }

    private fun retry() {
        clearReplacementsState()
        _uiState.value = BlockPreviewUiState.Loading
        viewModelScope.launch {
            unnamedBlock?.let { return@launch showBlock(it, restore = false) }
            val stored = blocks.currentBlock(planId)
            val block = stored.getOrNull()
            when {
                block != null -> showBlock(block, restore = true)

                stored.isFailure ->
                    _uiState.value = BlockPreviewUiState.Failed(planFailureKindOf(stored.exceptionOrNull()))

                else -> _uiState.value = BlockPreviewUiState.Failed(PlanFailureKind.Interrupted)
            }
        }
    }

    /** A renamed or new custom exercise updates the names shown; a lookup lacking a block exercise changes nothing. */
    private fun renameExercises(names: ExerciseNames) {
        _uiState.update { state ->
            if (state is BlockPreviewUiState.Preview && state.block.exerciseIds().all { names[it] != null }) {
                state.copy(exerciseNames = names.byId)
            } else {
                state
            }
        }
    }

    private fun CoachBlock.exerciseIds() = sessions.flatMap { session -> session.exercises.map { it.exerciseId } }

    private suspend fun showBlock(block: CoachBlock, restore: Boolean) {
        val resolved = exerciseNames.resolve()
        val named = resolved.getOrNull()
        if (named == null || block.exerciseIds().any { named[it] == null }) {
            // No preview with a generic name (R-54): the block is kept in memory so Retry never asks (and bills) again.
            unnamedBlock = block
            _uiState.value = BlockPreviewUiState.Failed(planFailureKindOf(resolved.exceptionOrNull()))
            return
        }
        unnamedBlock = null
        val names = named.byId
        val weeks = block.weeks().map { it.week }
        val savedWeek = savedState.get<Int>(KEY_WEEK)
        val week = savedWeek?.takeIf { it in weeks } ?: weeks.firstOrNull() ?: 1
        if (!restore) clearReplacementsState()
        val replacements = if (restore) restoredReplacements(block, names) else emptyMap()
        val slot = if (restore) savedState.get<String>(KEY_REPLACE_SLOT)?.takeIf { slotFits(block, it) } else null
        if (slot == null) savedState.remove<String>(KEY_REPLACE_SLOT)
        val preview = BlockPreviewUiState.Preview(
            block = block,
            exerciseNames = names,
            selectedWeek = week,
            reasonKey = if (restore) savedState.get<String>(KEY_REASON) else null,
            replacements = replacements,
            replaceSlot = slot,
        )
        // A key that no longer points to an exercise would open an empty sheet.
        _uiState.value = if (preview.reasonKey != null && preview.reasonExercise == null) {
            savedState.remove<String>(KEY_REASON)
            preview.copy(reasonKey = null)
        } else {
            preview
        }
        if (slot != null) loadAlternatives()
        prefetchAlternatives(block)
    }

    /**
     * Reads the alternatives of each distinct exercise of the block once, in parallel, to know which slots can be
     * swapped. A failed read leaves its exercise out of the cache, so its button stays (the sheet shows the retry).
     */
    private fun prefetchAlternatives(block: CoachBlock) {
        prefetchJob?.cancel()
        alternativeIds.clear()
        prefetched = false
        prefetchJob = viewModelScope.launch {
            val ids = block.sessions.flatMap { session -> session.exercises.map { it.exerciseId } }.distinct()
            // Without readable preferences nothing is cached: every button stays and the sheet shows the retry.
            val selection = selectedEquipment()
            val results = if (selection.isSuccess) {
                coroutineScope { ids.map { id -> async { id to exercises.alternatives(id) } }.awaitAll() }
            } else {
                emptyList()
            }
            results.forEach { (id, result) ->
                result.getOrNull()?.let { list ->
                    val allowed = EquipmentFilter.filter(list, selection.getOrNull())
                    alternativeIds[id] = allowed.map { it.exercise.id }.toSet()
                }
            }
            prefetched = true
            _uiState.update { state ->
                if (state is BlockPreviewUiState.Preview) state.withSwappable() else state
            }
        }
    }

    /** The slots with at least one alternative that is not already elsewhere in the same workout. */
    private fun BlockPreviewUiState.Preview.withSwappable(): BlockPreviewUiState.Preview {
        if (!prefetched) return copy(swappable = null)
        val slots = sessions.flatMap { session ->
            session.exercises.indices.filter { position ->
                val slotExercise = block.sessions.first { it.sessionIndex == session.sessionIndex }
                    .exercises[position]
                if (slotExercise.isCardio) return@filter true
                val original = slotExercise.exerciseId
                val candidates = alternativeIds[original] ?: return@filter true
                val others = session.exercises.filterIndexed { index, _ -> index != position }
                    .map { it.exerciseId }.toSet()
                candidates.any { it !in others }
            }.map { replaceSlotKey(session.sessionIndex, it) }
        }
        return copy(swappable = slots.toSet())
    }

    private fun slotFits(block: CoachBlock, slot: String): Boolean {
        val (session, position) = slotOf(slot) ?: return false
        return block.sessions.any { it.sessionIndex == session && position in it.exercises.indices }
    }

    private fun selectWeek(week: Int) {
        val preview = _uiState.value as? BlockPreviewUiState.Preview ?: return
        if (preview.weeks.none { it.week == week }) return
        savedState[KEY_WEEK] = week
        _uiState.value = preview.copy(selectedWeek = week)
    }

    private fun setReason(key: String?) {
        if (key == null) savedState.remove<String>(KEY_REASON) else savedState[KEY_REASON] = key
        _uiState.update { state ->
            if (state is BlockPreviewUiState.Preview) state.copy(reasonKey = key) else state
        }
    }

    private fun save() {
        val preview = _uiState.value as? BlockPreviewUiState.Preview ?: return
        if (preview.saving) return
        _uiState.value = preview.copy(saving = true, replaceSlot = null)
        savedState.remove<String>(KEY_REPLACE_SLOT)
        val edits = preview.replacements.mapNotNull { (key, value) ->
            slotOf(key)?.let { (session, position) -> BlockEdit(session, position + 1, value.exerciseId) }
        }
        viewModelScope.launch {
            val result = blocks.saveBlock(planId, edits)
            if (result.isSuccess) {
                savedState.remove<String>(KEY_REASON)
                clearReplacementsState()
                _uiState.value = BlockPreviewUiState.Closed
                effectChannel.send(BlockPreviewEffect.Saved)
            } else if ((result.exceptionOrNull() as? CoachException)?.failure == CoachFailure.InvalidReplacement) {
                // The server does not say which swap it refused, so none is kept: the coach's block is shown again.
                clearReplacementsState()
                _uiState.update { state ->
                    if (state is BlockPreviewUiState.Preview) {
                        state.copy(saving = false, replacements = emptyMap(), replaceSlot = null).withSwappable()
                    } else {
                        state
                    }
                }
                effectChannel.send(BlockPreviewEffect.ReplacementRejected)
            } else {
                // CoachFailure.Conflict (block already saved or plan no longer open) also lands here: the generic
                // save error is shown and the proposal stays, since nothing more can be saved from this screen.
                _uiState.update { state ->
                    if (state is BlockPreviewUiState.Preview) state.copy(saving = false) else state
                }
                effectChannel.send(
                    BlockPreviewEffect.ActionFailed(offline = result.appErrorOrNull() == AppError.Network),
                )
            }
        }
    }
}
