package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchExercisesTest {
    private val search = SearchExercises()
    private val items =
        listOf(
            listed("1", "Press de banca con barra", MuscleGroup.Chest, Equipment.Barbell),
            listed("2", "Press inclinado con mancuernas", MuscleGroup.Chest, Equipment.Dumbbell),
            listed("3", "Dominádas", MuscleGroup.Back, Equipment.Bodyweight),
            listed("4", "Press militar", MuscleGroup.Shoulders, Equipment.Barbell),
            listed("5", "Remo con mancuerna", MuscleGroup.Back, Equipment.Dumbbell),
            listed("6", "Pañuelo de ñandú", MuscleGroup.Core, Equipment.Other),
        )

    private fun names(query: String, group: MuscleGroup? = null, equipment: Equipment? = null) =
        search(items, query, group, equipment).map { it.name }

    @Test
    fun queryAndMuscleGroup_returnTheMatchingChestExercisesOnly() {
        assertEquals(
            listOf("Press de banca con barra", "Press inclinado con mancuernas"),
            names("press", MuscleGroup.Chest),
        )
    }

    @Test
    fun equipmentFilter_keepsOnlyThatEquipment() {
        assertEquals(
            listOf("Press inclinado con mancuernas", "Remo con mancuerna"),
            names("", null, Equipment.Dumbbell),
        )
    }

    @Test
    fun accentAndCase_doNotChangeTheResult() {
        val expected = listOf("Dominádas")
        assertEquals(expected, names("dominadas"))
        assertEquals(expected, names("Dominádas"))
        assertEquals(expected, names("DOMINADAS"))
    }

    @Test
    fun tokens_matchInAnyOrder() {
        assertEquals(listOf("Press de banca con barra"), names("barra press"))
    }

    @Test
    fun emptyQuery_withFilter_listsTheWholeFilterSortedByName() {
        assertEquals(listOf("Dominádas", "Remo con mancuerna"), names("   ", MuscleGroup.Back))
    }

    @Test
    fun noMatch_givesEmptyList() {
        assertEquals(emptyList(), names("sentadilla"))
    }

    @Test
    fun bothFilters_areAnded() {
        assertEquals(listOf("Press de banca con barra"), names("", MuscleGroup.Chest, Equipment.Barbell))
        assertEquals(emptyList(), names("", MuscleGroup.Chest, Equipment.Cable))
    }

    @Test
    fun enye_isFoldedToN() {
        assertEquals(listOf("Pañuelo de ñandú"), names("panuelo nandu"))
        assertEquals(listOf("Pañuelo de ñandú"), names("pañuelo"))
    }

    @Test
    fun extraWhitespace_isCollapsed() {
        assertEquals(listOf("Press de banca con barra"), names("  press   banca "))
    }

    @Test
    fun results_areSortedByNormalisedName() {
        assertEquals(
            listOf("Dominádas", "Pañuelo de ñandú", "Press de banca con barra"),
            names("").take(3),
        )
    }
}
