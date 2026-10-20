package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.model.DiaryViewMode

/**
 * What a meal section does with its entries: how they are shown and what the user can do to them. In selection mode
 * ([selecting]) a tap toggles the entry and editing and swiping are off; [selectedIds] are the checked entries.
 * Meal-level actions take the slot id.
 */
@Suppress("LongParameterList") // One callback per action the section offers.
internal class EntryActions(
    val viewMode: DiaryViewMode,
    val canDelete: Boolean,
    val selecting: Boolean,
    val selectedIds: Set<String>,
    val canRepeat: Map<String, Boolean>,
    val onEdit: (entryId: String) -> Unit,
    val onDelete: (entryId: String) -> Unit,
    val onLongPress: (entryId: String) -> Unit,
    val onToggleSelected: (entryId: String) -> Unit,
    val onSelectMeal: (slotId: String) -> Unit,
    val onCopyMeal: (slotId: String) -> Unit,
    val onRepeat: (slotId: String) -> Unit,
)
