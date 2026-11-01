package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.model.CardioMinutes

private const val KEY_OPEN = "cardio_edit_open"
private const val KEY_DRAFT = "cardio_edit_draft"

/**
 * The open cardio-minutes sheet: [draft] is what the stepper shows, [saved] what the plan holds now. "Guardar" is
 * enabled only when the draft differs from the saved value and no save is in flight ([saving]).
 */
data class CardioEdit(val draft: Int, val saved: Int, val saving: Boolean = false) {
    /** The live note under the stepper: the draft is under the WHO minimum (the user may keep it). */
    val belowRecommended: Boolean get() = CardioMinutes.isBelowRecommended(draft)

    val canSave: Boolean get() = draft != saved && !saving
}

/**
 * Sheet state shared by the proposal and the "Tu plan" card view models. The open flag and the draft (an Int, never
 * text) live in the [SavedStateHandle], so the sheet and an unsaved draft survive process death (ADR-40).
 */
internal class CardioEditState(private val savedState: SavedStateHandle) {
    private val isOpen: Boolean get() = savedState.get<Boolean>(KEY_OPEN) == true

    fun open(saved: Int) {
        savedState[KEY_OPEN] = true
        savedState[KEY_DRAFT] = saved
    }

    fun change(minutes: Int) {
        if (isOpen) savedState[KEY_DRAFT] = CardioMinutes.clamp(minutes)
    }

    fun close() {
        savedState.remove<Boolean>(KEY_OPEN)
        savedState.remove<Int>(KEY_DRAFT)
    }

    /** The sheet to show for a plan whose cardio is [saved] (null when the plan has no activity), or null if closed. */
    fun current(saved: Int?, saving: Boolean = false): CardioEdit? {
        if (!isOpen || saved == null) return null
        return CardioEdit(draft = savedState.get<Int>(KEY_DRAFT) ?: saved, saved = saved, saving = saving)
    }
}
