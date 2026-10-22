package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lopezjavier.appfitness.domain.repository.WeightChartPreferences
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [WeightChartPreferences] over a Preferences DataStore; the range is stored by enum name. */
class DataStoreWeightChartPreferences(private val store: DataStore<Preferences>) : WeightChartPreferences {
    override val range: Flow<WeightChartRange> = store.data.map { prefs ->
        WeightChartRange.entries.firstOrNull { it.name == prefs[KEY] } ?: WeightChartRange.Default
    }

    override suspend fun setRange(range: WeightChartRange) {
        store.edit { it[KEY] = range.name }
    }

    private companion object {
        val KEY = stringPreferencesKey("weight_chart_range")
    }
}
