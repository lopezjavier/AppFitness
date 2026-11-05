package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.usecase.GroupRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FolderBlockCheckTest {
    private val cat = catalogue()

    private fun fe(
        id: String,
        group: MuscleGroup,
        sets: Int = 5,
        pattern: MovementPattern? = MovementPattern.Isolation,
        reps: Pair<Int, Int>? = 8 to 12,
        equipment: Equipment = Equipment.Machine,
        weight: Double? = null,
        rest: Int? = 120,
        counts: Boolean = true,
        added: Boolean = false,
    ) = FolderBlockExercise(
        id, group, emptyList(), counts, pattern, equipment, sets,
        reps?.first, reps?.second, 8.0, rest, weight, added,
    )

    private fun dayOf(suffix: String, pattern: (MuscleGroup) -> MovementPattern = { MovementPattern.Isolation }) =
        FolderBlockRoutine(
            "R$suffix",
            SplitCatalog.ALL_GROUPS.map { fe("${it.name.lowercase()}-$suffix", it, pattern = pattern(it)) },
        )

    private fun full(intention: TrainingIntention = TrainingIntention.Muscle, days: Int = 2) =
        FolderBlockDraft(listOf(dayOf("1"), dayOf("2")), intention, days, emptySet())

    @Test
    fun completeFolderWithMatchingDays_savesUnchanged() {
        val status = full().status()
        assertEquals(100, status.percent)
        assertEquals(DaysCheck.Ok, status.days)
        assertTrue(status.canSave)
        val block = assertNotNull(full().toBuiltBlock(60, 150, "bike", emptyMap()))
        assertEquals(BuiltBlock.SOURCE_FOLDER, block.source)
        assertEquals(listOf("R1", "R2"), block.sessions.map { it.name })
        assertEquals(6, block.sessions[0].exercises.count { it.role != BuiltExercise.ROLE_CARDIO })
        assertEquals(BuiltExercise.ROLE_CARDIO, block.sessions[0].exercises.last().role)
    }

    @Test
    fun status_isComputedOncePerDraft() {
        val draft = full()

        assertSame(draft.status(), draft.status())
    }

    @Test
    fun daysMismatch_isRefusedWithBothNumbers_andUnlocksWhenTheyAreEqual() {
        assertEquals(DaysCheck.Refused(2, 3), full(days = 3).status().days)
        assertFalse(full(days = 3).status().canSave)
        assertNull(full(days = 3).toBuiltBlock(60, 150, null, emptyMap()))
        assertTrue(full(days = 2).status().canSave)
    }

    @Test
    fun oneRoutineWithTwoDays_needsASecondRoutine() {
        val draft = FolderBlockDraft(listOf(dayOf("1")), TrainingIntention.Muscle, 2, emptySet())
        assertEquals(DaysCheck.NeedsSecondRoutine, draft.status().days)
        assertFalse(draft.status().canSave)
    }

    @Test
    fun nonLiftingRoutine_isNotADay_andIsNotCopied() {
        val stretch = FolderBlockRoutine("Estirar", listOf(fe("stretch", MuscleGroup.Back, counts = false)))
        val draft = full().copy(routines = full().routines + stretch)
        assertEquals(DaysCheck.Ok, draft.status().days)
        assertEquals(
            listOf("R1", "R2"),
            assertNotNull(draft.toBuiltBlock(60, 0, null, emptyMap())).sessions.map {
                it.name
            },
        )
    }

    @Test
    fun oneDayOneRoutine_topsAtSeventyFive_andEightyForStrength_butSaves() {
        val muscle = FolderBlockDraft(
            listOf(
                dayOf("1").let { r ->
                    r.copy(
                        exercises =
                        r.exercises + r.exercises.map { it.copy(id = it.id + "b") },
                    )
                },
            ),
            TrainingIntention.Muscle,
            1,
            emptySet(),
        )
        val s = muscle.status()
        assertEquals(75, s.percent)
        assertTrue(s.canSave)
        val strength = muscle.copy(
            routines = muscle.routines.map { r ->
                r.copy(
                    exercises = r.exercises.mapIndexed { i, e ->
                        if (i % 2 == 0) {
                            e.copy(movementPattern = MovementPattern.HorizontalPush, repMin = 4, repMax = 6)
                        } else {
                            e.copy(repMin = 6, repMax = 8)
                        }
                    },
                )
            },
            intention = TrainingIntention.Strength,
        )
        assertEquals(80, strength.status().percent)
        assertTrue(strength.status().canSave)
    }

    @Test
    fun add_capsAtFiveAndAtLeastTwo_andStartsFromTheMissingSets() {
        val missingChest = full().copy(
            routines = full().routines.map { r ->
                r.copy(
                    exercises = r.exercises.map {
                        if (it.muscleGroup ==
                            MuscleGroup.Chest
                        ) {
                            it.copy(sets = 1)
                        } else {
                            it
                        }
                    },
                )
            },
        )
        assertEquals(
            8,
            missingChest.status().review.rows.filterIsInstance<GroupRow>().first {
                it.group ==
                    MuscleGroup.Chest
            }.missingSets,
        )
        val chest = cat.first { it.id == "chest-iso2" }
        val added = missingChest.add(0, chest, null)
        val addedEx = added.routines[0].exercises.last()
        assertEquals(5, addedEx.sets)
        assertTrue(addedEx.added)
        assertEquals(listOf(8, 12, 120), listOf(addedEx.repMin, addedEx.repMax, addedEx.restSeconds))
        // Needs 3 more after the first add: 10 - (1 + 1 + 5) = 3.
        val second = added.add(1, cat.first { it.id == "chest-iso3" }, null)
        assertEquals(3, second.routines[1].exercises.last().sets)
        // 1 set missing -> at least 2.
        val oneShort = full().copy(
            routines = full().routines.map { r ->
                r.copy(
                    exercises = r.exercises.map {
                        if (it.muscleGroup ==
                            MuscleGroup.Chest &&
                            r.name == "R1"
                        ) {
                            it.copy(sets = 4)
                        } else {
                            it
                        }
                    },
                )
            },
        )
        assertEquals(2, oneShort.add(0, cat.first { it.id == "chest-iso2" }, null).routines[0].exercises.last().sets)
    }

    @Test
    fun add_raisesThePercent() {
        val short = full().copy(
            routines = full().routines.map { r ->
                r.copy(
                    exercises = r.exercises.filter {
                        it.muscleGroup !=
                            MuscleGroup.Glutes
                    },
                )
            },
        )
        val before = short.status().percent
        val after = short.add(
            0,
            cat.first {
                it.id == "glutes-iso"
            },
            null,
        ).add(1, cat.first { it.id == "glutes-iso2" }, null).status().percent
        assertTrue(after > before)
        assertEquals(100, after)
    }

    @Test
    fun add_strengthValues_heavyOnlyWhenItIsTheOnlyMainLiftOfTheGroupInTheRoutine() {
        val base = FolderBlockDraft(listOf(dayOf("1"), dayOf("2")), TrainingIntention.Strength, 2, emptySet())
        val heavy = base.add(0, cat.first { it.id == "chest-main" }, null).routines[0].exercises.last()
        assertEquals(listOf(4, 6, 180), listOf(heavy.repMin, heavy.repMax, heavy.restSeconds))
        val accessory = base.add(0, cat.first { it.id == "chest-iso2" }, null).routines[0].exercises.last()
        assertEquals(listOf(6, 8, 120), listOf(accessory.repMin, accessory.repMax, accessory.restSeconds))
        val withMain = base.copy(
            routines = base.routines.map { r ->
                r.copy(
                    exercises = r.exercises.map {
                        if (it.muscleGroup ==
                            MuscleGroup.Chest
                        ) {
                            it.copy(movementPattern = MovementPattern.HorizontalPush)
                        } else {
                            it
                        }
                    },
                )
            },
        )
        val second = withMain.add(0, cat.first { it.id == "chest-main" }, null).routines[0].exercises.last()
        assertEquals(listOf(6, 8, 120), listOf(second.repMin, second.repMax, second.restSeconds))
    }

    @Test
    fun add_ignoresRepeatsAndOutOfEquipment() {
        val draft = full().copy(equipment = setOf(Equipment.Machine))
        assertEquals(draft, draft.add(0, cat.first { it.id == "chest-main" }, null)) // barbell
        val held = draft.routines[0].exercises.first().id
        assertEquals(draft, draft.add(0, exercise(held, draft.routines[0].exercises.first().muscleGroup), null))
    }

    @Test
    fun removeAdded_removesOnlyWhatWasAdded() {
        val added = full().add(0, cat.first { it.id == "chest-iso2" }, null)
        val index = added.routines[0].exercises.lastIndex
        assertEquals(full().routines, added.removeAdded(0, index).routines)
        assertEquals(added, added.removeAdded(0, 0))
    }

    @Test
    fun swap_toAnotherGroup_isIgnored() {
        val draft = full()
        val swapped = draft.swap(0, 0, cat.first { it.id == "quads-iso" }, null)
        assertEquals(draft, swapped) // different group: ignored
    }

    @Test
    fun swap_sameGroup_keepsTheValues() {
        val draft = full()
        val index = draft.routines[0].exercises.indexOfFirst { it.muscleGroup == MuscleGroup.Chest }
        val new = draft.swap(0, index, cat.first { it.id == "chest-iso2" }, null).routines[0].exercises[index]
        val old = draft.routines[0].exercises[index]
        assertEquals("chest-iso2", new.id)
        assertEquals(
            listOf(old.sets, old.repMin, old.repMax, old.restSeconds, old.rpe),
            listOf(new.sets, new.repMin, new.repMax, new.restSeconds, new.rpe),
        )
    }

    @Test
    fun swap_fixesAnExerciseOutsideTheEquipment_andARepeat() {
        val outside = FolderBlockDraft(
            listOf(
                FolderBlockRoutine(
                    "R",
                    full().routines[0].exercises.map {
                        if (it.muscleGroup ==
                            MuscleGroup.Chest
                        ) {
                            it.copy(equipment = Equipment.Barbell)
                        } else {
                            it
                        }
                    },
                ),
                dayOf("2"),
            ),
            TrainingIntention.Muscle,
            2,
            setOf(Equipment.Machine),
        )
        assertTrue(outside.status().gate.any { it is GateIssue.OutsideEquipment })
        assertFalse(outside.status().canSave)
        val index = outside.routines[0].exercises.indexOfFirst { it.muscleGroup == MuscleGroup.Chest }
        val fixed = outside.swap(0, index, cat.first { it.id == "chest-iso" }, null)
        assertTrue(fixed.status().gate.isEmpty())

        val repeat = FolderBlockDraft(
            listOf(
                FolderBlockRoutine("R", full().routines[0].exercises + full().routines[0].exercises.first()),
                dayOf("2"),
            ),
            TrainingIntention.Muscle,
            2,
            emptySet(),
        )
        assertTrue(repeat.status().gate.any { it is GateIssue.RepeatInRoutine })
        val dupIndex = repeat.routines[0].exercises.lastIndex
        val unrepeated = repeat.swap(
            0,
            dupIndex,
            cat.first {
                it.id ==
                    "${repeat.routines[0].exercises.first().muscleGroup.name.lowercase()}-iso2"
            },
            null,
        )
        assertTrue(unrepeated.status().gate.isEmpty())
    }

    @Test
    fun swap_rejectsAnotherRepeatOrOutsideEquipment() {
        val draft = full().copy(equipment = setOf(Equipment.Machine))
        val chestIndex = draft.routines[0].exercises.indexOfFirst { it.muscleGroup == MuscleGroup.Chest }
        assertEquals(draft, draft.swap(0, chestIndex, cat.first { it.id == "chest-main" }, null)) // barbell not owned
    }

    @Test
    fun strengthRepFix_changesToMinOfMinAndSix_toEight() {
        val base =
            FolderBlockDraft(
                listOf(
                    dayOf("1") {
                        MovementPattern.HorizontalPush
                    },
                    dayOf("2") { MovementPattern.HorizontalPush },
                ),
                TrainingIntention.Strength,
                2,
                emptySet(),
            )
        val status = base.status()
        assertFalse(status.canSave)
        assertEquals(12, status.repFixes.size)
        val first = status.repFixes.first()
        assertEquals(listOf(6, 8), listOf(first.newMin, first.newMax))
        val fixed = base.applyStrengthRepFix(first.routineIndex, first.exerciseIndex)
        val e = fixed.routines[first.routineIndex].exercises[first.exerciseIndex]
        assertEquals(6 to 8, e.repMin to e.repMax)
        assertEquals(11, fixed.status().repFixes.size)
        // min 4 stays 4.
        val four = base.copy(
            routines = base.routines.map { r ->
                r.copy(exercises = r.exercises.map { it.copy(repMin = 4, repMax = 10) })
            },
        )
        assertEquals(4, four.status().repFixes.first().newMin)
        // Not an offender: unchanged.
        assertEquals(fixed, fixed.applyStrengthRepFix(first.routineIndex, first.exerciseIndex))
    }

    @Test
    fun strengthOrder_isFixedAtSave_mainLiftsFirst() {
        val mixed = FolderBlockRoutine(
            "R1",
            listOf(
                fe("iso", MuscleGroup.Chest, 5, MovementPattern.Isolation, 6 to 8),
                fe("bench", MuscleGroup.Chest, 5, MovementPattern.HorizontalPush, 4 to 6),
                fe("fly", MuscleGroup.Back, 5, MovementPattern.Isolation, 6 to 8),
                fe("row", MuscleGroup.Back, 5, MovementPattern.HorizontalPull, 4 to 6),
            ),
        )
        val other = mixed.copy(name = "R2", exercises = mixed.exercises.map { it.copy(id = it.id + "2") })
        val rest = SplitCatalog.ALL_GROUPS.filter { it != MuscleGroup.Chest && it != MuscleGroup.Back }.flatMap { g ->
            listOf("1", "2").map { fe("${g.name}-$it", g, 5, MovementPattern.Isolation, 6 to 8) }
        }
        val draft = FolderBlockDraft(
            listOf(
                mixed.copy(exercises = mixed.exercises + rest.filter { it.id.endsWith("1") }),
                other.copy(
                    exercises =
                    other.exercises + rest.filter { it.id.endsWith("2") },
                ),
            ),
            TrainingIntention.Strength,
            2,
            emptySet(),
        )
        assertTrue(draft.status().orderFixedAtSave)
        assertTrue(draft.status().canSave)
        val block = assertNotNull(draft.toBuiltBlock(60, 0, null, emptyMap()))
        assertEquals(listOf("bench", "row"), block.sessions[0].exercises.take(2).map { it.exerciseId })
    }

    @Test
    fun sourceDraft_isNeverMutated() {
        val source = full()
        val snapshot = source.copy()
        source.add(0, cat.first { it.id == "chest-iso2" }, null)
        source.swap(0, 0, cat.first { it.id == "chest-iso2" }, null)
        source.removeAdded(0, 0)
        source.applyStrengthRepFix(0, 0)
        assertEquals(snapshot, source)
    }

    @Test
    fun folderValues_fallbackRepsAndWeights() {
        val noRange = fe("c", MuscleGroup.Chest, reps = null, weight = 50.0)
        val routines = full().routines.map { r ->
            r.copy(
                exercises = r.exercises.map {
                    if (it.muscleGroup ==
                        MuscleGroup.Chest
                    ) {
                        noRange.copy(id = it.id)
                    } else {
                        it
                    }
                },
            )
        }
        val block = assertNotNull(full().copy(routines = routines).toBuiltBlock(60, 0, null, emptyMap()))
        val chest = block.sessions[0].exercises.first { it.exerciseId.startsWith("chest") }
        assertEquals(listOf(10, 10), listOf(chest.repMin, chest.repMax))
        assertEquals(50.0, chest.plannedWeightKg)
        // No weight in the folder: the 07 (e) rule from history.
        val other = block.sessions[0].exercises.first { it.exerciseId.startsWith("back") }
        assertNull(other.plannedWeightKg)
        val withHistory =
            assertNotNull(full().toBuiltBlock(60, 0, null, mapOf("back-1" to ExerciseHistory(2, 70.0, 10))))
        assertEquals(70.0, withHistory.sessions[0].exercises.first { it.exerciseId == "back-1" }.plannedWeightKg)
        assertIs<BuiltBlock>(withHistory)
    }
}
