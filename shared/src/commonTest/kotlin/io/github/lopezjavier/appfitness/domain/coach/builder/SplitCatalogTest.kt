package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType.FullBody
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType.Lower
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType.Pull
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType.Push
import io.github.lopezjavier.appfitness.domain.coach.builder.RoutineType.Upper
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitCatalogTest {
    private val expected: Map<Int, List<List<RoutineType>>> = mapOf(
        1 to listOf(listOf(FullBody)),
        2 to listOf(listOf(FullBody, FullBody), listOf(Upper, Lower), listOf(Push, Pull)),
        3 to listOf(
            listOf(FullBody, FullBody, FullBody),
            listOf(Upper, Lower, FullBody),
            listOf(Push, Pull, FullBody),
        ),
        4 to listOf(
            listOf(Upper, Lower, Upper, Lower),
            listOf(Push, Pull, Push, Pull),
            listOf(FullBody, FullBody, FullBody, FullBody),
        ),
        5 to listOf(
            listOf(Upper, Lower, Upper, Lower, FullBody),
            listOf(Push, Pull, Push, Pull, FullBody),
            List(5) { FullBody },
        ),
        6 to listOf(listOf(Upper, Lower, Upper, Lower, Upper, Lower), listOf(Push, Pull, Push, Pull, Push, Pull)),
    )

    @Test
    fun everyDayCount_listsTheTableSplitsRecommendedFirst() {
        for ((days, splits) in expected) {
            val actual = SplitCatalog.splitsFor(days)
            assertEquals(splits, actual.map { it.types }, "days $days")
            assertTrue(actual.first().recommended)
            assertTrue(actual.drop(1).none { it.recommended })
            assertTrue(actual.first().reason != null)
            assertTrue(actual.drop(1).all { it.reason == null })
        }
    }

    @Test
    fun everySplit_trainsEveryGroupAtLeastOnce_andNeverOnSixDays() {
        for (days in 1..6) {
            for (split in SplitCatalog.splitsFor(days)) {
                for (g in SplitCatalog.ALL_GROUPS) {
                    val f = SplitCatalog.frequency(split.types, g)
                    assertTrue(f >= 1, "days $days $g")
                    assertTrue(f < 6 || days < 6, "days $days $g")
                }
            }
        }
    }

    @Test
    fun outsideOneToSix_noSplits() {
        assertTrue(SplitCatalog.splitsFor(0).isEmpty())
        assertTrue(SplitCatalog.splitsFor(7).isEmpty())
    }

    @Test
    fun oneDay_onlyFullBodyIsEnabled() {
        val split = SplitCatalog.splitsFor(1).single()
        val options = SplitCatalog.dayOptions(split, split.types, 0, TrainingIntention.Muscle)
        assertEquals(RoutineType.entries, options.map { it.type })
        assertEquals(listOf(FullBody), options.filter { it.enabled }.map { it.type })
        assertEquals(listOf(FullBody), options.filter { it.recommended }.map { it.type })
        assertEquals(
            DisabledReason.LeavesGroupUntrained(MuscleGroup.Quads),
            options.first { it.type == Upper }.disabledReason,
        )
    }

    @Test
    fun sixDays_aGroupOnEverySixDaysIsDisabled() {
        val split = SplitCatalog.splitsFor(6).first()
        val options = SplitCatalog.dayOptions(split, List(6) { FullBody }, 0, TrainingIntention.Muscle)
        assertEquals(
            DisabledReason.GroupEveryDay(MuscleGroup.Chest),
            options.first {
                it.type == FullBody
            }.disabledReason,
        )
        assertEquals(DisabledReason.GroupEveryDay(MuscleGroup.Chest), options.first { it.type == Upper }.disabledReason)
        assertEquals(DisabledReason.GroupEveryDay(MuscleGroup.Quads), options.first { it.type == Lower }.disabledReason)
    }

    @Test
    fun twoDays_swappingOneDayToTorsoLeavesLegsUntrainedWhenTheOtherIsTorso() {
        val chosen = listOf(Upper, Upper)
        val split = SplitCatalog.splitsFor(2)[1]
        val options = SplitCatalog.dayOptions(split, chosen, 1, TrainingIntention.Muscle)
        assertEquals(
            DisabledReason.LeavesGroupUntrained(MuscleGroup.Quads),
            options.first {
                it.type == Upper
            }.disabledReason,
        )
        assertTrue(options.first { it.type == Lower }.enabled)
    }

    @Test
    fun strengthNote_whenAGroupIsTrainedOnOneDay_neverDisables() {
        val split = SplitCatalog.splitsFor(2)[1]
        val options = SplitCatalog.dayOptions(split, listOf(Upper, Lower), 0, TrainingIntention.Strength)
        assertTrue(options.first { it.type == Upper }.strengthNote)
        assertTrue(options.first { it.type == Upper }.enabled)
        val other = SplitCatalog.dayOptions(split, listOf(Upper, Lower), 0, TrainingIntention.Muscle)
        assertFalse(other.any { it.strengthNote })
    }

    @Test
    fun strengthNote_absentWhenEveryGroupTwice() {
        val split = SplitCatalog.splitsFor(2).first()
        val options = SplitCatalog.dayOptions(split, split.types, 0, TrainingIntention.Strength)
        assertNull(options.first { it.type == FullBody }.disabledReason)
        assertFalse(options.first { it.type == FullBody }.strengthNote)
    }

    @Test
    fun deterministic() {
        assertEquals(SplitCatalog.splitsFor(4), SplitCatalog.splitsFor(4))
    }
}
