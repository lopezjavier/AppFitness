package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.lopezjavier.appfitness.domain.model.WatchMetric
import io.github.lopezjavier.appfitness.domain.port.WatchSeedStore
import kotlinx.coroutines.flow.first

/** [WatchSeedStore] over a Preferences DataStore; the metrics are stored by enum name, unknown names are ignored. */
class DataStoreWatchSeedStore(private val store: DataStore<Preferences>) : WatchSeedStore {
    override suspend fun seeded(): Set<WatchMetric> = decode(store.data.first()[KEY])

    override suspend fun markSeeded(types: Set<WatchMetric>) {
        store.edit { prefs -> prefs[KEY] = (decode(prefs[KEY]) + types).map { it.name }.toSet() }
    }

    private fun decode(names: Set<String>?): Set<WatchMetric> =
        WatchMetric.entries.filter { names?.contains(it.name) == true }.toSet()

    private companion object {
        val KEY = stringSetPreferencesKey("watch_seeded_metrics")
    }
}
