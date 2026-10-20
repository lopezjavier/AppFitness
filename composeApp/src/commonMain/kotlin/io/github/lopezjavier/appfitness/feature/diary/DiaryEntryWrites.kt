package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.repository.FoodDiaryRepository

/**
 * The Diary's writes on existing entries: delete. Writes need the server, so they are refused with
 * [AppError.Network] while [isOnline] is false. A success sends nothing (the entry just leaves the list); a failure
 * goes to [send] as [DiaryEffect.WriteFailed].
 */
internal class DiaryEntryWrites(
    private val foodDiary: FoodDiaryRepository,
    private val isOnline: () -> Boolean,
    private val send: suspend (DiaryEffect) -> Unit,
) {
    suspend fun delete(entry: FoodLogEntry) {
        if (!isOnline()) return send(DiaryEffect.WriteFailed(AppError.Network))
        val result = foodDiary.deleteEntry(entry.id)
        if (result.isFailure) send(DiaryEffect.WriteFailed(result.appErrorOrNull()))
    }
}
