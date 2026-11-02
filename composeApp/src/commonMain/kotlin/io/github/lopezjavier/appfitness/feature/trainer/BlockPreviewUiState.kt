package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.BlockSession
import io.github.lopezjavier.appfitness.domain.model.BlockWeek
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.feature.exercises.SimilarExerciseRow

/** Key of one exercise of the block (week, workout index, position); ids stay unique across the week tabs. */
internal fun blockExerciseKey(week: Int, sessionIndex: Int, position: Int): String = "$week/$sessionIndex/$position"

/** Key of one replaceable slot: the same position of workout [sessionIndex] in every week (position is 0-based). */
internal fun replaceSlotKey(sessionIndex: Int, position: Int): String = "$sessionIndex/$position"

/** A pending swap shown over the coach's exercise until "Guardar bloque"; [key] and [equipment] feed the row. */
data class Replacement(val exerciseId: String, val key: String?, val equipment: Equipment)

/** Alternatives offered for the slot whose picker is open. */
sealed interface AlternativesState {
    data object Loading : AlternativesState

    /** The exercise has no alternative that fits the workout. */
    data object Empty : AlternativesState

    data class Content(val rows: List<SimilarExerciseRow>) : AlternativesState

    data object Offline : AlternativesState

    data object Error : AlternativesState
}

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
     * [replacements] are the pending swaps by [replaceSlotKey]; [weeks] and [sessions] already show them (same sets,
     * reps, rest and reason). [replaceSlot] is the slot whose picker is open, with its [alternatives]. [swappable]
     * holds the slots (see [replaceSlotKey]) that have at least one usable alternative; null while unknown.
     */
    data class Preview(
        val block: CoachBlock,
        val exerciseNames: Map<String, String>,
        val selectedWeek: Int,
        val reasonKey: String? = null,
        val saving: Boolean = false,
        val replacements: Map<String, Replacement> = emptyMap(),
        val replaceSlot: String? = null,
        val alternatives: AlternativesState = AlternativesState.Loading,
        val swappable: Set<String>? = null,
        /** The block was requested with "Tengo poco tiempo": the screen shows the Mínimo note. */
        val minimal: Boolean = false,
    ) : BlockPreviewUiState {
        /**
         * Whether the swap button is drawn for the slot: hidden only when the alternatives are known and none fits.
         * While they load ([swappable] null) or when the read failed, the button stays so the sheet can retry.
         */
        fun canSwap(sessionIndex: Int, position: Int): Boolean =
            swappable?.contains(replaceSlotKey(sessionIndex, position)) ?: true

        val weeks: List<BlockWeek>
            get() = block.weeks().map { week -> week.copy(sessions = week.sessions.map { it.withReplacements() }) }

        /** The pending swap of the exercise at [position] of workout [sessionIndex], or null when it is the coach's. */
        fun replacementAt(sessionIndex: Int, position: Int): Replacement? =
            replacements[replaceSlotKey(sessionIndex, position)]

        /**
         * True while the open picker is for a cardio slot: the sheet shows the "same minutes with any cardio" note,
         * no similarity percent and no "Similares" grouping.
         */
        val isCardioSwap: Boolean
            get() {
                val slot = replaceSlot?.split('/') ?: return false
                val session = slot.getOrNull(0)?.toIntOrNull() ?: return false
                val position = slot.getOrNull(1)?.toIntOrNull() ?: return false
                return block.sessions.firstOrNull { it.sessionIndex == session }
                    ?.exercises?.getOrNull(position)?.isCardio == true
            }

        fun isChanged(sessionIndex: Int, position: Int): Boolean = replacementAt(sessionIndex, position) != null

        private fun BlockSession.withReplacements(): BlockSession = copy(
            exercises = exercises.mapIndexed { position, exercise ->
                replacementAt(sessionIndex, position)?.let {
                    exercise.copy(exerciseId = it.exerciseId, exerciseKey = it.key)
                }
                    ?: exercise
            },
        )

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

    /** "Cambiar ejercicio" on the exercise at [position] (0-based) of workout [sessionIndex]: opens the picker. */
    data class OpenReplace(val sessionIndex: Int, val position: Int) : BlockPreviewEvent

    /** Reads the alternatives of the open slot again (first load, "Reintentar"). */
    data object LoadAlternatives : BlockPreviewEvent

    /** Applies an alternative to the open slot; local until saved, and the picker closes. */
    data class PickReplacement(val exerciseId: String) : BlockPreviewEvent

    /** "Volver al ejercicio original": drops the swap of the open slot and closes the picker. */
    data object ClearReplacement : BlockPreviewEvent

    /** Dismisses the picker without changes. */
    data object CloseReplace : BlockPreviewEvent
}

sealed interface BlockPreviewEffect {
    /** The block became routines in Entreno: confirm haptic, message, navigate back. */
    data object Saved : BlockPreviewEffect

    /** Saving failed; [offline] picks the offline message. */
    data class ActionFailed(val offline: Boolean) : BlockPreviewEffect

    /** The server refused a swap; the pending swaps were dropped and the screen shows the coach's block again. */
    data object ReplacementRejected : BlockPreviewEffect
}
