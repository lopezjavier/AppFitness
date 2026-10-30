package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockWeek
import io.github.lopezjavier.appfitness.domain.model.CoachBlock

/** Key of one exercise of the block (week, workout index, position); ids stay unique across the week tabs. */
internal fun blockExerciseKey(week: Int, sessionIndex: Int, position: Int): String = "$week/$sessionIndex/$position"

sealed interface BlockPreviewUiState {
    /** Reading the stored block (R-31 skeleton). */
    data object Loading : BlockPreviewUiState

    /** No stored block: the screen asks the coach once with [BlockPreviewEvent.Request]. */
    data object Idle : BlockPreviewUiState

    /** Waiting for the model (indeterminate progress). */
    data object Requesting : BlockPreviewUiState

    /**
     * The stored [block]. [selectedWeek] is the open tab (one of the block's weeks), [reasonKey] the exercise whose
     * reason sheet is open (see [blockExerciseKey]) and [saving] is true while "Guardar bloque" is in flight.
     * [exerciseNames] maps exercise ids to display names; an id missing from it is shown as a generic exercise.
     */
    data class Preview(
        val block: CoachBlock,
        val exerciseNames: Map<String, String>,
        val selectedWeek: Int,
        val reasonKey: String? = null,
        val saving: Boolean = false,
    ) : BlockPreviewUiState {
        val weeks: List<BlockWeek> get() = block.weeks()

        /** The sessions of the selected tab. */
        val sessions: List<BlockSession> get() = weeks.firstOrNull { it.week == selectedWeek }?.sessions.orEmpty()

        /** The exercise behind the open reason sheet, or null when it is closed or no longer in the block. */
        val reasonExercise: BlockExercise?
            get() = block.sessions.firstNotNullOfOrNull { session ->
                session.exercises.withIndex().firstOrNull { (position, _) ->
                    blockExerciseKey(session.week, session.sessionIndex, position) == reasonKey
                }?.value
            }
    }

    data class Failed(val kind: PlanFailureKind) : BlockPreviewUiState

    /** The block was saved; the screen is leaving. */
    data object Closed : BlockPreviewUiState
}

sealed interface BlockPreviewEvent {
    /** Asks the coach for the block (costs money); ignored unless the state is [BlockPreviewUiState.Idle]. */
    data object Request : BlockPreviewEvent

    /** Reloads a block stored meanwhile, else asks the coach again. */
    data object Retry : BlockPreviewEvent

    data class SelectWeek(val week: Int) : BlockPreviewEvent

    /** The "?" of the exercise at [key] (see [blockExerciseKey]). */
    data class OpenReason(val key: String) : BlockPreviewEvent

    data object CloseReason : BlockPreviewEvent

    /** "Guardar bloque". Leaving the screen never saves. */
    data object Save : BlockPreviewEvent
}

sealed interface BlockPreviewEffect {
    /** The block became routines in Entreno: confirm haptic, message, navigate back. */
    data object Saved : BlockPreviewEffect

    /** Saving failed; [offline] picks the offline message. */
    data class ActionFailed(val offline: Boolean) : BlockPreviewEffect
}
