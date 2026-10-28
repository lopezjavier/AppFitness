package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lopezjavier.appfitness.domain.model.RestSound
import io.github.lopezjavier.appfitness.domain.port.RestAlertPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** [RestAlertPreferences] over a Preferences DataStore. */
class DataStoreRestAlertPreferences(private val store: DataStore<Preferences>) : RestAlertPreferences {
    override val sound: Flow<RestSound> = store.data.map { RestSound.fromId(it[SOUND_KEY]) }

    override suspend fun setSound(sound: RestSound) {
        store.edit { it[SOUND_KEY] = sound.id }
    }

    override suspend fun explanationShown(): Boolean = store.data.first()[EXPLANATION_KEY] ?: false

    override suspend fun markExplanationShown() {
        store.edit { it[EXPLANATION_KEY] = true }
    }

    private companion object {
        val SOUND_KEY = stringPreferencesKey("rest_alert_sound")
        val EXPLANATION_KEY = booleanPreferencesKey("rest_alert_explanation_shown")
    }
}
