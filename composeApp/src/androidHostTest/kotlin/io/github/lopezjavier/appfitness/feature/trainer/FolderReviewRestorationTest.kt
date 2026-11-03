package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FolderOp
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.routines.routineOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: "Revisar carpeta" keeps the review and the unchecked changes, and never bills or writes again by itself. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class FolderReviewRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val reviews = FakeFolderReviewRepository()
    private val routines = FakeRoutineRepository(
        listOf(routineOf("r1", "Torso", "id-bench", "id-row"), routineOf("r2", "Pierna", "id-squat")),
    ).apply {
        folderOf["r1"] = "folder-1"
        folderOf["r2"] = "folder-1"
    }
    private val saved = SavedStateHandle(mapOf("folderId" to "folder-1", "purpose" to "review"))

    private fun newModel(handle: SavedStateHandle = saved) = folderReviewModel(reviews, routines, handle)

    private var current by mutableStateOf(newModel())

    private fun givenReview() {
        val review = review(
            listOf(change("c1", FolderOp.SetSets(1, 1, 4)), change("c2", FolderOp.RemoveExercise(1, 2))),
            purpose = ReviewPurpose.Review,
        )
        reviews.reviewResult = Result.success(review)
        reviews.storedResult = Result.success(review)
    }

    private fun showRoot() {
        rule.setContent {
            AppTheme {
                FolderReviewRoot({}, {}, {}, viewModel = current, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
    }

    private fun recreate(handle: SavedStateHandle = saved) {
        current = newModel(handle)
        rule.waitForIdle()
    }

    @Test
    fun theUncheckedChange_survivesRecreation_andTheCoachIsNotAskedAgain() {
        givenReview()
        showRoot()
        rule.onNodeWithTag("folder_review_change_c2").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("folder_review_change_c2").assertIsOff()

        recreate()

        rule.onNodeWithTag("folder_review_change_c2").assertIsOff()
        rule.onNodeWithTag("folder_review_change_c1").assertIsOn()
        assertEquals(1, reviews.reviewCalls)
        assertEquals(listOf("rev-1"), reviews.storedReads)
    }

    @Test
    fun aReviewAskedButNotReceived_showsInterrupted_andDoesNotAskAgain() {
        val handle = SavedStateHandle(
            mapOf(
                "folderId" to "folder-1",
                "purpose" to "review",
                "folder_review_pending" to true,
            ),
        )

        current = newModel(handle)
        showRoot()

        rule.onNodeWithText("No se recibió la revisión. Inténtalo de nuevo.").assertExists()
        assertEquals(0, reviews.reviewCalls)
    }

    @Test
    fun recreation_neverRepeatsTheCopy() {
        givenReview()
        showRoot()
        rule.onNodeWithTag("folder_review_apply").performClick()
        rule.waitForIdle()
        assertEquals(1, reviews.applied.size)

        recreate()

        assertEquals(1, reviews.applied.size)
        assertTrue(reviews.blocks.isEmpty())
    }
}
