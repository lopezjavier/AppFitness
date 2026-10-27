package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MealSlotBounds
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft

/** Why a meal label is rejected. */
enum class MealSlotError { Blank, TooLong, Duplicate }

/**
 * Outcome of [ValidateMealSlots]: [slots] are the drafts with trimmed labels, [errors] the rejected labels by list
 * index.
 */
data class MealSlotsValidation(val slots: List<MealSlotDraft>, val errors: Map<Int, MealSlotError>) {
    val isValid: Boolean get() = slots.isNotEmpty() && errors.isEmpty()
}

/**
 * Checks the meals editor draft: labels of 1 to 50 characters after trimming, unique per user (trim + lowercase, the
 * same rule as the server index and the Name sheet) and at least one meal. The later repeat is the duplicate.
 */
class ValidateMealSlots {
    operator fun invoke(drafts: List<MealSlotDraft>): MealSlotsValidation {
        val trimmed = drafts.map { it.copy(label = it.label.trim()) }
        val seen = mutableSetOf<String>()
        val errors = buildMap {
            trimmed.forEachIndexed { index, slot ->
                when {
                    slot.label.isEmpty() -> put(index, MealSlotError.Blank)
                    slot.label.length > MealSlotBounds.LABEL_MAX_LENGTH -> put(index, MealSlotError.TooLong)
                    !seen.add(key(slot.label)) -> put(index, MealSlotError.Duplicate)
                }
            }
        }
        return MealSlotsValidation(trimmed, errors)
    }

    companion object {
        private fun key(label: String) = label.trim().lowercase()

        /** True when [candidate] equals (trim + lowercase) any of [existing]. */
        fun isDuplicate(candidate: String, existing: Collection<String>): Boolean =
            existing.any { key(it) == key(candidate) }
    }
}
