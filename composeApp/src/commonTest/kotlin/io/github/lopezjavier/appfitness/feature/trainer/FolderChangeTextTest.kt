package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.FolderChange
import io.github.lopezjavier.appfitness.domain.model.FolderFalta
import io.github.lopezjavier.appfitness.domain.model.FolderOp
import io.github.lopezjavier.appfitness.domain.model.FolderReview
import io.github.lopezjavier.appfitness.domain.model.FolderSetSpec
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun folderContext() = FolderContext(
    routines = listOf(
        FolderRoutine("Torso", listOf("id-bench", "id-row")),
        FolderRoutine("Pierna", listOf("id-squat")),
    ),
    idNames = mapOf(
        "id-bench" to "Press banca",
        "id-row" to "Remo",
        "id-squat" to "Sentadilla",
        "id-custom" to "Mi curl",
    ),
    catalogue = mapOf("curl" to "Curl de biceps", "bench" to "Press banca"),
)

internal fun change(
    id: String,
    op: FolderOp = FolderOp.SetSets(1, 1, 4),
    fixes: String? = null,
    conflictsWith: List<String> = emptyList(),
) = FolderChange(id, op, "Motivo $id", fixes, conflictsWith)

internal fun falta(code: String, routine: Int? = null, key: String? = null) =
    FolderFalta(code, routine, key, emptyMap())

internal fun review(
    changes: List<FolderChange> = emptyList(),
    falta: List<FolderFalta> = emptyList(),
    purpose: ReviewPurpose = ReviewPurpose.Review,
) = FolderReview("rev-1", "folder-1", purpose, falta, emptyList(), "Mensaje", emptyList(), changes)

class FolderChangeTextTest {
    private val context = folderContext()

    @Test
    fun addExercise_namesTheCatalogueExerciseAndCountsTheSets() {
        val sets = List(3) { FolderSetSpec(it + 1, false, 8, 12, 8.0, 90) }

        val line = FolderOp.AddExercise(2, "curl", sets).toLine(context)

        assertEquals(ChangeLine(ChangeKind.Add, 2, "Pierna", "Curl de biceps", listOf("3")), line)
    }

    @Test
    fun addExercise_withACustomKeyUsesTheCustomName() {
        val line = FolderOp.AddExercise(1, "id-custom", emptyList()).toLine(context)

        assertEquals("Mi curl", line?.exerciseName)
    }

    @Test
    fun replaceExercise_namesTheOldExerciseAtThePositionAndTheNewOne() {
        val line = FolderOp.ReplaceExercise(1, 2, "curl").toLine(context)

        assertEquals(ChangeLine(ChangeKind.Replace, 1, "Torso", "Remo", listOf("Curl de biceps")), line)
    }

    @Test
    fun removeExercise_namesTheExerciseAtThePosition() {
        val line = FolderOp.RemoveExercise(1, 1).toLine(context)

        assertEquals(ChangeLine(ChangeKind.Remove, 1, "Torso", "Press banca", emptyList()), line)
    }

    @Test
    fun setSets_carriesTheNewCount() {
        assertEquals(listOf("4"), FolderOp.SetSets(1, 1, 4).toLine(context)?.extra)
    }

    @Test
    fun setRepRange_carriesMinAndMax() {
        val line = FolderOp.SetRepRange(2, 1, 6, 10).toLine(context)

        assertEquals(ChangeLine(ChangeKind.SetRepRange, 2, "Pierna", "Sentadilla", listOf("6", "10")), line)
    }

    @Test
    fun setRpe_dropsTheFractionOfAWholeNumberAndKeepsHalves() {
        assertEquals(listOf("8"), FolderOp.SetRpe(1, 1, 8.0).toLine(context)?.extra)
        assertEquals(listOf("8.5"), FolderOp.SetRpe(1, 1, 8.5).toLine(context)?.extra)
    }

    @Test
    fun setRest_carriesTheSeconds() {
        assertEquals(listOf("120"), FolderOp.SetRest(1, 2, 120).toLine(context)?.extra)
    }

    @Test
    fun unknownRoutineName_isNullSoTheScreenCanSayRoutineN() {
        val line = FolderOp.AddExercise(5, "curl", emptyList()).toLine(context)

        assertNull(line?.routineName)
        assertEquals(5, line?.routineIndex)
    }

    @Test
    fun missingExerciseName_givesNoLine() {
        assertNull(FolderOp.RemoveExercise(1, 9).toLine(context))
        assertNull(FolderOp.AddExercise(1, "unknown-key", emptyList()).toLine(context))
        assertNull(FolderOp.ReplaceExercise(1, 1, "unknown-key").toLine(context))
    }

    @Test
    fun faltaItem_resolvesTheRoutineAndTheExercise() {
        val item = context.itemOf(falta("rep_range", routine = 2, key = "bench"))

        assertEquals("Pierna", item.routineName)
        assertEquals("Press banca", item.exerciseName)
        assertTrue(context.itemOf(falta("days")).routineName == null)
    }
}
