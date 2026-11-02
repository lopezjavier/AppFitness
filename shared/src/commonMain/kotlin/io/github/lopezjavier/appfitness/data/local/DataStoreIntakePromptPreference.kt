package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import io.github.lopezjavier.appfitness.domain.repository.IntakePromptPreference
import kotlinx.coroutines.flow.first

/** [IntakePromptPreference] over a Preferences DataStore. */
class DataStoreIntakePromptPreference(private val store: DataStore<Preferences>) : IntakePromptPreference {
    override suspend fun isAsked(): Boolean = store.data.first()[KEY] ?: false

    override suspend fun markAsked() {
        store.edit { it[KEY] = true }
    }

    private companion object {
        val KEY = booleanPreferencesKey("intake_asked")
    }
}
