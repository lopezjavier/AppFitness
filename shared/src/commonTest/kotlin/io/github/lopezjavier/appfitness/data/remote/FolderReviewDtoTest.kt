package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.FolderOp
import io.github.lopezjavier.appfitness.domain.model.FolderSetSpec
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderReviewDtoTest {
    private fun opOf(json: String): FolderOp? = coachPlanJson.decodeFromString<FolderChangeDto>(json).toDomain()?.op

    @Test
    fun everyOp_decodesToItsTypedForm() {
        assertEquals(
            FolderOp.AddExercise(
                1,
                "lateral_raise",
                listOf(FolderSetSpec(3, false, 12, 15, 8.5, 75), FolderSetSpec(1, true, 15, 20, 9.0, 60)),
            ),
            opOf(
                """{"id":"c1","op":"add_exercise","routine_index":1,"exercise_key":"lateral_raise","reason":"r",""" +
                    """"sets":[{"n":3,"type":"working","rep_min":12,"rep_max":15,"rpe":8.5,"rest_seconds":75},""" +
                    """{"n":1,"type":"drop","rep_min":15,"rep_max":20,"rpe":9,"rest_seconds":60}]}""",
            ),
        )
        assertEquals(
            FolderOp.ReplaceExercise(2, 3, "k"),
            opOf("""{"id":"c","op":"replace_exercise","routine_index":2,"position":3,"exercise_key":"k"}"""),
        )
        assertEquals(
            FolderOp.RemoveExercise(1, 2),
            opOf("""{"id":"c","op":"remove_exercise","routine_index":1,"position":2}"""),
        )
        assertEquals(
            FolderOp.SetSets(1, 1, 10),
            opOf("""{"id":"c","op":"set_sets","routine_index":1,"position":1,"n":10}"""),
        )
        assertEquals(
            FolderOp.SetRepRange(1, 1, 6, 10),
            opOf("""{"id":"c","op":"set_rep_range","routine_index":1,"position":1,"rep_min":6,"rep_max":10}"""),
        )
        assertEquals(
            FolderOp.SetRpe(1, 1, 8.0),
            opOf("""{"id":"c","op":"set_rpe","routine_index":1,"position":1,"rpe":8}"""),
        )
        assertEquals(
            FolderOp.SetRest(1, 1, 120),
            opOf("""{"id":"c","op":"set_rest","routine_index":1,"position":1,"rest_seconds":120}"""),
        )
    }

    @Test
    fun unknownOp_orMissingField_isNotDecoded() {
        assertNull(opOf("""{"id":"c","op":"teleport","routine_index":1,"position":1}"""))
        assertNull(opOf("""{"id":"c","op":"set_rpe","routine_index":1,"position":1}"""))
    }

    @Test
    fun change_keepsReasonFixesAndConflicts() {
        val change = coachPlanJson.decodeFromString<FolderChangeDto>(
            """{"id":"c2","op":"remove_exercise","routine_index":1,"position":2,"reason":"Sobra","fixes":"H3",""" +
                """"conflicts_with":["c3"]}""",
        ).toDomain()

        assertNotNull(change)
        assertEquals(listOf("c3"), change.conflictsWith)
        assertEquals("H3", change.fixes)
        assertEquals("Sobra", change.reason)
    }

    @Test
    fun goodFolder_hasEmptyFalta() {
        val review = coachPlanJson.decodeFromString<FolderReviewResultDto>(
            """{"review_id":"r1","purpose":"review","message":"Bien","falta":[],"notes":["below_recommended"],""" +
                """"comments":[{"text":"Buen reparto","exercise_key":"squat"}],"changes":[]}""",
        ).toDomain("r1", "f1", ReviewPurpose.Review)

        assertNotNull(review)
        assertTrue(review.falta.isEmpty())
        assertEquals(listOf("below_recommended"), review.notes)
        assertEquals("squat", review.comments.single().exerciseKey)
    }

    @Test
    fun falta_keepsRefsAndStringifiesData() {
        val review = coachPlanJson.decodeFromString<FolderReviewResultDto>(
            """{"falta":[{"code":"H3","routine_index":2,"exercise_key":"k","data":{"min":6,"group":"side_delts"}}]}""",
        ).toDomain("r1", "f1", ReviewPurpose.Block)

        val item = assertNotNull(review).falta.single()
        assertEquals("H3", item.code)
        assertEquals(2, item.routineIndex)
        assertEquals("k", item.exerciseKey)
        assertEquals(mapOf("min" to "6", "group" to "side_delts"), item.data)
    }

    @Test
    fun reviewWithAnUnknownOp_isNotDecoded() {
        val review = coachPlanJson.decodeFromString<FolderReviewResultDto>(
            """{"changes":[{"id":"c1","op":"teleport","routine_index":1}]}""",
        ).toDomain("r1", "f1", ReviewPurpose.Review)

        assertNull(review)
    }

    @Test
    fun storedRow_decodesItsResultAndPurpose() {
        val row = coachPlanJson.decodeFromString<StoredFolderReviewDto>(
            """{"id":"r1","folder_id":"f1","purpose":"block","plan_id":"p1","input_hash":"h",""" +
                """"result":{"message":"m","falta":[],"notes":[],"comments":[],"changes":[]}}""",
        ).toDomain()

        assertNotNull(row)
        assertEquals(ReviewPurpose.Block, row.purpose)
        assertEquals("f1", row.folderId)
    }
}
