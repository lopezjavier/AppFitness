package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortFollowUpRepository
import io.github.lopezjavier.appfitness.domain.usecase.DeclineDiscomfortSwapUseCase
import io.github.lopezjavier.appfitness.domain.usecase.DismissDiscomfortNoteUseCase
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.LoadDiscomfortFollowUpsUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadSwapCandidatesUseCase
import io.github.lopezjavier.appfitness.domain.usecase.SwapFlaggedExerciseUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val KEY_SWAP_PICKER = "swap_picker_exercise"

/**
 * The discomfort follow-up part of the Trainer tab (US-108): the swap offer, its picker and the one-time health note.
 * The server decides what is offered and shown; this class only maps it to UI state, resolves the exercise names
 * (R-54) and runs the user's taps. Only the id of the exercise whose picker is open is saved (ADR-40): follow-ups,
 * busy flags and candidates are reloaded, never saved.
 */
@Suppress("LongParameterList", "TooManyFunctions") // Collaborators of the view model, one small handler per tap.
internal class TrainerDiscomfortController(
    repository: DiscomfortFollowUpRepository,
    private val names: ExerciseNameResolver?,
    private val state: MutableStateFlow<TrainerUiState>,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val send: suspend (TrainerEffect) -> Unit,
    private val reloadHub: suspend () -> Unit,
) {
    private val loadFollowUps = LoadDiscomfortFollowUpsUseCase(repository)
    private val loadCandidates = LoadSwapCandidatesUseCase(repository)
    private val swapFlagged = SwapFlaggedExerciseUseCase(repository)
    private val declineSwap = DeclineDiscomfortSwapUseCase(repository)
    private val dismissNote = DismissDiscomfortNoteUseCase(repository)

    private fun content(): TrainerUiState.Content? = state.value as? TrainerUiState.Content

    private inline fun updateContent(crossinline change: (TrainerUiState.Content) -> TrainerUiState.Content) {
        state.update { if (it is TrainerUiState.Content) change(it) else it }
    }

    /** Follow-ups to show; a failed read hides them, and an exercise without a name is skipped (never an id). */
    @Suppress("ReturnCount") // Guards: nothing to show, or no names to show it with.
    suspend fun followUps(): List<DiscomfortFollowUpUi> {
        val rows = loadFollowUps().getOrNull()?.filter { it.offerSwap || it.showNote }.orEmpty()
        if (rows.isEmpty()) return emptyList()
        val byId = names?.resolve()?.getOrNull()?.byId ?: return emptyList()
        return rows.mapNotNull { row ->
            byId[row.exerciseId]?.let {
                DiscomfortFollowUpUi(row.exerciseId, it, row.offerSwap, row.showNote, row.noteReason)
            }
        }
    }

    /** The picker saved before recreation, rebuilt from its candidates; null (forgotten) if it no longer applies. */
    suspend fun restoredPicker(followUps: List<DiscomfortFollowUpUi>): SwapPickerUi? {
        val id = savedState.get<String>(KEY_SWAP_PICKER) ?: return null
        val flagged = followUps.firstOrNull { it.exerciseId == id && it.offerSwap }
        val candidates = flagged?.let { candidatesOf(id).getOrNull() }
        if (flagged == null || candidates == null) savedState.remove<String>(KEY_SWAP_PICKER)
        return candidates?.let { SwapPickerUi(id, flagged.exerciseName, it, loading = false) }
    }

    private suspend fun candidatesOf(exerciseId: String): Result<List<SwapCandidateUi>> = runCatching {
        val rows = loadCandidates(exerciseId).getOrThrow()
        val byId = names?.resolve()?.getOrThrow()?.byId.orEmpty()
        rows.mapNotNull { row -> byId[row.exerciseId]?.let { SwapCandidateUi(row.exerciseId, it, row.isFavorite) } }
    }

    @Suppress("ReturnCount") // Guards: no content, a picker already open, or no such offer.
    fun openPicker(exerciseId: String) {
        val content = content() ?: return
        if (content.swapPicker != null || content.busyFollowUpExerciseId != null) return
        val flagged = content.discomfortFollowUps.firstOrNull { it.exerciseId == exerciseId && it.offerSwap } ?: return
        savedState[KEY_SWAP_PICKER] = exerciseId
        updateContent { it.copy(swapPicker = SwapPickerUi(exerciseId, flagged.exerciseName)) }
        scope.launch {
            val result = candidatesOf(exerciseId)
            val list = result.getOrNull()
            if (list != null) {
                updateContent {
                    val picker = it.swapPicker
                    if (picker?.exerciseId == exerciseId) {
                        it.copy(swapPicker = picker.copy(candidates = list, loading = false))
                    } else {
                        it
                    }
                }
            } else {
                if (content()?.swapPicker?.exerciseId == exerciseId) closePicker()
                send(TrainerEffect.SwapFailed(offline = result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    fun dismissPicker() {
        if (content()?.busyFollowUpExerciseId != null) return
        closePicker()
    }

    private fun closePicker() {
        savedState.remove<String>(KEY_SWAP_PICKER)
        updateContent { it.copy(swapPicker = null) }
    }

    @Suppress("ReturnCount") // Guards: no loaded picker, a swap in flight, or an unknown candidate.
    fun pick(newExerciseId: String) {
        val content = content() ?: return
        val picker = content.swapPicker?.takeIf { !it.loading } ?: return
        if (content.busyFollowUpExerciseId != null || picker.candidates.none { it.exerciseId == newExerciseId }) return
        updateContent { it.copy(busyFollowUpExerciseId = picker.exerciseId) }
        scope.launch {
            val result = swapFlagged(picker.exerciseId, newExerciseId)
            if (result.isSuccess) {
                // The fresh hub has no picker and no busy flag, and the offer is gone: the exercise left the block.
                savedState.remove<String>(KEY_SWAP_PICKER)
                reloadHub()
                send(TrainerEffect.SwapApplied)
            } else {
                updateContent { it.copy(busyFollowUpExerciseId = null) }
                send(TrainerEffect.SwapFailed(offline = result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    fun decline(exerciseId: String) = act(
        exerciseId,
        change = { it.copy(offerSwap = false).takeIf { row -> row.showNote } },
        call = { declineSwap(exerciseId) },
    )

    fun dismissNote(exerciseId: String) = act(
        exerciseId,
        change = { it.copy(showNote = false, noteReason = null).takeIf { row -> row.offerSwap } },
        call = { dismissNote.invoke(exerciseId) },
    )

    /** Optimistic: the item changes at once and comes back, with a message, when the server refuses. */
    private fun act(
        exerciseId: String,
        change: (DiscomfortFollowUpUi) -> DiscomfortFollowUpUi?,
        call: suspend () -> Result<Unit>,
    ) {
        val before =
            content()?.discomfortFollowUps?.takeIf { list -> list.any { it.exerciseId == exerciseId } } ?: return
        updateContent { c ->
            c.copy(
                discomfortFollowUps = c.discomfortFollowUps.mapNotNull {
                    if (it.exerciseId ==
                        exerciseId
                    ) {
                        change(it)
                    } else {
                        it
                    }
                },
            )
        }
        scope.launch {
            val result = call()
            if (result.isFailure) {
                updateContent { it.copy(discomfortFollowUps = before) }
                send(TrainerEffect.FollowUpActionFailed(offline = result.appErrorOrNull() == AppError.Network))
            }
        }
    }
}
