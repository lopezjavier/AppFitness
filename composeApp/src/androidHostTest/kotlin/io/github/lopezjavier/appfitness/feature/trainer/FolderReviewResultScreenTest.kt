package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextContains
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

    private fun metItems() = groups.map { ReviewItem.Group(it, 12.0, 0, none = false, over = false, met = true) } +
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
        ReviewItem.Group(group, sets, missing, none = sets == 0.0, over = false, met = false)

    private fun metGroup(group: MuscleGroup, sets: Double) =
        ReviewItem.Group(group, sets, 0, none = false, over = sets > 20.0, met = true)

    private fun withGroup(item: ReviewItem.Group) = metItems().map {
        if ((it as? ReviewItem.Group)?.group == item.group) item else it
    }

    private fun hasText(text: String) = rule.onNodeWithText(text, substring = true)

    private fun openHelp(suffix: String) {
        rule.onNodeWithTag("folder_review_result_row_${suffix}_help").performClick()
        rule.waitForIdle()
    }

    @Test
    fun reviewing_showsTheScoreAndOneRowPerGroupThenTheDays_inOrder() {
        val items = withGroup(unmetGroup(MuscleGroup.Chest, 7.5, 3))
        show(content(score = 7.5, items = items))

        rule.onNodeWithTag("folder_review_result_score", useUnmergedTree = true).assert(fraction(0.75f))
        rule.onNodeWithTag("folder_review_result_score_value", useUnmergedTree = true).assertTextEquals("7,5")
        rule.onNodeWithTag("folder_review_result_score_band", useUnmergedTree = true).assertTextEquals("Aceptable")
        listOf("chest", "back", "shoulders", "quads", "hamstrings", "glutes", "days").forEach {
            rule.onNodeWithTag("folder_review_result_row_$it").assertExists()
        }
        rule.onAllNodesWithTag("folder_review_result_row_reps").assertCountEquals(0)
        rule.onAllNodesWithTag("folder_review_result_row_order").assertCountEquals(0)
    }

    @Test
    fun theTitle_carriesTheFolderName() {
        show(content(folderName = "Fuerza"))

        rule.onNodeWithText("Revisión de «Fuerza»").assertExists()
    }

    @Test
    fun withoutAFolderName_theTitleIsThePlainFallback() {
        show(content(folderName = null))

        rule.onNodeWithText("Revisión de la carpeta").assertExists()
    }

    @Test
    fun noSummaryLine_andTheWordPatronAppearsNowhere() {
        show(content(items = withGroup(unmetGroup(MuscleGroup.Back, 4.0, 6)), strength = true))

        rule.onAllNodesWithTag("folder_review_result_summary").assertCountEquals(0)
        rule.onAllNodesWithText("Es una guía", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Carpeta «Fuerza»", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("patrón", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun aGoodFolder_showsTenAllMetRowsAndTheCompleteLine() {
        show(content())

        rule.onNodeWithTag("folder_review_result_score", useUnmergedTree = true).assert(fraction(1f))
        rule.onNodeWithTag("folder_review_result_score_value", useUnmergedTree = true).assertTextEquals("10,0")
        rule.onNodeWithTag("folder_review_result_score_band", useUnmergedTree = true).assertTextEquals("Completa")
        rule.onNodeWithTag("folder_review_result_complete")
            .assertTextEquals("Todos los grupos principales tienen 10 o más series a la semana en 3 días.")
        rule.onAllNodesWithText("12 series (se recomiendan al menos 10)", substring = true).assertCountEquals(6)
        rule.onAllNodesWithText("Añade", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Por ejemplo", substring = true).assertCountEquals(0)
    }

    @Test
    fun aNotCompleteFolder_hasNoCompleteLine() {
        show(content(score = 9.0, items = withGroup(unmetGroup(MuscleGroup.Back, 7.5, 3))))

        rule.onAllNodesWithTag("folder_review_result_complete").assertCountEquals(0)
    }

    @Test
    fun aMetGroup_showsItsFigureWithADecimalComma() {
        show(content(items = withGroup(metGroup(MuscleGroup.Chest, 10.5))))

        hasText("10,5 series (se recomiendan al menos 10)").assertExists()
    }

    @Test
    fun aGroupWithOneSet_usesTheSingular() {
        show(content(score = 9.0, items = withGroup(unmetGroup(MuscleGroup.Chest, 1.0, 9))))

        hasText("1 serie (se recomiendan al menos 10)").assertExists()
        rule.onAllNodesWithText("Añade 9 series", substring = true).assertCountEquals(0)
        openHelp("chest")
        hasText("Añade 9 series de ejercicios donde sea el músculo principal").assertExists()
    }

    @Test
    fun anUnmetGroup_showsOnlyTheFigure_andTheAdviceAndExampleSitBehindTheQuestionMark() {
        show(content(score = 9.0, items = withGroup(unmetGroup(MuscleGroup.Back, 4.0, 6))))

        hasText("4 series (se recomiendan al menos 10)").assertExists()
        rule.onAllNodesWithText("Añade 6 series", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Por ejemplo", substring = true).assertCountEquals(0)
        openHelp("back")
        hasText(
            "Añade 6 series de ejercicios donde sea el músculo principal; donde solo ayuda cuentan la mitad.",
        ).assertExists()
        hasText("Por ejemplo: remos, jalones al pecho, dominadas.").assertExists()
    }

    @Test
    fun theRows_areNotTappable() {
        show(content(score = 9.0, items = withGroup(unmetGroup(MuscleGroup.Back, 4.0, 6))))

        rule.onNodeWithTag("folder_review_result_row_back").assertHasNoClickAction()
        rule.onNodeWithTag("folder_review_result_row_chest").assertHasNoClickAction()
    }

    @Test
    fun everyUnmetGroup_hasAQuestionMark() {
        val items = metItems().map { item ->
            (item as? ReviewItem.Group)?.let { unmetGroup(it.group, 4.0, 6) } ?: item
        }
        show(content(score = 5.0, items = items))

        listOf("chest", "back", "shoulders", "quads", "hamstrings", "glutes").forEach {
            rule.onNodeWithTag("folder_review_result_row_${it}_help").assertExists()
        }
    }

    @Test
    fun theGlutesExample_isBehindItsQuestionMark() {
        show(content(score = 9.0, items = withGroup(unmetGroup(MuscleGroup.Glutes, 4.0, 6))))

        openHelp("glutes")

        hasText(
            "Por ejemplo: empuje de cadera, puente de glúteos (sentadillas y zancadas cuentan la mitad).",
        ).assertExists()
    }

    @Test
    fun aGroupWithNoSets_isRecommendedAtLeastTenAndTheScoreIsCapped() {
        val items = withGroup(unmetGroup(MuscleGroup.Glutes, 0.0, 10))
        show(content(score = 6.0, items = items))

        rule.onNodeWithTag("folder_review_result_score", useUnmergedTree = true).assert(fraction(0.6f))
        rule.onNodeWithTag("folder_review_result_score_value", useUnmergedTree = true).assertTextEquals("6,0")
        rule.onNodeWithTag("folder_review_result_score_band", useUnmergedTree = true).assertTextEquals("Aceptable")
        hasText("Sin series (se recomiendan al menos 10)").assertExists()
        openHelp("glutes")
        hasText("Añade al menos 10 series de ejercicios donde sea el músculo principal").assertExists()
    }

    @Test
    fun moreThanTwentySets_keepTheRowMetWithANeutralNote() {
        show(content(items = withGroup(metGroup(MuscleGroup.Chest, 25.0))))

        hasText("25 series (se recomiendan al menos 10)").assertExists()
        rule.onAllNodesWithText("Más de 20 series", substring = true).assertCountEquals(0)
        openHelp("chest")
        hasText("Más de 20 series a la semana: por encima de unas 20, añadir más series aporta poco.").assertExists()
        rule.onNodeWithTag("folder_review_result_row_chest").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Cumplido"),
        )
        rule.onNodeWithTag("folder_review_result_complete").assertExists()
    }

    @Test
    fun exactlyTwentySets_showNoNote() {
        show(content(items = withGroup(metGroup(MuscleGroup.Chest, 20.0))))

        rule.onAllNodesWithText("Más de 20 series", substring = true).assertCountEquals(0)
    }

    @Test
    fun aMetGroupWithNothingToAdvise_hasNoQuestionMark() {
        show(content(items = withGroup(metGroup(MuscleGroup.Chest, 12.0))))

        rule.onAllNodesWithTag("folder_review_result_row_chest_help").assertCountEquals(0)
        rule.onAllNodesWithTag("folder_review_result_row_days_help").assertCountEquals(0)
    }

    @Test
    fun fewDays_areRecommendedToBeSplit() {
        show(content(score = 7.5, items = metItems().dropLast(1) + ReviewItem.Days(1, met = false)))

        rule.onNodeWithText("Solo 1 día de entreno; se recomiendan al menos 2.").assertExists()
        rule.onAllNodesWithText("Si puedes", substring = true).assertCountEquals(0)
        rule.onAllNodesWithTag("folder_review_result_row_days_help").assertCountEquals(1)
        rule.onNodeWithTag("folder_review_result_row_days_help").performClick()
        rule.onNodeWithTag("folder_review_result_row_days_help_sheet_body")
            .assertTextContains("Si puedes, reparte este trabajo en 2 rutinas.", substring = true)
    }

    @Test
    fun forStrength_theHeavyRepsAndOrderRowsListEachOffender() {
        val reps = ReviewItem.Reps(listOf(RepLine("Torso", "Press banca", 10, 12)))
        val order = ReviewItem.Order(listOf(OrderLine("Torso", "Press banca", "Curl", listOf("Press banca"))))
        show(content(score = 8.0, items = metItems() + reps + order, strength = true))

        rule.onNodeWithTag("folder_review_result_row_reps").assertExists()
        rule.onNodeWithTag("folder_review_result_row_order").assertExists()
        rule.onNodeWithText("Press banca (Torso): 10-12 repeticiones; para fuerza, hasta 8.").assertExists()
        rule.onNodeWithText("Torso: Press banca va después de Curl.").assertExists()
        rule.onAllNodesWithText("Cámbialo", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Pon primero", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithTag("folder_review_result_row_reps_help").assertCountEquals(1)
        rule.onAllNodesWithTag("folder_review_result_row_order_help").assertCountEquals(1)
        rule.onNodeWithTag("folder_review_result_row_reps_help").performClick()
        rule.onNodeWithTag("folder_review_result_row_reps_help_sheet_body")
            .assertTextContains("Press banca: cámbialo a 6-8.", substring = true)
        rule.onNodeWithTag("folder_review_result_row_reps_help_sheet_close").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("folder_review_result_row_order_help").performClick()
        rule.onNodeWithTag("folder_review_result_row_order_help_sheet_body")
            .assertTextContains("Torso: pon primero Press banca.", substring = true)
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
    fun helpButton_forAnotherIntention_explainsTheChecksWithoutTheStrengthSentence() {
        show(content())

        rule.onNodeWithTag("folder_review_result_score_help").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Cómo se calcula").assertExists()
        hasText("con al menos 10 cada uno").assertExists()
        hasText("Por encima de 20 series en un grupo solo se avisa, sin restar nota.").assertExists()
        rule.onAllNodesWithText("Como buscas fuerza", substring = true).assertCountEquals(0)
    }

    @Test
    fun helpButton_forStrength_appendsTheStrengthSentence() {
        show(content(strength = true))

        rule.onNodeWithTag("folder_review_result_score_help").performClick()
        rule.waitForIdle()

        hasText("Como buscas fuerza, también revisa que los ejercicios principales").assertExists()
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
