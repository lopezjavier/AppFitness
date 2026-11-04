package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewCalculator
import io.github.lopezjavier.appfitness.domain.usecase.ReviewExercise
import io.github.lopezjavier.appfitness.domain.usecase.ReviewRoutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuilderStateTest {
    private val cat = catalogue()

    private fun review(block: BuiltBlock, intention: TrainingIntention) = FolderReviewCalculator.compute(
        block.sessions.map { session ->
            ReviewRoutine(
                session.name,
                session.exercises.filter { it.role != BuiltExercise.ROLE_CARDIO }.map { e ->
                    val x = cat.first { it.id == e.exerciseId }.exercise
                    ReviewExercise(
                        e.exerciseId,
                        x.muscleGroup,
                        x.secondaryMuscles,
                        true,
                        x.movementPattern,
                        e.sets,
                        e.repMin,
                        e.repMax,
                    )
                },
            )
        },
        intention,
    )

    @Test
    fun fullBlock_everyDayCountAndIntention_reviewsAtTenPointZero_whenNothingWasCut() {
        for (days in 2..6) {
            for (split in SplitCatalog.splitsFor(days)) {
                for (intention in TrainingIntention.entries) {
                    val block = assertNotNull(fillAll(startState(split.types, intention)).toBuiltBlock())
                    val result = review(block, intention)
                    assertEquals(10.0, result.score, "$days $intention ${split.types}")
                    assertTrue(result.complete, "$days $intention ${split.types}")
                }
            }
        }
    }

    @Test
    fun oneDay_capsAtSevenPointFive_andEightForStrength() {
        val types = listOf(RoutineType.FullBody)
        assertEquals(
            7.5,
            review(assertNotNull(fillAll(startState(types)).toBuiltBlock()), TrainingIntention.Muscle).score,
        )
        assertEquals(
            8.0,
            review(
                assertNotNull(fillAll(startState(types, TrainingIntention.Strength)).toBuiltBlock()),
                TrainingIntention.Strength,
            ).score,
        )
    }

    @Test
    fun strengthBlock_mainPatternExercisesOfAccessorySlotsComeFirst() {
        // Pick an isolation in the main slot's place is impossible; fill an accessory slot with a main lift.
        var s = startState(listOf(RoutineType.Upper, RoutineType.Lower), TrainingIntention.Strength)
        s = fillAll(s)
        val day = s.days[0]
        val accessoryIndex = day.slots.indexOfFirst {
            it.slot.kind == SlotKind.Accessory &&
                it.slot.group == MuscleGroup.Back
        }
        val back = cat.first { it.id == "back-main" }
        // The same exercise would repeat in the routine, so swap the back main slot to the isolation first.
        val mainIndex = day.slots.indexOfFirst { it.slot.kind == SlotKind.Main && it.slot.group == MuscleGroup.Back }
        s = s.pick(0, mainIndex, Pick.of(cat.first { it.id == "back-iso" }.exercise, day.slots[mainIndex].slot, null))
        s = s.pick(0, accessoryIndex, Pick.of(back.exercise, day.slots[accessoryIndex].slot, null))
        val exercises = assertNotNull(s.toBuiltBlock()).sessions[0].exercises
        val roles = exercises.map { it.role }
        assertEquals(roles.sortedBy { if (it == BuiltExercise.ROLE_MAIN) 0 else 1 }, roles)
        assertTrue(review(assertNotNull(s.toBuiltBlock()), TrainingIntention.Strength).complete)
    }

    @Test
    fun payload_roleByPattern_ratesAndSlotValues() {
        val block =
            assertNotNull(
                fillAll(startState(SplitCatalog.splitsFor(2).first().types, TrainingIntention.Muscle)).toBuiltBlock(),
            )
        val first = block.sessions[0].exercises.first()
        assertEquals(BuiltExercise.ROLE_MAIN, first.role) // first eligible: the barbell main lift
        assertEquals(
            listOf(5, 8, 12, 120, 8),
            listOf(first.sets, first.repMin, first.repMax, first.restSeconds, first.targetRpe),
        )
        assertEquals(2, block.daysPerWeek)
        assertEquals(4, BuiltBlock.BLOCK_WEEKS)
        assertEquals(BuiltBlock.SOURCE_BUILDER, block.source)
        assertEquals(listOf("quads", "chest", "back", "hamstrings", "shoulders", "glutes"), block.sessions[0].focus)
    }

    @Test
    fun payload_incompleteBlock_isNull() {
        assertNull(startState(listOf(RoutineType.FullBody)).toBuiltBlock())
    }

    @Test
    fun payload_cardioLast_withFiveMinuteUnitsAndTotals() {
        val block =
            assertNotNull(
                fillAll(
                    startState(SplitCatalog.splitsFor(4).first().types, minutes = 600, cardioTarget = 150),
                ).toBuiltBlock(),
            )
        val per = block.cardio.perRoutineMin
        assertEquals(150, per.sum())
        assertEquals(listOf(40, 40, 35, 35), per)
        assertEquals(per.map { 600 + it }, block.routineTotalMin)
        block.sessions.forEachIndexed { i, session ->
            val last = session.exercises.last()
            assertEquals(BuiltExercise.ROLE_CARDIO, last.role)
            assertEquals(per[i], last.durationMin)
            assertEquals("suave", last.pace)
            assertEquals(1, session.exercises.count { it.role == BuiltExercise.ROLE_CARDIO })
        }
    }

    @Test
    fun cardioPlan_capAtSixtyPerRoutine_andRemainderIsOnYourOwn() {
        val plan = CardioPlan.of(300, 2)
        assertEquals(listOf(60, 60), plan.perRoutineMin)
        assertEquals(120, plan.plannedMinWeek)
        assertEquals(180, plan.onYourOwnMin)
        val small = CardioPlan.of(150, 4)
        assertEquals(listOf(40, 40, 35, 35), small.perRoutineMin)
        assertEquals(0, small.onYourOwnMin)
        assertEquals(listOf(0, 0), CardioPlan.of(0, 2).perRoutineMin)
        assertEquals(listOf(0, 0), CardioPlan.of(4, 2).perRoutineMin)
    }

    @Test
    fun noCardioExercise_noCardioSlot() {
        val block = assertNotNull(fillAll(startState(listOf(RoutineType.FullBody), cardioId = null)).toBuiltBlock())
        assertTrue(block.sessions.single().exercises.none { it.role == BuiltExercise.ROLE_CARDIO })
    }

    @Test
    fun supersetGroups_pairEqualSetsAndAreNumberedPerRoutine() {
        val types = listOf(RoutineType.Upper, RoutineType.Upper)
        val tight = (
            BuilderState.start(
                types,
                TrainingIntention.Muscle,
                24,
                emptyList(),
                150,
                null,
            ) as BuilderStart.Ready
            ).state
        val filled = fillAll(tight)
        val block = assertNotNull(filled.toBuiltBlock())
        for (session in block.sessions) {
            val grouped = session.exercises.filter { it.supersetGroup != null }.groupBy { it.supersetGroup }
            assertTrue(grouped.isNotEmpty())
            for (pair in grouped.values) {
                assertEquals(2, pair.size)
                assertEquals(1, pair.map { it.sets }.distinct().size)
            }
        }
    }

    @Test
    fun routineNames_areUniquePerDay() {
        assertEquals(
            listOf("Torso A", "Pierna A", "Torso B", "Pierna B"),
            BuilderState.routineNames(SplitCatalog.splitsFor(4).first().types),
        )
        assertEquals(listOf("Cuerpo completo"), BuilderState.routineNames(listOf(RoutineType.FullBody)))
        assertEquals(
            setOf("Cuerpo completo A", "Cuerpo completo B", "Cuerpo completo C"),
            BuilderState.routineNames(
                List(3) {
                    RoutineType.FullBody
                },
            ).toSet(),
        )
    }

    @Test
    fun plannedWeight_lastWeightWhenRepsInRange_elseEmpty_andRepsAreTheRangeMinimum() {
        val slot = Slot(MuscleGroup.Chest, SlotKind.Standard, 3, 8, 12, 120)
        val x = exercise("c", MuscleGroup.Chest).exercise
        assertEquals(60.0, Pick.of(x, slot, ExerciseHistory(3, 60.0, 10)).plannedWeightKg)
        assertEquals(60.0, Pick.of(x, slot, ExerciseHistory(3, 60.0, 8)).plannedWeightKg)
        assertNull(Pick.of(x, slot, ExerciseHistory(3, 60.0, 5)).plannedWeightKg)
        assertNull(Pick.of(x, slot, ExerciseHistory(3, 60.0, 13)).plannedWeightKg)
        assertNull(Pick.of(x, slot, null).plannedWeightKg)
        assertNull(Pick.of(x, slot, ExerciseHistory(1, null, null)).plannedWeightKg)
    }

    @Test
    fun plannedWeight_travelsToThePayload() {
        var s = startState(listOf(RoutineType.FullBody))
        s = fillAll(s)
        val slot = s.days[0].slots[0].slot
        val withWeight = s.pick(
            0,
            0,
            Pick(s.days[0].slots[0].pick!!.exerciseId, slot.group, MovementPattern.Squat, 80.0),
        )
        assertEquals(80.0, assertNotNull(withWeight.toBuiltBlock()).sessions[0].exercises[0].plannedWeightKg)
    }

    @Test
    fun extras_areTwoSetsAfterTheRequiredSlots_andReportOverTime() {
        val base = fillAll(startState(listOf(RoutineType.Upper, RoutineType.Lower), minutes = 40))
        val extra = exercise("biceps-1", MuscleGroup.Biceps, MovementPattern.Isolation).exercise
        val s = base.addExtra(0, Pick.of(extra, SectionPrescription.extraSlot(MuscleGroup.Biceps, null), null))
        assertEquals(2, s.days[0].extras.single().slot.sets)
        assertEquals(SlotKind.Extra, s.days[0].extras.single().slot.kind)
        val exercises = assertNotNull(s.toBuiltBlock()).sessions[0].exercises
        val ids = exercises.map { it.exerciseId }
        assertTrue("biceps-1" in ids)
        assertEquals(ids.indexOf("biceps-1"), exercises.lastIndex - 1, "extra sits just before cardio")
        assertEquals(0, s.removeExtra(0, 0).days[0].extras.size)
    }

    @Test
    fun determinism_sameInputsSameBlock() {
        val types = SplitCatalog.splitsFor(3).first().types
        assertEquals(fillAll(startState(types)).toBuiltBlock(), fillAll(startState(types)).toBuiltBlock())
    }

    @Test
    fun doesNotFit_reportsTheDay() {
        val result = BuilderState.start(
            listOf(RoutineType.FullBody),
            TrainingIntention.Muscle,
            10,
            emptyList(),
            150,
            null,
        )
        assertTrue(result is BuilderStart.NoFit && result.dayIndex == 0 && result.fit.limitMinutes == 10)
    }
}
