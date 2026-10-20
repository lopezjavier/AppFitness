package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MealSlotBounds
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft

/** Why a meal label is rejected. */
enum class MealSlotError { Blank, TooLong }

/**
 * Outcome of [ValidateMealSlots]: [slots] are the drafts with trimmed labels, [errors] the rejected labels by list
 * index and [noneEnabled] is set when no meal is enabled (the Diary would be empty).
 */
data class MealSlotsValidation(
    val slots: List<MealSlotDraft>,
    val errors: Map<Int, MealSlotError>,
    val noneEnabled: Boolean,
) {
    val isValid: Boolean get() = slots.isNotEmpty() && errors.isEmpty() && !noneEnabled
}

/** Checks the meals editor draft: labels of 1 to 50 characters after trimming and at least one enabled meal. */
class ValidateMealSlots {
    operator fun invoke(drafts: List<MealSlotDraft>): MealSlotsValidation {
        val trimmed = drafts.map { it.copy(label = it.label.trim()) }
        val errors = buildMap {
            trimmed.forEachIndexed { index, slot ->
                when {
                    slot.label.isEmpty() -> put(index, MealSlotError.Blank)
                    slot.label.length > MealSlotBounds.LABEL_MAX_LENGTH -> put(index, MealSlotError.TooLong)
                }
            }
        }
        return MealSlotsValidation(trimmed, errors, noneEnabled = trimmed.none { it.enabled })
    }
}
