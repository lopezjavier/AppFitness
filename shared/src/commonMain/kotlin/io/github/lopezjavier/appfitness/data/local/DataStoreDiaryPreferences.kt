package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lopezjavier.appfitness.domain.model.DiaryViewMode
import io.github.lopezjavier.appfitness.domain.repository.DiaryPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val ViewModeKey = stringPreferencesKey("diary_view_mode")

/** [DiaryPreferences] on a DataStore; an unknown stored value falls back to [DiaryViewMode.Detail]. */
class DataStoreDiaryPreferences(private val store: DataStore<Preferences>) : DiaryPreferences {
    override val viewMode: Flow<DiaryViewMode> = store.data.map { prefs ->
        DiaryViewMode.entries.firstOrNull { it.name == prefs[ViewModeKey] } ?: DiaryViewMode.Detail
    }

    override suspend fun setViewMode(mode: DiaryViewMode) {
        store.edit { it[ViewModeKey] = mode.name }
    }
}
