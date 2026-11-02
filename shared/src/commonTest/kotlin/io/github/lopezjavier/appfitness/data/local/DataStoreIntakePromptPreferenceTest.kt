package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class IntakeMemoryStore : DataStore<Preferences> {
    private val state = MutableStateFlow(preferencesOf())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class DataStoreIntakePromptPreferenceTest {
    @Test
    fun nothingStored_isNotAsked() = runTest {
        assertEquals(false, DataStoreIntakePromptPreference(IntakeMemoryStore()).isAsked())
    }

    @Test
    fun markAsked_survivesANewInstanceOverTheSameStore() = runTest {
        val store = IntakeMemoryStore()
        DataStoreIntakePromptPreference(store).markAsked()

        assertEquals(true, DataStoreIntakePromptPreference(store).isAsked())
    }
}
