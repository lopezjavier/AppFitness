package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeDiscomfortFollowUpRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-108 (UI side): the swap offer, its picker, the health note and the two new `?` helps of the Trainer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class TrainerDiscomfortScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeDiscomfortFollowUpRepository(candidates = swapCandidates())

    private fun model(): TrainerViewModel = TrainerViewModel(
        FakeCoachRepository(),
        MutableClock(),
        savedState = SavedStateHandle(),
        discomfortFollowUps = repository,
        exerciseNames = discomfortNames(),
    )

    private fun showRoute(online: Boolean = true) {
        val viewModel = model()
        rule.setContent {
            AppTheme { TrainerRoute(viewModel = viewModel, connectivity = FakeConnectivityObserver(online)) }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun milderDiscomfort_showsNeitherTheOfferNorTheNote() {
        repository.followUps = emptyList()
        showRoute()

        rule.onNodeWithTag("trainer_discomfort_offer_card").assertDoesNotExist()
        rule.onNodeWithTag("trainer_discomfort_note").assertDoesNotExist()
    }

    @Test
    fun severeDiscomfort_offersTheSwapWithTheExerciseName() {
        repository.followUps = listOf(followUp())
        showRoute()

        rule.onNodeWithText("Molestia fuerte en Press banca").assertExists()
        rule.onNodeWithText(
            "¿Cambiamos Press banca por otro del mismo movimiento hasta que acabe el bloque?",
        ).assertExists()
        rule.onNodeWithTag("trainer_discomfort_offer_card_accept").assertExists()
        rule.onNodeWithText("Cambiar ejercicio").assertExists()
        rule.onNodeWithText("Ahora no").assertExists()
    }

    @Test
    fun notNow_hidesTheOffer() {
        repository.followUps = listOf(followUp())
        showRoute()

        tap("trainer_discomfort_offer_card_discard")

        rule.onNodeWithTag("trainer_discomfort_offer_card").assertDoesNotExist()
        assertEquals(listOf(FLAGGED_ID), repository.declined)
    }

    @Test
    fun picker_listsOnlyTheCandidates_andATapAppliesTheSwap() {
        repository.followUps = listOf(followUp())
        showRoute()

        tap("trainer_discomfort_offer_card_accept")

        rule.onNodeWithText("Se cambiará en todas las rutinas del bloque.").assertExists()
        rule.onNodeWithTag("trainer_swap_picker_row_$CANDIDATE_FAVORITE_ID").assertExists()
        rule.onNodeWithTag("trainer_swap_picker_row_$CANDIDATE_OTHER_ID").assertExists()
        tap("trainer_swap_picker_row_$CANDIDATE_FAVORITE_ID")

        assertEquals(listOf(FLAGGED_ID to CANDIDATE_FAVORITE_ID), repository.swaps)
        rule.onNodeWithTag("trainer_swap_picker_row_$CANDIDATE_FAVORITE_ID").assertDoesNotExist()
        rule.onNodeWithTag("trainer_discomfort_offer_card").assertDoesNotExist()
    }

    @Test
    fun noCandidates_showTheEmptyState() {
        repository.followUps = listOf(followUp())
        repository.candidates = emptyMap()
        showRoute()

        tap("trainer_discomfort_offer_card_accept")

        rule.onNodeWithTag("trainer_swap_picker_empty").assertExists()
        rule.onNodeWithText("No hay otro ejercicio del mismo movimiento con tu material.").assertExists()
    }

    @Test
    fun offline_disablesCambiar_withTheHint() {
        repository.followUps = listOf(followUp())
        showRoute(online = false)

        rule.onNodeWithTag("trainer_discomfort_offer_card_accept").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede cambiar el ejercicio").assertExists()
    }

    @Test
    fun persistentDiscomfort_showsTheNoteForItsTrigger_andClosingHidesIt() {
        repository.followUps =
            listOf(followUp(offerSwap = false, showNote = true, noteReason = DiscomfortNoteReason.Days14))
        showRoute()

        rule.onNodeWithText(
            "Esta molestia fuerte en Press banca sigue ahí desde hace 14 días. Seguimos entrenando el resto y " +
                "no subimos el peso en Press banca. Puede merecer la pena que te lo revise un profesional.",
        ).assertExists()
        tap("trainer_discomfort_note_close")

        rule.onNodeWithTag("trainer_discomfort_note").assertDoesNotExist()
        assertEquals(listOf(FLAGGED_ID), repository.dismissed)
    }

    @Test
    fun threeSessions_useTheirOwnNoteText() {
        repository.followUps =
            listOf(followUp(offerSwap = false, showNote = true, noteReason = DiscomfortNoteReason.Sessions3))
        showRoute()

        rule.onNodeWithText("Has notado una molestia fuerte en Press banca en 3 sesiones.", substring = true)
            .assertExists()
    }

    @Test
    fun targetsHelp_opensItsExplanation() {
        rule.setContent {
            AppTheme {
                BlockTargetsScreen(
                    BlockBuilderUiState.Targets(
                        emptyList(),
                        adherencePct = null,
                        cut = false,
                        noFit = null,
                    ),
                    {
                    },
                )
            }
        }

        rule.onNodeWithTag("block_targets_help").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Prioriza pocos grupos a la vez.", substring = true).assertExists()
    }

    @Test
    fun deloadHelp_opensItsExplanation() {
        val state = TrainerUiState.Content(
            plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
            block = BlockSummary.Saved(coachBlock(saved = true)),
            weeks = listOf(
                planWeek(week = 1, phase = WeekPhase.Current),
                planWeek(week = 2, phase = WeekPhase.Upcoming),
            ),
        )
        rule.setContent { AppTheme { TrainerScreen(state, flowOf(true), {}) } }

        rule.onNodeWithTag("trainer_deload_help").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Una semana de descarga es una semana más ligera", substring = true).assertExists()
    }
}
