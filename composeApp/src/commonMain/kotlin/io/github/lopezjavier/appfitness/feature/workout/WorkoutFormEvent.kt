package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.model.SetNoteKind
import io.github.lopezjavier.appfitness.domain.model.SetType

/**
 * The edits shared by the edit and the past-workout forms. Indices are 0-based positions in the form: [index] is the
 * exercise, [setIndex] the set within it. Nothing is written until the screen's `Save` event.
 */
sealed interface WorkoutFormEvent {
    data class NameChanged(val text: String) : WorkoutFormEvent

    data class DescriptionChanged(val text: String) : WorkoutFormEvent

    data class GymChanged(val text: String) : WorkoutFormEvent

    /** Duration in minutes as typed. */
    data class DurationChanged(val text: String) : WorkoutFormEvent

    /** Appends the exercise (picked in the exercise picker) with one empty set. */
    data class AddExercise(val exerciseId: String) : WorkoutFormEvent

    data class RemoveExercise(val index: Int) : WorkoutFormEvent

    data class MoveExercise(val from: Int, val to: Int) : WorkoutFormEvent

    data class RestChanged(val index: Int, val seconds: Int) : WorkoutFormEvent

    /** Adds a copy of the last set (every member of a superset gets one). */
    data class AddSet(val index: Int) : WorkoutFormEvent

    /** Removes the set at once (no undo, ADR-45); the last set of an exercise cannot be removed. */
    data class RemoveSet(val index: Int, val setIndex: Int) : WorkoutFormEvent

    data class ValueChanged(val index: Int, val setIndex: Int, val field: SetField, val text: String) :
        WorkoutFormEvent

    data class TypeChanged(val index: Int, val setIndex: Int, val type: SetType) : WorkoutFormEvent

    /** A whole number 6-10, or null to clear it. */
    data class RpeChanged(val index: Int, val setIndex: Int, val rpe: Double?) : WorkoutFormEvent

    /**
     * "Guardar" in the set note sheet: the chosen option (null removes the note) and its optional trimmed text,
     * required for "Otro". An invalid pair is ignored.
     */
    data class NoteChanged(val index: Int, val setIndex: Int, val kind: SetNoteKind?, val text: String) :
        WorkoutFormEvent
}
