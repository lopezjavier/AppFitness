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
    fun everySplit_hasAFamily_andItsTypesBelongToIt() {
        for (days in 1..6) {
            for (split in SplitCatalog.splitsFor(days)) {
                assertTrue(split.types.all { it in split.family.allowedTypes }, "days $days ${split.types}")
            }
        }
        assertEquals(SplitFamily.FullBody, SplitCatalog.splitsFor(1).single().family)
        assertEquals(
            listOf(SplitFamily.FullBody, SplitFamily.UpperLower, SplitFamily.PushPull),
            SplitCatalog.splitsFor(2).map { it.family },
        )
    }

    @Test
    fun hasFullBodyDay_onlyForTheExtraDayOfThreeAndFiveDaySplits() {
        assertEquals(listOf(false, true, true), SplitCatalog.splitsFor(3).map { it.hasFullBodyDay })
        assertEquals(listOf(true, true, false), SplitCatalog.splitsFor(5).map { it.hasFullBodyDay })
        assertEquals(listOf(false, false), SplitCatalog.splitsFor(6).map { it.hasFullBodyDay })
        assertFalse(SplitCatalog.splitsFor(1).single().hasFullBodyDay)
    }

    @Test
    fun oneDay_fullBodyFamily_offersOnlyFullBody() {
        val split = SplitCatalog.splitsFor(1).single()
        val options = SplitCatalog.dayOptions(split, split.types, 0, TrainingIntention.Muscle)
        assertEquals(listOf(FullBody), options.map { it.type })
        assertTrue(options.single().enabled)
        assertTrue(options.single().recommended)
    }

    @Test
    fun threeDayTorsoLeg_offersTorsoAndLegOnDaysOneAndTwo_andFullBodyOnlyOnDayThree() {
        val split = SplitCatalog.splitsFor(3)[1]
        fun types(day: Int) = SplitCatalog.dayOptions(split, split.types, day, TrainingIntention.Muscle).map { it.type }
        assertEquals(listOf(Upper, Lower), types(0))
        assertEquals(listOf(Upper, Lower), types(1))
        assertEquals(listOf(FullBody), types(2))
    }

    @Test
    fun noOptionsOfAnotherFamily() {
        for (days in 1..6) {
            for (split in SplitCatalog.splitsFor(days)) {
                val types = split.types.indices.flatMap { day ->
                    SplitCatalog.dayOptions(split, split.types, day, null).map { it.type }
                }
                assertTrue(types.all { it in split.family.allowedTypes }, "days $days")
                if (split.family == SplitFamily.UpperLower) assertTrue(Push !in types && Pull !in types)
                if (split.family == SplitFamily.PushPull) assertTrue(Upper !in types && Lower !in types)
            }
        }
    }

    @Test
    fun recommendedOption_isTheSplitsOwnType_andStaysResolvableForTheChosenType() {
        val split = SplitCatalog.splitsFor(4).first()
        val options = SplitCatalog.dayOptions(split, split.types, 1, TrainingIntention.Muscle)
        assertEquals(listOf(Lower), options.filter { it.recommended }.map { it.type })
        assertNull(options.first { it.type == Lower }.disabledReason)
    }

    @Test
    fun sixDays_swappingToTheOtherTypeOfTheFamily_neverTrainsAGroupEverySixDays() {
        val split = SplitCatalog.splitsFor(6).first()
        val options = SplitCatalog.dayOptions(split, split.types, 0, TrainingIntention.Muscle)
        assertEquals(listOf(Upper, Lower), options.map { it.type })
        assertTrue(options.all { it.enabled })
    }

    @Test
    fun sixDays_swappingTheOnlyLowerDayToUpper_leavesLegsUntrained() {
        val split = SplitCatalog.splitsFor(6).first()
        val chosen = listOf(Upper, Upper, Upper, Upper, Upper, Lower)
        val options = SplitCatalog.dayOptions(split, chosen, 5, TrainingIntention.Muscle)
        assertEquals(
            DisabledReason.LeavesGroupUntrained(MuscleGroup.Quads),
            options.first { it.type == Upper }.disabledReason,
        )
        assertTrue(options.first { it.type == Lower }.enabled)
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
