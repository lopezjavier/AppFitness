package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class SortRoutinesByNameTest {
    private fun names(vararg names: String) = names.mapIndexed { i, name -> RoutineSummary("id$i", name, 1) }

    private fun sorted(vararg names: String) = sortRoutinesByName(names(*names)).map { it.name }

    @Test
    fun sortsAlphabetically_fromAnyInputOrder() {
        val expected = listOf("Abdominales", "brazos", "Énfasis pierna", "Torso fuerza")

        assertEquals(expected, sorted("Torso fuerza", "Énfasis pierna", "brazos", "Abdominales"))
        assertEquals(expected, sorted("brazos", "Abdominales", "Torso fuerza", "Énfasis pierna"))
    }

    @Test
    fun ignoresAccents() {
        assertEquals(listOf("Ebano", "Élite"), sorted("Élite", "Ebano"))
        assertEquals(listOf("Élite", "Fuerza"), sorted("Fuerza", "Élite"))
    }

    @Test
    fun ignoresCase() {
        assertEquals(listOf("A", "b"), sorted("b", "A"))
    }

    @Test
    fun equalNames_keepTheirOrder() {
        val result = sortRoutinesByName(listOf(RoutineSummary("1", "torso", 1), RoutineSummary("2", "Torso", 1)))

        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun emptyList_staysEmpty() {
        assertEquals(emptyList(), sortRoutinesByName(emptyList()))
    }
}
