package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import io.github.lopezjavier.appfitness.domain.port.RestAlertPreferences
import kotlinx.coroutines.flow.first

/** [RestAlertPreferences] over a Preferences DataStore. */
class DataStoreRestAlertPreferences(private val store: DataStore<Preferences>) : RestAlertPreferences {
    override suspend fun explanationShown(): Boolean = store.data.first()[EXPLANATION_KEY] ?: false

    override suspend fun markExplanationShown() {
        store.edit { it[EXPLANATION_KEY] = true }
    }

    private companion object {
        val EXPLANATION_KEY = booleanPreferencesKey("rest_alert_explanation_shown")
    }
}
