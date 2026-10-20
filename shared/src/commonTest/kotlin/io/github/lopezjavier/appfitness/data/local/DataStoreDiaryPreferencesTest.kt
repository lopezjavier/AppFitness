package io.github.lopezjavier.appfitness.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.lopezjavier.appfitness.domain.model.DiaryViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class InMemoryStore(initial: Preferences = preferencesOf()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class DataStoreDiaryPreferencesTest {
    @Test
    fun defaultsToDetail() = runTest {
        assertEquals(DiaryViewMode.Detail, DataStoreDiaryPreferences(InMemoryStore()).viewMode.first())
    }

    @Test
    fun readsWhatWasSet() = runTest {
        val prefs = DataStoreDiaryPreferences(InMemoryStore())
        prefs.setViewMode(DiaryViewMode.Summary)
        assertEquals(DiaryViewMode.Summary, prefs.viewMode.first())
        prefs.setViewMode(DiaryViewMode.List)
        assertEquals(DiaryViewMode.List, prefs.viewMode.first())
    }

    @Test
    fun valueSurvivesANewInstanceOnTheSameStore() = runTest {
        val store = InMemoryStore()
        DataStoreDiaryPreferences(store).setViewMode(DiaryViewMode.List)
        assertEquals(DiaryViewMode.List, DataStoreDiaryPreferences(store).viewMode.first())
    }

    @Test
    fun unknownStoredValueFallsBackToDetail() = runTest {
        val store = InMemoryStore()
        store.edit { it[stringPreferencesKey("diary_view_mode")] = "Grid" }
        assertEquals(DiaryViewMode.Detail, DataStoreDiaryPreferences(store).viewMode.first())
    }
}
