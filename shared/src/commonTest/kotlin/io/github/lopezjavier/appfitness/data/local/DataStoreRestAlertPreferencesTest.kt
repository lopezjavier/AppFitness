package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lopezjavier.appfitness.domain.model.RestSound
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun nothingSaved_soundIsSystem() = runTest {
        assertEquals(RestSound.SYSTEM, DataStoreRestAlertPreferences(MemoryRestStore()).sound.first())
    }

    @Test
    fun everySound_roundTripsThroughAnotherInstance() = runTest {
        RestSound.entries.forEach { sound ->
            val store = MemoryRestStore()
            DataStoreRestAlertPreferences(store).setSound(sound)

            assertEquals(sound, DataStoreRestAlertPreferences(store).sound.first())
        }
    }

    @Test
    fun unknownStoredId_fallsBackToSystem() = runTest {
        val store = MemoryRestStore(mutablePreferencesOf(stringPreferencesKey("rest_alert_sound") to "gong"))

        assertEquals(RestSound.SYSTEM, DataStoreRestAlertPreferences(store).sound.first())
    }

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
    fun soundAndExplanation_doNotOverwriteEachOther() = runTest {
        val prefs = DataStoreRestAlertPreferences(MemoryRestStore())
        prefs.setSound(RestSound.CHIME)
        prefs.markExplanationShown()

        assertEquals(RestSound.CHIME, prefs.sound.first())
        assertTrue(prefs.explanationShown())
    }

    @Test
    fun restSoundIds_areUniqueAndNullGivesDefault() {
        assertEquals(RestSound.entries.size, RestSound.entries.map { it.id }.toSet().size)
        assertEquals(RestSound.SYSTEM, RestSound.fromId(null))
    }
}
