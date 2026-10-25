package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealRow
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupMealEntriesTest {
    private val group = GroupMealEntries()

    private fun entry(
        id: String,
        kcal: Double = 100.0,
        slot: String = "breakfast",
        groupId: String? = null,
        groupName: String? = groupId?.let { "Comida $it" },
    ) = FoodLogEntry(
        id = id,
        logDate = LocalDate(2026, 11, 12),
        mealSlotId = slot,
        foodId = "f-$id",
        foodName = id,
        amount = 100.0,
        portionLabel = null,
        baseUnit = "g",
        kcal = kcal,
        proteinG = 1.0,
        carbsG = 2.0,
        fatG = 0.5,
        groupId = groupId,
        groupName = groupName,
    )

    @Test
    fun noGroups_everyEntryIsSingleInOrder() {
        val rows = group(listOf(entry("a"), entry("b")))

        assertEquals(listOf("a", "b"), rows.map { (it as MealRow.Single).entry.id })
    }

    @Test
    fun emptyList_givesNoRows() {
        assertTrue(group(emptyList()).isEmpty())
    }

    @Test
    fun aGroupCollapsesItsEntriesAndSumsTheTotalsWithTenthsPrecision() {
        val rows = group(
            listOf(
                entry("oats", kcal = 150.0, groupId = "g1", groupName = "Avena con plátano"),
                entry("banana", kcal = 105.0, groupId = "g1", groupName = "Avena con plátano"),
            ),
        )

        val row = rows.single() as MealRow.Group
        assertEquals("Avena con plátano", row.name)
        assertEquals(listOf("oats", "banana"), row.entries.map { it.id })
        assertEquals(MealTotals(255.0, 2.0, 4.0, 1.0), row.totals)
    }

    @Test
    fun theGroupSitsAtItsFirstEntryAndALaterLooseEntryStaysSingle() {
        val rows = group(
            listOf(
                entry("before"),
                entry("oats", groupId = "g1"),
                entry("coffee"),
                entry("banana", groupId = "g1"),
                entry("later"),
            ),
        )

        assertEquals(
            listOf("before", "g1", "coffee", "later"),
            rows.map { if (it is MealRow.Group) it.groupId else (it as MealRow.Single).entry.id },
        )
    }

    @Test
    fun sameGroupIdInDifferentSlotsFormsTwoGroups() {
        val rows = group(
            listOf(
                entry("a", slot = "breakfast", groupId = "g1"),
                entry("b", slot = "lunch", groupId = "g1"),
            ),
        )

        assertEquals(2, rows.filterIsInstance<MealRow.Group>().size)
    }

    @Test
    fun twoApplicationsOfTheSameComidaAreSeparateGroups() {
        val rows = group(
            listOf(
                entry("a", groupId = "g1", groupName = "Avena"),
                entry("b", groupId = "g2", groupName = "Avena"),
            ),
        )

        assertEquals(listOf("g1", "g2"), rows.map { (it as MealRow.Group).groupId })
    }

    @Test
    fun oneRemainingEntryIsStillAGroup() {
        val rows = group(listOf(entry("a", groupId = "g1")))

        assertEquals(1, (rows.single() as MealRow.Group).entries.size)
    }

    @Test
    fun groupedTotalsEqualTheFlatTotals() {
        val entries = listOf(entry("a", 150.0, groupId = "g1"), entry("b", 105.0, groupId = "g1"), entry("c", 40.5))

        val rows = group(entries)

        val rowsKcal = rows.sumOf { if (it is MealRow.Group) it.totals.kcal else (it as MealRow.Single).entry.kcal }
        assertEquals(DiaryTotals.sum(entries).kcal, rowsKcal)
    }
}
