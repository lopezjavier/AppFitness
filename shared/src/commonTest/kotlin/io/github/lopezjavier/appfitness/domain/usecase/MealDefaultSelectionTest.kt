package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MealDefaultSelectionTest {
    private val defaults = listOf(
        MealDefault("breakfast", "A", 1),
        MealDefault("breakfast", "A", 3),
        MealDefault("breakfast", "B", 2),
        MealDefault("lunch", "C", 1),
    )

    @Test
    fun assignedDays_listsEveryDayOfThatMealOnly() {
        assertEquals(setOf(1, 2, 3), MealDefaultSelection.assignedDays(defaults, "breakfast"))
        assertEquals(setOf(1), MealDefaultSelection.assignedDays(defaults, "lunch"))
    }

    @Test
    fun conflictingDays_areThoseOfAnotherComida() {
        assertEquals(setOf(2), MealDefaultSelection.conflictingDays(defaults, "breakfast", "A"))
        assertEquals(setOf(1, 3), MealDefaultSelection.conflictingDays(defaults, "breakfast", "B"))
    }

    @Test
    fun availableDays_keepTheOwnDaysAndExcludeConflicts() {
        assertEquals(setOf(1, 3, 4, 5, 6, 7), MealDefaultSelection.availableDays(defaults, "breakfast", "A"))
    }

    @Test
    fun otherMeals_doNotConflict() {
        assertEquals(IsoWeekday.ALL.toSet(), MealDefaultSelection.availableDays(defaults, "dinner", "A"))
        assertEquals(emptySet(), MealDefaultSelection.conflictingDays(defaults, "dinner", "A"))
    }

    @Test
    fun isEveryDayAssigned_trueOnlyWhenTheMealHasAllSeven() {
        val full = IsoWeekday.ALL.map { MealDefault("breakfast", if (it < 4) "A" else "B", it) }

        assertTrue(MealDefaultSelection.isEveryDayAssigned(full, "breakfast"))
        assertFalse(MealDefaultSelection.isEveryDayAssigned(full, "lunch"))
        assertFalse(MealDefaultSelection.isEveryDayAssigned(defaults, "breakfast"))
    }

    @Test
    fun emptyList_hasNothingAssigned() {
        assertEquals(emptySet(), MealDefaultSelection.assignedDays(emptyList(), "breakfast"))
        assertFalse(MealDefaultSelection.isEveryDayAssigned(emptyList(), "breakfast"))
        assertEquals(emptyList(), MealDefaultSelection.toAssignments(emptyList()))
    }

    @Test
    fun defaultsOf_groupsByComidaWithSortedDays() {
        val unsorted = listOf(MealDefault("m", "A", 5), MealDefault("m", "B", 2), MealDefault("m", "A", 1))

        assertEquals(
            listOf(MealDefaultAssignment("m", "A", setOf(1, 5)), MealDefaultAssignment("m", "B", setOf(2))),
            MealDefaultSelection.defaultsOf(unsorted, "m"),
        )
    }

    @Test
    fun toAssignments_groupsPerMealAndComida() {
        assertEquals(
            listOf(
                MealDefaultAssignment("breakfast", "A", setOf(1, 3)),
                MealDefaultAssignment("breakfast", "B", setOf(2)),
                MealDefaultAssignment("lunch", "C", setOf(1)),
            ),
            MealDefaultSelection.toAssignments(defaults),
        )
    }
}
