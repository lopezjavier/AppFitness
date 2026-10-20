package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.DiaryViewMode
import kotlinx.coroutines.flow.Flow

/** Local (device) preferences of the Diary. */
interface DiaryPreferences {
    /** The chosen view mode; [DiaryViewMode.Detail] until the user picks another. */
    val viewMode: Flow<DiaryViewMode>

    suspend fun setViewMode(mode: DiaryViewMode)
}
