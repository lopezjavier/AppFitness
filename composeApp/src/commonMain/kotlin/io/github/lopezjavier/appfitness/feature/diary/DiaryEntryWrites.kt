package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository

/**
 * The Diary's writes on existing entries: delete, and undo of the last delete. Writes need the server, so they are
 * refused with [AppError.Network] while [isOnline] is false. Outcomes go to [send] as [DiaryEffect]s.
 */
internal class DiaryEntryWrites(
    private val foodDiary: FoodDiaryRepository,
    private val isOnline: () -> Boolean,
    private val send: suspend (DiaryEffect) -> Unit,
) {
    /** The entry removed by the last [delete], kept for [undo]. */
    private var lastDeleted: FoodLogEntry? = null

    suspend fun delete(entry: FoodLogEntry) {
        if (!isOnline()) return send(DiaryEffect.WriteFailed(AppError.Network))
        val result = foodDiary.deleteEntry(entry.id)
        if (result.isSuccess) {
            lastDeleted = entry
            send(DiaryEffect.ShowUndo(UndoKind.Delete))
        } else {
            send(DiaryEffect.WriteFailed(result.appErrorOrNull()))
        }
    }

    /** Re-inserts the last deleted entry with its id and values; does nothing when there is none. */
    suspend fun undo() {
        val entry = lastDeleted ?: return
        lastDeleted = null
        val result = foodDiary.restoreEntry(entry)
        if (result.isFailure) {
            lastDeleted = entry
            send(DiaryEffect.WriteFailed(result.appErrorOrNull()))
        }
    }

    /** The ids created by the last [copy], kept for [undoCopy]. */
    private var lastCopied: List<String> = emptyList()

    /** Logs [entries] in one request; on success offers an undo that names [target]. Returns whether it worked. */
    suspend fun copy(entries: List<NewFoodEntry>, kind: UndoKind, target: CopyTarget): Boolean {
        if (entries.isEmpty()) return false
        val result = if (isOnline()) foodDiary.addEntries(entries) else Result.failure(AppException(AppError.Network))
        val created = result.getOrNull()
        if (created == null) {
            send(DiaryEffect.WriteFailed(result.appErrorOrNull()))
        } else {
            lastCopied = created.map { it.id }
            send(DiaryEffect.ShowUndo(kind, target))
        }
        return created != null
    }

    /** Deletes what the last copy created, in one request; does nothing when there is none. */
    suspend fun undoCopy() {
        val ids = lastCopied.takeIf { it.isNotEmpty() } ?: return
        lastCopied = emptyList()
        val result = foodDiary.deleteEntries(ids)
        if (result.isFailure) {
            lastCopied = ids
            send(DiaryEffect.WriteFailed(result.appErrorOrNull()))
        }
    }
}
