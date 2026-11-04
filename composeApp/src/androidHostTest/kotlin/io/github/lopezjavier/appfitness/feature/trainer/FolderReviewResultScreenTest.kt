package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-95: the score and checklist of a folder, from the stateless screen (one test per Gherkin scenario). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class FolderReviewResultScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<FolderReviewResultEvent>()
    private var backs = 0

    private val groups = listOf(
        MuscleGroup.Chest,
        MuscleGroup.Back,
        MuscleGroup.Shoulders,
        MuscleGroup.Quads,
        MuscleGroup.Hamstrings,
        MuscleGroup.Glutes,
    )

    private fun metItems() = groups.map { ReviewItem.Group(it, 12.0, 0, none = false, met = true) } +
        ReviewItem.Days(3, met = true)

    private fun content(
        score: Double = 10.0,
        items: List<ReviewItem> = metItems(),
        strength: Boolean = false,
        folderName: String? = "Fuerza",
    ) = FolderReviewResultUiState.Content(
        folderName = folderName,
        score = score,
        strength = strength,
        complete = items.all { it.met },
        liftingRoutines = 3,
        items = items,
    )

    private fun show(state: FolderReviewResultUiState) {
        rule.setContent {
            AppTheme { FolderReviewResultScreen(state, { events += it }, { backs++ }) }
        }
        rule.waitForIdle()
    }

    private fun fraction(value: Float) = SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo,
        ProgressBarRangeInfo(value, 0f..1f),
    )

    private fun unmetGroup(group: MuscleGroup, sets: Double, missing: Int) =
        ReviewItem.Group(group, sets, missing, none = sets == 0.0, met = false)

    @Test
    fun reviewing_showsTheScoreAndOneRowPerGroupThenTheDays_inOrder() {
        val items = listOf(unmetGroup(MuscleGroup.Chest, 7.5, 3)) + metItems().drop(1)
        show(content(score = 7.5, items = items))

        rule.onNodeWithText("Revisar tus rutinas").assertExists()
        rule.onNodeWithTag("folder_review_result_score", useUnmergedTree = true).assert(fraction(0.75f))
        rule.onNodeWithTag("folder_review_result_score_value", useUnmergedTree = true).assertTextEquals("7,5")
        rule.onNodeWithTag("folder_review_result_score_band", useUnmergedTree = true).assertTextEquals("Aceptable")
        listOf("chest", "back", "shoulders", "quads", "hamstrings", "glutes", "days").forEach {
            rule.onNodeWithTag("folder_review_result_row_$it").assertExists()
        }
        rule.onAllNodesWithTag("folder_review_result_row_reps").assertCountEquals(0)
        rule.onAllNodesWithTag("folder_review_result_row_order").assertCountEquals(0)
        rule.onNodeWithTag("folder_review_result_summary")
            .assertTextEquals("Carpeta «Fuerza». Es una guía: no cambia ni bloquea tus rutinas.")
    }

    @Test
    fun aGoodFolder_showsTenAllMetRowsAndTheCompleteLine() {
        show(content())

        rule.onNodeWithTag("folder_review_result_score", useUnmergedTree = true).assert(fraction(1f))
        rule.onNodeWithTag("folder_review_result_score_value", useUnmergedTree = true).assertTextEquals("10,0")
        rule.onNodeWithTag("folder_review_result_score_band", useUnmergedTree = true).assertTextEquals("Completa")
        rule.onNodeWithTag("folder_review_result_complete")
            .assertTextEquals("Todos los grupos principales tienen 10 o más series a la semana en 3 días.")
        rule.onAllNodesWithTag("folder_review_result_summary").assertCountEquals(0)
        rule.onAllNodesWithText("12 series a la semana.").assertCountEquals(6)
    }

    @Test
    fun anUnmetRow_showsItsShortRecommendation_andNothingIsTappable() {
        val items = metItems().map {
            if ((it as? ReviewItem.Group)?.group ==
                MuscleGroup.Back
            ) {
                unmetGroup(MuscleGroup.Back, 7.5, 3)
            } else {
                it
            }
        }
        show(content(score = 9.0, items = items))

        rule.onNodeWithText("7,5 de 10 series a la semana. Añade 3 más.").assertExists()
        rule.onNodeWithTag("folder_review_result_row_back").assertHasNoClickAction()
        rule.onNodeWithTag("folder_review_result_row_chest").assertHasNoClickAction()
    }

    @Test
    fun aGroupWithNoSets_isRecommendedAtLeastTenAndTheScoreIsCapped() {
        val items = listOf(unmetGroup(MuscleGroup.Glutes, 0.0, 10)) + metItems().dropLast(2) + ReviewItem.Days(3, true)
        show(content(score = 6.0, items = items))

        rule.onNodeWithTag("folder_review_result_score", useUnmergedTree = true).assert(fraction(0.6f))
        rule.onNodeWithTag("folder_review_result_score_value", useUnmergedTree = true).assertTextEquals("6,0")
        rule.onNodeWithTag("folder_review_result_score_band", useUnmergedTree = true).assertTextEquals("Aceptable")
        rule.onNodeWithText("Sin series a la semana. Añade al menos 10 a la semana.").assertExists()
    }

    @Test
    fun fewDays_areRecommendedToBeSplit() {
        show(content(score = 7.5, items = metItems().dropLast(1) + ReviewItem.Days(1, met = false)))

        rule.onNodeWithText(
            "Solo 1 día de entreno; se recomiendan al menos 2. Si puedes, reparte este trabajo en 2 rutinas.",
        ).assertExists()
    }

    @Test
    fun forStrength_theHeavyRepsAndOrderRowsListEachOffender() {
        val reps = ReviewItem.Reps(listOf(RepLine("Torso", "Press banca", 10, 12)))
        val order = ReviewItem.Order(listOf(OrderLine("Torso", "Press banca", "Curl", listOf("Press banca"))))
        show(content(score = 8.0, items = metItems() + reps + order, strength = true))

        rule.onNodeWithTag("folder_review_result_row_reps").assertExists()
        rule.onNodeWithTag("folder_review_result_row_order").assertExists()
        rule.onNodeWithText("Press banca (Torso): 10-12 repeticiones; para fuerza, hasta 8. Cámbialo a 6-8.")
            .assertExists()
        rule.onNodeWithText("Torso: Press banca va después de Curl. Pon primero Press banca.").assertExists()
    }

    @Test
    fun forStrength_aCompleteFolderAddsTheHeavyAndFirstSentence() {
        show(
            content(items = metItems() + ReviewItem.Reps(emptyList()) + ReviewItem.Order(emptyList()), strength = true),
        )

        rule.onNodeWithTag("folder_review_result_complete").assertTextEquals(
            "Todos los grupos principales tienen 10 o más series a la semana en 3 días. " +
                "Los ejercicios principales son pesados y van primero.",
        )
    }

    @Test
    fun helpButton_opensTheExplanationOfTheScore() {
        show(content())

        rule.onNodeWithTag("folder_review_result_score_help").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Cómo se calcula").assertExists()
    }

    @Test
    fun anEmptyFolder_showsTheEmptyStateAndNoScore() {
        show(FolderReviewResultUiState.Empty)

        rule.onNodeWithText("Esta carpeta no tiene rutinas").assertExists()
        rule.onAllNodesWithTag("folder_review_result_score").assertCountEquals(0)
    }

    @Test
    fun aFailedLoad_offersReintentar_andRetrySendsTheEvent() {
        show(FolderReviewResultUiState.Error)

        rule.onNodeWithText("No se pudo revisar tus rutinas. Inténtalo de nuevo.").assertExists()
        rule.onNodeWithTag("folder_review_result_retry").performClick()

        assertEquals(listOf<FolderReviewResultEvent>(FolderReviewResultEvent.Retry), events)
    }

    @Test
    fun loading_showsTheSkeleton() {
        show(FolderReviewResultUiState.Loading)

        rule.onNodeWithTag("folder_review_result_loading").assertExists()
    }

    @Test
    fun back_leavesWithoutSendingAnEvent() {
        show(content())

        rule.onNodeWithTag("folder_review_result_back").performClick()

        assertEquals(1, backs)
        assertEquals(emptyList<FolderReviewResultEvent>(), events)
    }
}
