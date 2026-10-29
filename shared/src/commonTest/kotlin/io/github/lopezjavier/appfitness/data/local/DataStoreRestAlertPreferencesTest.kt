package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class MemoryRestStore(initial: Preferences = preferencesOf()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class DataStoreRestAlertPreferencesTest {
    @Test
    fun explanation_isNotShownByDefault() = runTest {
        assertFalse(DataStoreRestAlertPreferences(MemoryRestStore()).explanationShown())
    }

    @Test
    fun explanationMarked_persistsAcrossInstances() = runTest {
        val store = MemoryRestStore()
        DataStoreRestAlertPreferences(store).markExplanationShown()

        assertTrue(DataStoreRestAlertPreferences(store).explanationShown())
    }

    @Test
    fun aLeftoverSoundKey_isIgnoredAndKeptHarmless() = runTest {
        val store = MemoryRestStore(mutablePreferencesOf(stringPreferencesKey("rest_alert_sound") to "double_beep"))
        val prefs = DataStoreRestAlertPreferences(store)

        assertFalse(prefs.explanationShown())
        prefs.markExplanationShown()

        assertTrue(prefs.explanationShown())
    }
}
