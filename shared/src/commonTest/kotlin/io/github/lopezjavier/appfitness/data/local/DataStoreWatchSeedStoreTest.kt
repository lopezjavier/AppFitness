package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.lopezjavier.appfitness.domain.model.WatchMetric
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class MemoryStore(initial: Preferences = preferencesOf()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class DataStoreWatchSeedStoreTest {
    @Test
    fun nothingSaved_isEmpty() = runTest {
        assertTrue(DataStoreWatchSeedStore(MemoryStore()).seeded().isEmpty())
    }

    @Test
    fun markedTypes_accumulateAndSurviveANewInstance() = runTest {
        val store = MemoryStore()
        DataStoreWatchSeedStore(store).markSeeded(setOf(WatchMetric.Steps))
        DataStoreWatchSeedStore(store).markSeeded(setOf(WatchMetric.Sleep))

        assertEquals(setOf(WatchMetric.Steps, WatchMetric.Sleep), DataStoreWatchSeedStore(store).seeded())
    }

    @Test
    fun unknownStoredNames_areIgnored() = runTest {
        val store = MemoryStore(
            mutablePreferencesOf(stringSetPreferencesKey("watch_seeded_metrics") to setOf("Steps", "Gone")),
        )

        assertEquals(setOf(WatchMetric.Steps), DataStoreWatchSeedStore(store).seeded())
    }
}
