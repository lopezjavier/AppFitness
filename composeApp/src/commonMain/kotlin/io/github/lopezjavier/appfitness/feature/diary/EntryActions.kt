package io.github.lopezjavier.appfitness.feature.diary

/** What a meal section lets the user do with an entry: edit it (tap) or, when [canDelete], delete it (swipe). */
internal class EntryActions(
    val canDelete: Boolean,
    val onEdit: (entryId: String) -> Unit,
    val onDelete: (entryId: String) -> Unit,
)
