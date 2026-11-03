package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.RecordingAppHaptics
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.FolderOp
import io.github.lopezjavier.appfitness.domain.model.ReviewComment
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.routines.routineOf
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-95 and US-93: "Revisar carpeta", from the stateless screen to the root with a real model over fakes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class FolderReviewScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<FolderReviewEvent>()
    private var backs = 0

    private val removeRow = ChangeRow(
        change("c1", FolderOp.RemoveExercise(1, 2)),
        ChangeLine(ChangeKind.Remove, 1, "Torso", "Remo", emptyList()),
        checked = true,
    )
    private val setsRow = ChangeRow(
        change("c2", FolderOp.SetSets(2, 1, 4), fixes = "h1_volume_low"),
        ChangeLine(ChangeKind.SetSets, 2, null, "Sentadilla", listOf("4")),
        checked = true,
    )

    private fun content(
        purpose: ReviewPurpose = ReviewPurpose.Review,
        rows: List<ChangeRow> = listOf(removeRow, setsRow),
        falta: List<FaltaItem> = emptyList(),
        accepted: Set<String> = rows.filter { it.checked }.map { it.id }.toSet(),
        comments: List<ReviewComment> = emptyList(),
        blocked: List<FaltaItem> = emptyList(),
    ) = FolderReviewUiState.Content(
        review = review(rows.map { it.change }, falta.map { it.falta }, purpose).copy(comments = comments),
        accepted = accepted,
        rows = rows,
        allFalta = falta,
        blockedFalta = blocked,
    )

    private val faltaLow = FaltaItem(falta("h1_volume_low", routine = 2), routineName = null, exerciseName = null)

    private fun show(state: FolderReviewUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme { FolderReviewScreen(state, flowOf(online), { events += it }, { backs++ }) }
        }
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot().top

    @Test
    fun reviewing_showsTheMessageFaltaCommentsAndChangesInThatOrder_withReasons() {
        show(
            content(
                rows = listOf(removeRow, setsRow.copy(checked = false)),
                falta = listOf(faltaLow),
                comments = listOf(ReviewComment("Buen reparto de días.", null)),
            ),
        )

        rule.onNodeWithText("Revisar tus rutinas").assertExists()
        assertTrue(top("folder_review_message") < top("folder_review_section_falta"))
        assertTrue(top("folder_review_section_falta") < top("folder_review_section_comments"))
        assertTrue(top("folder_review_section_comments") < top("folder_review_section_changes"))
        rule.onNodeWithText("Algún músculo tiene pocas series a la semana").assertExists()
        rule.onNodeWithText("Rutina 2").assertExists()
        rule.onNodeWithText("Buen reparto de días.").assertExists()
        rule.onNodeWithText("Torso: Quitar Remo").assertExists()
        rule.onNodeWithText("Motivo c1").assertExists()
        rule.onNodeWithText("Rutina 2: Sentadilla: 4 series").assertExists()
    }

    @Test
    fun everyChangeStartsChecked_andTappingARowSendsAToggle() {
        show(content())

        rule.onNodeWithTag("folder_review_change_c1").performClick()

        assertEquals(listOf<FolderReviewEvent>(FolderReviewEvent.Toggle("c1")), events)
    }

    @Test
    fun aGoodFolder_hasNoFaltaSectionAndNoFooter() {
        show(content(rows = emptyList(), comments = listOf(ReviewComment("Todo bien.", null))))

        rule.onAllNodesWithTag("folder_review_section_falta").assertCountEquals(0)
        rule.onAllNodesWithTag("folder_review_section_changes").assertCountEquals(0)
        rule.onNodeWithText("Todo bien.").assertExists()
        rule.onAllNodesWithTag("folder_review_apply").assertCountEquals(0)
        rule.onAllNodesWithTag("folder_review_create_block").assertCountEquals(0)
    }

    @Test
    fun reviewFooter_isAplicarEnUnaCopia_andSendsApply() {
        show(content())

        rule.onNodeWithTag("folder_review_apply").assertTextContains("Aplicar en una copia").performClick()

        assertEquals(listOf<FolderReviewEvent>(FolderReviewEvent.Apply), events)
    }

    @Test
    fun reviewFooter_isDisabledWhenNothingIsChecked() {
        show(content(rows = listOf(removeRow.copy(checked = false)), accepted = emptySet()))

        rule.onNodeWithTag("folder_review_apply").assertIsNotEnabled()
    }

    @Test
    fun nothingIsBlocked_aReviewWithAnOpenFaltaStillOffersTheCopy() {
        show(content(falta = listOf(faltaLow), accepted = emptySet(), rows = listOf(setsRow.copy(checked = false))))

        rule.onNodeWithText("Algún músculo tiene pocas series a la semana").assertExists()
        rule.onAllNodesWithTag("folder_review_disabled_reason").assertCountEquals(0)
        rule.onNodeWithTag("folder_review_apply").assertExists()
    }

    @Test
    fun blockFooter_withAnOpenFalta_isDisabledAndSaysWhy() {
        show(content(purpose = ReviewPurpose.Block, falta = listOf(faltaLow), accepted = emptySet()))

        rule.onNodeWithTag("folder_review_create_block").assertTextContains("Crear bloque").assertIsNotEnabled()
        rule.onNodeWithTag("folder_review_disabled_reason")
            .assertTextContains("Marca los cambios que arreglan lo que falta.")
        rule.onNodeWithTag("folder_review_falta_1").assertExists()
    }

    @Test
    fun blockFooter_withAFaltaNoChangeFixes_explainsTheRoutinesMustBeEdited() {
        val unfixable = FaltaItem(falta("h6_equipment", routine = 1, key = "bench"), "Torso", "Press banca")
        show(content(purpose = ReviewPurpose.Block, falta = listOf(unfixable), rows = listOf(removeRow)))

        rule.onNodeWithTag("folder_review_disabled_reason")
            .assertTextContains("Falta algo que esta carpeta no puede arreglar. Edita tus rutinas y vuelve a revisar.")
        rule.onNodeWithText("Torso · Press banca").assertExists()
    }

    @Test
    fun blockFooter_withNoFalta_isEnabledAndSendsContinue() {
        show(content(purpose = ReviewPurpose.Block))

        rule.onNodeWithTag("folder_review_create_block").assertIsEnabled().performClick()

        assertEquals(listOf<FolderReviewEvent>(FolderReviewEvent.Continue), events)
        rule.onAllNodesWithTag("folder_review_disabled_reason").assertCountEquals(0)
    }

    @Test
    fun whenTheServerRefusesTheBlock_itsFaltaListAndAnInlineNoteAreShown() {
        show(content(purpose = ReviewPurpose.Block, blocked = listOf(faltaLow)))

        rule.onNodeWithText("Aún falta algo para crear el bloque.").assertExists()
        rule.onNodeWithTag("folder_review_falta_1").assertExists()
    }

    @Test
    fun offline_disablesTheFooterAndSaysWhatItNeedsTheNetworkFor() {
        show(content(), online = false)

        rule.onNodeWithTag("folder_review_apply").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede crear la copia").assertExists()
    }

    @Test
    fun requesting_showsTheProgress() {
        show(FolderReviewUiState.Requesting)

        rule.onNodeWithTag("folder_review_progress").assertExists()
        rule.onNodeWithText("Revisando tus rutinas…").assertExists()
    }

    @Test
    fun aFailure_offersReintentar_andRetrySendsTheEvent() {
        show(FolderReviewUiState.Failed(PlanFailureKind.Other))

        rule.onNodeWithText("No se pudo revisar tus rutinas. Inténtalo de nuevo.").assertExists()
        rule.onNodeWithTag("folder_review_retry").performClick()

        assertEquals(listOf<FolderReviewEvent>(FolderReviewEvent.Retry), events)
    }

    @Test
    fun aChangedFolder_asksToReviewAgain() {
        show(FolderReviewUiState.Failed(PlanFailureKind.Other, changed = true))

        rule.onNodeWithText("La carpeta cambió. Vuelve a revisarla.").assertExists()
        rule.onNodeWithTag("folder_review_retry").assertExists()
    }

    @Test
    fun back_leavesWithoutSendingAnEvent() {
        show(content())

        rule.onNodeWithTag("folder_review_back").performClick()

        assertEquals(1, backs)
        assertTrue(events.isEmpty())
    }

    // ---- The root over a real model and fakes ----

    private val reviews = FakeFolderReviewRepository()
    private val routines = FakeRoutineRepository(
        listOf(routineOf("r1", "Torso editado", "id-bench", "id-row"), routineOf("r2", "Pierna", "id-squat")),
    ).apply {
        folderOf["r1"] = "folder-1"
        folderOf["r2"] = "folder-1"
        folders += RoutineFolder("copy-1", "Mi carpeta (revisada)")
    }
    private val haptics = RecordingAppHaptics()
    private val shown = mutableListOf<String>()
    private var applied = 0
    private var blockReady = 0

    private fun viewModel(purpose: String = "review") = folderReviewModel(
        reviews,
        routines,
        SavedStateHandle(mapOf("folderId" to "folder-1", "purpose" to purpose, "planId" to "plan-1")),
    )

    private fun showRoot(model: FolderReviewViewModel) {
        val messages = FolderReviewMessages(shown)
        rule.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics, LocalAppMessages provides messages) {
                AppTheme {
                    FolderReviewRoot(
                        onFinishCopy = { applied++ },
                        onFinishBlock = { blockReady++ },
                        onBack = { backs++ },
                        viewModel = model,
                        connectivity = FakeConnectivityObserver(),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun givenReview(purpose: ReviewPurpose = ReviewPurpose.Review) {
        reviews.reviewResult = Result.success(
            review(
                listOf(
                    change("c1", FolderOp.SetSets(1, 1, 4)),
                    change("c2", FolderOp.RemoveExercise(1, 2)),
                ),
                purpose = purpose,
            ),
        )
    }

    @Test
    fun root_asksTheCoachOnce_andShowsTheEditedRoutineNames() {
        givenReview()

        showRoot(viewModel())

        assertEquals(1, reviews.reviewCalls)
        rule.onNodeWithText("Torso editado: Press banca: 4 series").assertExists()
        rule.onNodeWithText("Torso editado: Quitar Remo").assertExists()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun applyingInACopy_sendsOnlyTheCheckedChanges_confirmsAndShowsTheServerName() {
        givenReview()
        showRoot(viewModel())
        rule.onNodeWithTag("folder_review_change_c2").performClick()

        rule.onNodeWithTag("folder_review_apply").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) { shown.isNotEmpty() }

        assertEquals(listOf("rev-1" to listOf("c1")), reviews.applied)
        assertEquals(listOf("Carpeta «Mi carpeta (revisada)» creada."), shown)
        assertEquals(listOf(HapticEvent.Confirm), haptics.calls)
        assertEquals(1, applied)
    }

    @Test
    fun leavingWithoutApplying_createsNothing() {
        givenReview()
        showRoot(viewModel())

        rule.onNodeWithTag("folder_review_back").performClick()

        assertEquals(1, backs)
        assertTrue(reviews.applied.isEmpty())
        assertTrue(reviews.blocks.isEmpty())
    }

    @Test
    fun applyFailing_showsTheFailureMessageAndKeepsTheChecklist() {
        givenReview()
        reviews.applyResult = Result.failure(IllegalStateException("boom"))
        showRoot(viewModel())

        rule.onNodeWithTag("folder_review_apply").performClick()
        rule.waitForIdle()

        assertEquals(listOf("No se pudo crear la copia. Inténtalo de nuevo."), shown)
        assertEquals(listOf(HapticEvent.Reject), haptics.calls)
        rule.onNodeWithTag("folder_review_change_c1").assertExists()
        assertEquals(0, applied)
    }

    @Test
    fun block_createsFromTheCheckedChanges_andMovesOn() {
        reviews.reviewResult = Result.success(
            review(
                listOf(change("c1", FolderOp.SetSets(1, 1, 4), fixes = "h1_volume_low")),
                listOf(falta("h1_volume_low", 1)),
                ReviewPurpose.Block,
            ),
        )
        showRoot(viewModel("block"))
        rule.onNodeWithTag("folder_review_create_block").assertIsEnabled().performClick()
        rule.waitForIdle()

        assertEquals(listOf(listOf("c1")), reviews.blocks)
        assertEquals(listOf("plan-1"), reviews.blockPlans)
        assertEquals(1, blockReady)
    }

    @Test
    fun block_uncheckingTheRequiredChange_showsFaltaAndCreatesNothing() {
        reviews.reviewResult = Result.success(
            review(
                listOf(change("c1", FolderOp.SetSets(1, 1, 4), fixes = "h1_volume_low")),
                listOf(falta("h1_volume_low", 1)),
                ReviewPurpose.Block,
            ),
        )
        showRoot(viewModel("block"))

        rule.onNodeWithTag("folder_review_change_c1").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("folder_review_create_block").assertIsNotEnabled()
        rule.onNodeWithTag("folder_review_falta_1").assertExists()
        assertTrue(reviews.blocks.isEmpty())
        assertEquals(0, blockReady)
    }

    @Test
    fun block_whenTheServerNeedsChanges_staysOnTheScreenWithTheFaltaList() {
        reviews.reviewResult = Result.success(review(listOf(change("c1")), purpose = ReviewPurpose.Block))
        reviews.blockResult = Result.failure(
            CoachException(CoachFailure.NeedsChanges(listOf(falta("h7_order", 1)))),
        )
        showRoot(viewModel("block"))

        rule.onNodeWithTag("folder_review_create_block").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Aún falta algo para crear el bloque.").assertExists()
        rule.onNodeWithText("Los ejercicios principales deben ir primero").assertExists()
        assertEquals(0, blockReady)
    }
}

private class FolderReviewMessages(private val shown: MutableList<String>) : AppMessages(CoroutineScope(Job())) {
    override fun show(text: String) {
        shown += text
    }
}

/** A model over fakes for the folder "folder-1" (Press banca, Remo, Sentadilla); shared by the screen tests. */
internal fun folderReviewModel(
    reviews: FakeFolderReviewRepository,
    routines: FakeRoutineRepository,
    saved: SavedStateHandle,
): FolderReviewViewModel {
    val library = FakeExerciseLibrary(
        mapOf(
            "bench" to exerciseInfo("bench", "Press banca"),
            "row" to exerciseInfo("row", "Remo"),
            "squat" to exerciseInfo("squat", "Sentadilla"),
        ),
    )
    val exercises = FakeExerciseRepository(
        listOf(exerciseRow("id-bench", "bench"), exerciseRow("id-row", "row"), exerciseRow("id-squat", "squat")),
    )
    return FolderReviewViewModel(
        reviews,
        ExerciseNameResolver(exercises, library),
        library,
        routines,
        MutableClock(),
        saved,
    )
}
