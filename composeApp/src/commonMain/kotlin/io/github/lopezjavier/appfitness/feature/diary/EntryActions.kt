package io.github.lopezjavier.appfitness.feature.diary

/**
 * What a meal section lets the user do with an entry: edit it (tap) or, when [canDelete], delete it (swipe); and
 * expand or collapse a comida group (the ids in [expandedGroupIds] are open). A collapsed group is swiped as one
 * food and [onDeleteGroup] removes all of it (R-71); expanded, only its ingredients swipe.
 */
internal class EntryActions(
    val canDelete: Boolean,
    val onEdit: (entryId: String) -> Unit,
    val onDelete: (entryId: String) -> Unit,
    val expandedGroupIds: Set<String> = emptySet(),
    val onToggleGroup: (groupId: String) -> Unit = {},
    val onDeleteGroup: (groupId: String) -> Unit = {},
)
