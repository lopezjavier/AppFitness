package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** In-memory [DataStore]: enough to exercise the keys without touching the file system. */
private class InMemoryPreferencesStore(initial: Preferences = preferencesOf()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

class DataStoreWeightChartPreferencesTest {
    @Test
    fun nothingSaved_defaultsToThreeMonths() = runTest {
        val prefs = DataStoreWeightChartPreferences(InMemoryPreferencesStore())

        assertEquals(WeightChartRange.Quarter, prefs.range.first())
    }

    @Test
    fun savedRange_isReadBackByAnotherInstance() = runTest {
        val store = InMemoryPreferencesStore()
        DataStoreWeightChartPreferences(store).setRange(WeightChartRange.Month)

        assertEquals(WeightChartRange.Month, DataStoreWeightChartPreferences(store).range.first())
    }

    @Test
    fun unknownStoredValue_fallsBackToDefault() = runTest {
        val store = InMemoryPreferencesStore(
            mutablePreferencesOf(stringPreferencesKey("weight_chart_range") to "Decade"),
        )

        assertEquals(WeightChartRange.Quarter, DataStoreWeightChartPreferences(store).range.first())
    }
}
