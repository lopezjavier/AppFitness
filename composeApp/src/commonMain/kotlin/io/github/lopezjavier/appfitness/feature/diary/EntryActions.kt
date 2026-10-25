package io.github.lopezjavier.appfitness.feature.diary

/**
 * What a meal section lets the user do with an entry: edit it (tap) or, when [canDelete], delete it (swipe); and
 * expand or collapse a comida group (the ids in [expandedGroupIds] are open).
 */
internal class EntryActions(
    val canDelete: Boolean,
    val onEdit: (entryId: String) -> Unit,
    val onDelete: (entryId: String) -> Unit,
    val expandedGroupIds: Set<String> = emptySet(),
    val onToggleGroup: (groupId: String) -> Unit = {},
)
