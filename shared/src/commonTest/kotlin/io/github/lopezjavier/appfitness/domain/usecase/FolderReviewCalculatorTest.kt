package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RepRange
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderReviewCalculatorTest {
    private fun ex(
        id: String,
        group: MuscleGroup,
        sets: Int,
        secondary: List<MuscleGroup> = emptyList(),
        pattern: MovementPattern? = null,
        counts: Boolean = true,
        reps: Pair<Int, Int>? = null,
    ) = ReviewExercise(id, group, secondary, counts, pattern, sets, reps?.first, reps?.second)

    private fun routine(name: String, vararg exercises: ReviewExercise) = ReviewRoutine(name, exercises.toList())

    private fun iso(group: MuscleGroup, sets: Int = 10) =
        ex("iso-$group", group, sets, pattern = MovementPattern.Isolation)

    /** Two routines with every group at 10 sets, no main lifts. */
    private fun fullFolder() = listOf(
        routine("A", iso(MuscleGroup.Chest), iso(MuscleGroup.Back), iso(MuscleGroup.Shoulders)),
        routine("B", iso(MuscleGroup.Quads), iso(MuscleGroup.Hamstrings), iso(MuscleGroup.Glutes)),
    )

    private fun setsOf(result: FolderReviewResult, group: MuscleGroup) =
        result.rows.filterIsInstance<GroupRow>().first { it.group == group }.sets

    private fun compute(routines: List<ReviewRoutine>, intention: TrainingIntention? = TrainingIntention.Muscle) =
        FolderReviewCalculator.compute(routines, intention)

    // --- counting -------------------------------------------------------------------------------------------

    @Test
    fun primaryCountsOne_secondaryCountsHalf() {
        val bench = ex("bench", MuscleGroup.Chest, 4, listOf(MuscleGroup.Shoulders, MuscleGroup.Triceps))

        val result = compute(listOf(routine("A", bench)))

        assertEquals(4.0, setsOf(result, MuscleGroup.Chest))
        assertEquals(2.0, setsOf(result, MuscleGroup.Shoulders))
        assertEquals(0.0, setsOf(result, MuscleGroup.Back))
    }

    @Test
    fun repeatedSecondaryGroup_countsOnlyOneHalfPerExercise() {
        val row = ex("row", MuscleGroup.Back, 4, listOf(MuscleGroup.Shoulders, MuscleGroup.Shoulders))

        assertEquals(2.0, setsOf(compute(listOf(routine("A", row))), MuscleGroup.Shoulders))
    }

    @Test
    fun secondaryOnTwoExercises_addsUp() {
        val a = ex("a", MuscleGroup.Chest, 3, listOf(MuscleGroup.Shoulders))
        val b = ex("b", MuscleGroup.Back, 5, listOf(MuscleGroup.Shoulders))

        assertEquals(4.0, setsOf(compute(listOf(routine("A", a), routine("B", b))), MuscleGroup.Shoulders))
    }

    @Test
    fun nonCountingExercises_addNothing_andDoNotMakeALiftingRoutine() {
        val wallSit = ex("wall-sit", MuscleGroup.Quads, 5, counts = false)
        val cardio = ex("walking", MuscleGroup.FullBody, 1, counts = false)

        val result = compute(listOf(routine("A", wallSit, cardio)))

        assertEquals(0.0, setsOf(result, MuscleGroup.Quads))
        assertEquals(0, result.liftingRoutines)
    }

    @Test
    fun fullBodyPrimary_addsOnlyItsSecondaries() {
        val clean = ex("clean", MuscleGroup.FullBody, 4, listOf(MuscleGroup.Quads, MuscleGroup.Back))

        val result = compute(listOf(routine("A", clean)))

        assertEquals(2.0, setsOf(result, MuscleGroup.Quads))
        assertEquals(2.0, setsOf(result, MuscleGroup.Back))
    }

    @Test
    fun groupOutsideTheSix_isIgnored() {
        val curl = ex("curl", MuscleGroup.Biceps, 10)

        val result = compute(listOf(routine("A", curl)))

        assertEquals(6, result.rows.filterIsInstance<GroupRow>().size)
        assertEquals(1, result.liftingRoutines)
    }

    // --- custom exercise defaults and rep range -------------------------------------------------------------

    private fun custom(metric: ExerciseMetricType, stored: Boolean = true) = Exercise(
        id = "c", key = null, name = "Mine", muscleGroup = MuscleGroup.Back, secondaryMuscles = emptyList(),
        equipment = Equipment.Other, metricType = metric, isCustom = true, countsAsStrength = stored,
    )

    @Test
    fun customExercise_countsUnlessTimedOrDistance_whateverTheStoredFlag() {
        assertTrue(custom(ExerciseMetricType.WeightReps).countsForVolume())
        assertTrue(custom(ExerciseMetricType.RepsOnly).countsForVolume())
        assertFalse(custom(ExerciseMetricType.Duration).countsForVolume())
        assertFalse(custom(ExerciseMetricType.DistanceDuration, stored = true).countsForVolume())
        assertFalse(custom(ExerciseMetricType.WeightDuration).countsForVolume())
    }

    @Test
    fun catalogueExercise_followsTheStoredFlag() {
        val row = custom(ExerciseMetricType.Duration).copy(isCustom = false, key = "plank")

        assertFalse(row.copy(countsAsStrength = false).countsForVolume())
        assertTrue(row.copy(countsAsStrength = true).countsForVolume())
    }

    @Test
    fun repRange_ownRangeWins() {
        val sets = listOf(PlannedSet(null, 3), PlannedSet(null, 12))

        assertEquals(6 to 10, FolderReviewCalculator.resolveRepRange(RepRange(6, 10), sets))
    }

    @Test
    fun repRange_withoutOne_usesMinAndMaxOfWorkingSets() {
        val sets = listOf(PlannedSet(null, 15, SetType.WarmUp), PlannedSet(null, 5), PlannedSet(null, 8))

        assertEquals(5 to 8, FolderReviewCalculator.resolveRepRange(null, sets))
    }

    @Test
    fun repRange_withoutRangeOrPlannedReps_isNull() {
        assertNull(FolderReviewCalculator.resolveRepRange(null, listOf(PlannedSet.ofMinutes(20))))
        assertNull(FolderReviewCalculator.resolveRepRange(null, emptyList()))
    }

    // --- C1 / C2 / C3 ---------------------------------------------------------------------------------------

    @Test
    fun volume_9Fails_10And11Pass_andThereIsNoUpperBound() {
        fun metFor(sets: Int) = compute(listOf(routine("A", iso(MuscleGroup.Chest, sets))))
            .rows.filterIsInstance<GroupRow>().first { it.group == MuscleGroup.Chest }.met

        assertFalse(metFor(9))
        assertTrue(metFor(10))
        assertTrue(metFor(11))
        assertTrue(metFor(25))
    }

    @Test
    fun over_isFalseAtExactly20_andTrueAbove() {
        assertFalse(GroupRow(MuscleGroup.Chest, 20.0).over)
        assertTrue(GroupRow(MuscleGroup.Chest, 20.5).over)
        assertTrue(GroupRow(MuscleGroup.Chest, 25.0).over)
        assertFalse(GroupRow(MuscleGroup.Chest, 12.0).over)
    }

    @Test
    fun groupMetAndMissing_atTheBoundaries() {
        assertTrue(GroupRow(MuscleGroup.Back, 10.0).met)
        assertTrue(GroupRow(MuscleGroup.Back, 12.0).met)
        val low = GroupRow(MuscleGroup.Back, 9.5)
        assertFalse(low.met)
        assertEquals(1, low.missingSets)
        assertTrue(GroupRow(MuscleGroup.Back, 0.0).isMissing)
    }

    @Test
    fun groupRows_areTheSameForEveryIntention() {
        val folder = listOf(routine("A", iso(MuscleGroup.Chest, 4), iso(MuscleGroup.Back, 25)))
        val reference = compute(folder, TrainingIntention.Muscle).rows.filterIsInstance<GroupRow>()
        TrainingIntention.entries.forEach {
            assertEquals(reference, compute(folder, it).rows.filterIsInstance<GroupRow>())
        }
    }

    @Test
    fun setsAbove20_doNotChangeScoreFindingsOrComplete() {
        val at20 = compute(
            listOf(routine("A", iso(MuscleGroup.Chest, 20)), routine("B", iso(MuscleGroup.Back, 10))) +
                fullFolder().drop(0),
        )
        val at30 = compute(
            listOf(routine("A", iso(MuscleGroup.Chest, 30)), routine("B", iso(MuscleGroup.Back, 10))) +
                fullFolder().drop(0),
        )
        assertEquals(at20.score, at30.score)
        assertEquals(at20.findings.size, at30.findings.size)
        assertEquals(at20.complete, at30.complete)
    }

    @Test
    fun missingSets_roundsUp() {
        val row = compute(listOf(routine("A", iso(MuscleGroup.Chest, 7)))).rows.filterIsInstance<GroupRow>().first()

        assertEquals(3, row.missingSets)
        val half = compute(
            listOf(routine("A", ex("b", MuscleGroup.Chest, 3, listOf(MuscleGroup.Back)), iso(MuscleGroup.Back, 7))),
        ).rows.filterIsInstance<GroupRow>().first { it.group == MuscleGroup.Back }
        assertEquals(8.5, half.sets)
        assertEquals(2, half.missingSets)
    }

    @Test
    fun zeroGroup_isReportedAsMissing_notAsLow() {
        val result = compute(listOf(routine("A", iso(MuscleGroup.Chest, 10))))

        val back = result.findings.filterIsInstance<GroupRow>().first { it.group == MuscleGroup.Back }
        assertTrue(back.isMissing)
        assertEquals(1, result.findings.filterIsInstance<GroupRow>().count { it.group == MuscleGroup.Back })
    }

    @Test
    fun days_zeroOneAndTwoLiftingRoutines() {
        val lift = iso(MuscleGroup.Chest)
        val cardio = ex("walking", MuscleGroup.FullBody, 1, counts = false)

        assertEquals(0, compute(emptyList()).liftingRoutines)
        assertEquals(1, compute(listOf(routine("A", lift), routine("B", cardio))).liftingRoutines)
        val two = compute(listOf(routine("A", lift), routine("B", lift)))
        assertEquals(2, two.liftingRoutines)
        assertTrue(two.rows.filterIsInstance<DaysRow>().single().met)
    }

    // --- score ----------------------------------------------------------------------------------------------

    @Test
    fun goodFolder_scoresTen_andIsComplete() {
        val result = compute(fullFolder())

        assertEquals(10.0, result.score)
        assertTrue(result.complete)
        assertTrue(result.findings.isEmpty())
        assertEquals(7, result.rows.size)
    }

    @Test
    fun oneRoutine_hasNoDays_scoreLosesTheDaysShare() {
        val one = listOf(fullFolder().flatMap { it.exercises }.let { routine("A", *it.toTypedArray()) })

        val result = compute(one)

        assertEquals(7.5, result.score)
        assertFalse(result.complete)
        assertEquals(listOf(DaysRow(1)), result.findings)
    }

    @Test
    fun score_roundsHalfUp() {
        // Only chest at 10 over two routines: 10 x (75 x 1/6 + 25) / 100 = 3.75 -> 3.8.
        val result = compute(listOf(routine("A", iso(MuscleGroup.Chest, 5)), routine("B", iso(MuscleGroup.Chest, 5))))

        assertEquals(3.8, result.score)
    }

    @Test
    fun zeroGroup_capsAnOtherwiseHighScoreAtSix() {
        // Glutes missing, the rest met: 10 x (75 x 5/6 + 25) / 100 = 8.75 -> 8.8, capped.
        val folder = listOf(
            routine("A", iso(MuscleGroup.Chest), iso(MuscleGroup.Back), iso(MuscleGroup.Shoulders)),
            routine("B", iso(MuscleGroup.Quads), iso(MuscleGroup.Hamstrings)),
        )

        assertEquals(6.0, compute(folder).score)
    }

    @Test
    fun zeroGroup_doesNotRaiseALowerScore() {
        // Four groups met in one routine, two empty: 10 x 75 x 4/6 / 100 = 5.0 stays 5.0.
        val folder = listOf(
            routine(
                "A",
                iso(MuscleGroup.Chest),
                iso(MuscleGroup.Back),
                iso(MuscleGroup.Shoulders),
                iso(MuscleGroup.Quads),
            ),
        )

        assertEquals(5.0, compute(folder).score)
    }

    @Test
    fun emptyFolder_scoresZero_withEveryRowUnmet() {
        val result = compute(emptyList())

        assertEquals(0.0, result.score)
        assertTrue(result.isEmpty)
        assertTrue(result.rows.none { it.met })
    }

    @Test
    fun unknownIntention_usesTheOtherWeights_andShowsNoStrengthRows() {
        val result = compute(fullFolder(), intention = null)

        assertEquals(10.0, result.score)
        assertEquals(7, result.rows.size)
    }

    // --- strength -------------------------------------------------------------------------------------------

    private fun main(id: String, group: MuscleGroup, pattern: MovementPattern, reps: Pair<Int, Int>?, sets: Int = 10) =
        ex(id, group, sets, pattern = pattern, reps = reps)

    private val strength = TrainingIntention.Strength

    @Test
    fun heavyReps_max8Passes_9Fails_singlesPass_noRangeNotJudged() {
        fun repsRow(reps: Pair<Int, Int>?) = compute(
            listOf(routine("A", main("squat", MuscleGroup.Quads, MovementPattern.Squat, reps))),
            strength,
        ).rows.filterIsInstance<StrengthRepsRow>().singleOrNull()

        assertTrue(repsRow(6 to 8)!!.met)
        assertFalse(repsRow(6 to 9)!!.met)
        assertEquals(RepOffender("A", "squat", 6, 9), repsRow(6 to 9)!!.offenders.single())
        assertTrue(repsRow(1 to 1)!!.met)
        assertNull(repsRow(null))
    }

    @Test
    fun heavyReps_ignoresIsolationAndNonMainExercises() {
        val folder = listOf(
            routine(
                "A",
                ex(
                    "curl",
                    MuscleGroup.Biceps,
                    3,
                    pattern = MovementPattern.Isolation,
                    reps =
                    12 to 15,
                ),
            ),
        )

        assertNull(compute(folder, strength).rows.filterIsInstance<StrengthRepsRow>().singleOrNull())
    }

    @Test
    fun order_mainLiftFirstPasses_otherFirstFails() {
        val squat = main("squat", MuscleGroup.Quads, MovementPattern.Squat, 5 to 5)
        val curl = ex("curl", MuscleGroup.Biceps, 3, pattern = MovementPattern.Isolation)

        val ok = compute(listOf(routine("A", squat, curl)), strength).rows.filterIsInstance<StrengthOrderRow>().single()
        val bad = compute(
            listOf(routine("A", curl, squat)),
            strength,
        ).rows.filterIsInstance<StrengthOrderRow>().single()

        assertTrue(ok.met)
        assertEquals(OrderOffender("A", "squat", "curl", listOf("squat")), bad.offenders.single())
    }

    @Test
    fun order_nonCountingRowsAreIgnored() {
        val stretch = ex("stretch", MuscleGroup.Quads, 1, counts = false)
        val squat = main("squat", MuscleGroup.Quads, MovementPattern.Squat, 5 to 5)
        val curl = ex("curl", MuscleGroup.Biceps, 3, pattern = MovementPattern.Isolation)

        val row = compute(
            listOf(routine("A", stretch, squat, curl)),
            strength,
        ).rows.filterIsInstance<StrengthOrderRow>().single()

        assertTrue(row.met)
    }

    @Test
    fun order_routineWithOnlyMainLifts_isNotEligible() {
        val folder = listOf(routine("A", main("squat", MuscleGroup.Quads, MovementPattern.Squat, 5 to 5)))

        assertNull(compute(folder, strength).rows.filterIsInstance<StrengthOrderRow>().singleOrNull())
    }

    @Test
    fun strengthRows_areNotShownForOtherIntentions() {
        val squat = main("squat", MuscleGroup.Quads, MovementPattern.Squat, 10 to 12)
        val curl = ex("curl", MuscleGroup.Biceps, 3, pattern = MovementPattern.Isolation)

        val rows = compute(listOf(routine("A", curl, squat)), TrainingIntention.Muscle).rows

        assertTrue(rows.none { it is StrengthRepsRow || it is StrengthOrderRow })
    }

    @Test
    fun strengthScore_rescalesWhenNothingIsJudged() {
        // No main lift and no eligible routine: V 50 + D 20 over 70 -> 10.0, and no strength rows.
        val result = compute(fullFolder(), strength)

        assertEquals(10.0, result.score)
        assertTrue(result.complete)
    }

    @Test
    fun strengthScore_rescalesWithoutOrder_whenOnlyRepsAreJudged() {
        // All groups met, 2 days, the one main lift fails C4a, no eligible routine: 70 / 85 = 8.2.
        val bench = main("bench", MuscleGroup.Chest, MovementPattern.HorizontalPush, 10 to 12)
        val folder = listOf(
            routine("A", bench),
            routine(
                "B",
                iso(MuscleGroup.Back),
                iso(MuscleGroup.Shoulders),
                iso(MuscleGroup.Quads),
                iso(MuscleGroup.Hamstrings),
                iso(MuscleGroup.Glutes),
            ),
        )

        val result = compute(folder, strength)

        assertEquals(8.2, result.score)
    }

    @Test
    fun strengthScore_weightsHalfPassingRepsAndOrder() {
        // V 1, D 1, H 1/2, O 1/2: (50 + 20 + 7.5 + 7.5) / 100 = 8.5.
        val squat = main("squat", MuscleGroup.Quads, MovementPattern.Squat, 5 to 5)
        val bench = main("bench", MuscleGroup.Chest, MovementPattern.HorizontalPush, 10 to 12)
        val folder = listOf(
            routine("A", squat, iso(MuscleGroup.Back), iso(MuscleGroup.Hamstrings)),
            routine("B", iso(MuscleGroup.Shoulders), bench, iso(MuscleGroup.Glutes)),
        )

        val result = compute(folder, strength)

        assertEquals(8.5, result.score)
        assertEquals(listOf(StrengthRepsRow::class, StrengthOrderRow::class), result.findings.map { it::class })
    }

    @Test
    fun strengthFolder_allMet_scoresTen() {
        val squat = main("squat", MuscleGroup.Quads, MovementPattern.Squat, 5 to 5)
        val bench = main("bench", MuscleGroup.Chest, MovementPattern.HorizontalPush, 6 to 8)
        val folder = listOf(
            routine("A", squat, iso(MuscleGroup.Back), iso(MuscleGroup.Hamstrings)),
            routine("B", bench, iso(MuscleGroup.Shoulders), iso(MuscleGroup.Glutes)),
        )

        val result = compute(folder, strength)

        assertEquals(10.0, result.score)
        assertTrue(result.complete)
        assertEquals(9, result.rows.size)
    }

    // --- findings order -------------------------------------------------------------------------------------

    @Test
    fun findings_followC2ThenC1ByDeficitThenC3() {
        val folder = listOf(
            routine(
                "A",
                iso(MuscleGroup.Chest, 8),
                iso(MuscleGroup.Back, 2),
                iso(MuscleGroup.Quads),
                iso(MuscleGroup.Hamstrings),
                iso(MuscleGroup.Glutes),
            ),
        )

        val kinds = compute(folder).findings.map { (it as? GroupRow)?.group ?: it::class }

        assertEquals(listOf(MuscleGroup.Shoulders, MuscleGroup.Back, MuscleGroup.Chest, DaysRow::class), kinds)
    }

    @Test
    fun workedExample_chestOnlyBench_threeDaysFolder() {
        // 07 example style: bench 4 sets x 3 routines -> chest 12, shoulders 6; the rest empty; 3 days.
        val bench = ex("bench", MuscleGroup.Chest, 4, listOf(MuscleGroup.Shoulders), MovementPattern.HorizontalPush)
        val result = compute(listOf(routine("A", bench), routine("B", bench), routine("C", bench)))

        assertEquals(12.0, setsOf(result, MuscleGroup.Chest))
        assertEquals(6.0, setsOf(result, MuscleGroup.Shoulders))
        assertEquals(3, result.liftingRoutines)
        // pV = (1 + 0.6) / 6, pD = 1: 10 x (75 x 0.26667 + 25) / 100 = 4.5, no cap issue (below 6).
        assertEquals(4.5, result.score)
    }
}
