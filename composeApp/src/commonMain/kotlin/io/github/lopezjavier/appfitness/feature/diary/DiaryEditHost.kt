package io.github.lopezjavier.appfitness.feature.diary

import kotlinx.datetime.LocalDate

/**
 * What the Diary needs from navigation to edit entries: [onEditEntry] opens the edit sheet of an entry of a day, and
 * the sheet's "Eliminar" comes back as [pendingDeleteEntryId] (the Diary deletes it and offers the undo), which the
 * Diary acknowledges through [onConsumeDelete].
 */
class DiaryEditHost(
    val onEditEntry: (entryId: String, date: LocalDate) -> Unit,
    val pendingDeleteEntryId: String?,
    val onConsumeDelete: () -> Unit,
)
