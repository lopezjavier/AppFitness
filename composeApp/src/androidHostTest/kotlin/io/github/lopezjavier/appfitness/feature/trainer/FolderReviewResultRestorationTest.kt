package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.usecase.DaysRow
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewResult
import io.github.lopezjavier.appfitness.domain.usecase.GroupRow
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: leaving the app and coming back keeps the review; it is recomputed from the folder, never asked online. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class FolderReviewResultRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val routines = FakeRoutineRepository().apply { folders += RoutineFolder("folder-1", "Fuerza") }
    private val saved = SavedStateHandle(mapOf("folderId" to "folder-1"))
    private val computed = mutableListOf<String>()
    private var chestSets = 8.0

    private fun newModel(handle: SavedStateHandle = saved) = FolderReviewResultViewModel(
        review = { folderId ->
            computed += folderId
            val rows = listOf(GroupRow(MuscleGroup.Chest, chestSets), DaysRow(3))
            Result.success(FolderReviewResult(7.5, rows, rows.filter { !it.met }, 3, 3, strength = false))
        },
        names = ExerciseNameResolver(FakeExerciseRepository(emptyList()), FakeExerciseLibrary(emptyMap())),
        routines = routines,
        savedState = handle,
    )

    private var current by mutableStateOf(newModel())

    @Test
    fun theScreen_survivesRecreation_andKeepsTheHelpSheetOpen() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { AppTheme { FolderReviewResultRoot(onBack = {}, viewModel = current) } }
        rule.waitForIdle()
        rule.onNodeWithTag("folder_review_result_score_help").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("folder_review_result_score_help_sheet").assertExists()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("folder_review_result_score_help_sheet").assertExists()
        rule.onNodeWithTag("folder_review_result_row_chest").assertExists()
    }

    @Test
    fun aRecreatedModel_restoresTheFolder_andRecomputesFromTheCurrentRoutines() {
        rule.setContent { AppTheme { FolderReviewResultRoot(onBack = {}, viewModel = current) } }
        rule.waitForIdle()
        rule.onNodeWithText("8 series (se recomiendan al menos 10)", substring = true).assertExists()
        rule.onNodeWithTag("folder_review_result_row_chest_help").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Añade 2 series de ejercicios", substring = true).assertExists()
        assertEquals(listOf("folder-1"), computed)

        chestSets = 12.0
        current = newModel()
        rule.waitForIdle()

        assertEquals(listOf("folder-1", "folder-1"), computed)
        rule.onNodeWithText("12 series (se recomiendan al menos 10)", substring = true).assertExists()

        chestSets = 25.0
        current = newModel()
        rule.waitForIdle()

        rule.onNodeWithTag("folder_review_result_row_chest_help").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Más de 20 series a la semana", substring = true).assertExists()
        rule.onNodeWithTag("folder_review_result_row_chest").assertExists()
    }
}
