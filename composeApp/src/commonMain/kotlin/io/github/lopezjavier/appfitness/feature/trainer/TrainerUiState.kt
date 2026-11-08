package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.domain.usecase.GoalChecklist

/** The plan entry of the Trainer tab: no plan, a proposal waiting for a decision, or the active plan. */
sealed interface PlanSummary {
    data object None : PlanSummary

    data class Proposed(val plan: CoachPlan) : PlanSummary

    data class Accepted(val plan: CoachPlan) : PlanSummary
}

/** The training block entry under an open plan: none requested, one waiting to be saved, or already in Entreno. */
sealed interface BlockSummary {
    data object None : BlockSummary

    data class Proposed(val block: CoachBlock) : BlockSummary

    data class Saved(val block: CoachBlock) : BlockSummary
}

/**
 * A persistent level 4-5 discomfort of one exercise (US-108): [offerSwap] shows the swap offer, [showNote] the one-time
 * health note whose text follows [noteReason]. [exerciseName] is resolved in the view model (R-54).
 */
data class DiscomfortFollowUpUi(
    val exerciseId: String,
    val exerciseName: String,
    val offerSwap: Boolean,
    val showNote: Boolean,
    val noteReason: DiscomfortNoteReason? = null,
)

/** A replacement in the swap picker; the order is the server's (favourites first). */
data class SwapCandidateUi(val exerciseId: String, val name: String, val isFavorite: Boolean)

/** The open swap picker of the flagged exercise [exerciseId]; [loading] until the candidates arrive. */
data class SwapPickerUi(
    val exerciseId: String,
    val exerciseName: String,
    val candidates: List<SwapCandidateUi> = emptyList(),
    val loading: Boolean = true,
)

sealed interface TrainerUiState {
    data object Loading : TrainerUiState

    /**
     * The plan hub. [plan], [calorieProposal], [block], [weeks] degrade to none when their reads fail.
     * [provisionalCount] feeds the "Tus datos" row; null when the read failed (the row then shows no count).
     * [decidingChange] / [requestingDeload] are true while a decision is in flight.
     */
    data class Content(
        val plan: PlanSummary = PlanSummary.None,
        val calorieProposal: CalorieProposal? = null,
        val decidingChange: Boolean = false,
        /** The training block of the open [plan]; [BlockSummary.None] when there is no plan or the read failed. */
        val block: BlockSummary = BlockSummary.None,
        /** Weeks of the saved block with their adherence; empty without a saved block or when the read failed. */
        val weeks: List<PlanWeek> = emptyList(),
        /**
         * Pause of the saved block (US-106): [BlockPause.paused] shows "Semana en pausa", [BlockPause.pausedWeeks] > 0
         * shows the moved end date. Null without a saved block or when the read failed.
         */
        val pause: BlockPause? = null,
        /** "Hacer descarga la próxima semana" is in flight. */
        val requestingDeload: Boolean = false,
        val provisionalCount: Int? = null,
        /** The open cardio-minutes sheet of the accepted plan ("Tu plan"); null when closed. */
        val cardioEdit: CardioEdit? = null,
        /** The diet-break switch of "Tu plan" is saving: shown pending, further taps are ignored. */
        val dietBreakSaving: Boolean = false,
        /** The "Eliminar bloque" confirmation is open; only ever true while the block is saved. */
        val removeBlockConfirm: Boolean = false,
        /** The block removal is in flight: the dialog stays open with its confirm action disabled. */
        val removingBlock: Boolean = false,
        /** N1-N4 on the Diario goals in use; null until the core supplies it. Shown only for an edited plan. */
        val goalsChecklist: GoalChecklist? = null,
        /** "Cambios del entrenador": pending flags first, then the latest changes; empty hides the section. */
        val coachChanges: List<CoachChangeRowUi> = emptyList(),
        /** Id of the change whose undo or decision is in flight; the other rows' actions are disabled meanwhile. */
        val busyChangeId: String? = null,
        /** The latest finished plan week has a summary: shows the "Resumen semanal" row. */
        val hasWeekSummary: Boolean = false,
        /** The block has ended and its review is open: shows "Revisión del bloque" and hides "Crear bloque". */
        val blockReviewDue: Boolean = false,
        /**
         * "Bienvenido de nuevo" (US-107): the latest session is 14+ days old and no session or block was saved since.
         * Hidden while [blockReviewDue]: the review footer is the only way on then (like `NewBlockEntries`).
         */
        val welcomeBack: Boolean = false,
        /** Weeks started in the current calorie phase ("Llevas n semanas en esta fase"); null hides the line. */
        val weeksInPhase: PhaseWeeks? = null,
        /** Swap offers and health notes of unresolved level 4-5 discomforts, one per exercise (US-108). */
        val discomfortFollowUps: List<DiscomfortFollowUpUi> = emptyList(),
        /** The open swap picker; null when closed. Rebuilt from its exercise id after recreation (ADR-40). */
        val swapPicker: SwapPickerUi? = null,
        /** Exercise whose swap is in flight: the picker ignores further taps meanwhile. */
        val busyFollowUpExerciseId: String? = null,
    ) : TrainerUiState {
        private val accepted: CoachPlan? get() = (plan as? PlanSummary.Accepted)?.plan

        /** "Bloque nuevo" replaces "Crear plan nuevo": an accepted plan with its block already saved. */
        val canStartNewBlock: Boolean get() = plan is PlanSummary.Accepted && block is BlockSummary.Saved

        /** The accepted plan's phase for the "Tu plan" line; null without an accepted plan. */
        val planPhase: Phase? get() = accepted?.phase

        val planPhaseReason: PhaseReason? get() = accepted?.nutrition?.phaseReason

        /**
         * "Hacer descarga la próxima semana" is offered: a saved block, no deload planned or ran in it, and a next
         * week to put it in (the current week + 1, or week 1 before the block starts).
         */
        val canRequestDeload: Boolean
            get() {
                if (block !is BlockSummary.Saved || weeks.isEmpty() || weeks.any { it.deload }) return false
                val current = weeks.firstOrNull { it.phase == WeekPhase.Current }
                val next = if (current != null) current.week + 1 else weeks.minOf { it.week }
                val blockRunningOrNotStarted = current != null || weeks.all { it.phase == WeekPhase.Upcoming }
                return blockRunningOrNotStarted && weeks.any { it.week == next && it.phase == WeekPhase.Upcoming }
            }
    }
}

sealed interface TrainerEvent {
    data object Load : TrainerEvent

    /** The tab came back to the foreground: silent reload that keeps the shown content (T-177). */
    data object Refresh : TrainerEvent

    /** "Aplicar" on the calorie proposal card. */
    data object ApplyCalorieProposal : TrainerEvent

    /** "Ahora no" on the calorie proposal card. */
    data object DeclineCalorieProposal : TrainerEvent

    /** "Cancelar descarga" on an applied deload row; ignored for any other row or while another action is busy. */
    data class CancelDeload(val id: String) : TrainerEvent

    /** "Hacer descarga la próxima semana"; ignored unless [TrainerUiState.Content.canRequestDeload]. */
    data object RequestDeload : TrainerEvent

    /** Opens the cardio-minutes sheet of the accepted plan; ignored without an accepted plan with activity targets. */
    data object OpenCardioEdit : TrainerEvent

    /** The stepper moved: [minutes] is clamped to 0..600 in steps of 5. */
    data class CardioMinutesChanged(val minutes: Int) : TrainerEvent

    /** The "Descanso de dieta" switch of "Tu plan" (accepted cut, not edited): saves live; ignored while saving. */
    data class ToggleDietBreak(val on: Boolean) : TrainerEvent

    /** "Guardar" in the sheet; ignored while the draft equals the saved value. */
    data object SaveCardio : TrainerEvent

    /** X, back or "Cancelar": closes the sheet and drops the draft. */
    data object DismissCardioEdit : TrainerEvent

    /** "Eliminar bloque": opens the confirmation; ignored without a saved block. */
    data object RequestRemoveBlock : TrainerEvent

    /** "Eliminar" in the confirmation; ignored when it is not open or a removal is already in flight. */
    data object ConfirmRemoveBlock : TrainerEvent

    /** "Cancelar", back or scrim: closes the confirmation and changes nothing (ignored while removing). */
    data object DismissRemoveBlock : TrainerEvent

    /** "Deshacer" on an applied weight change; ignored while another change is busy. */
    data class UndoCoachChange(val id: String) : TrainerEvent

    /** A one-tap option of a flagged raise; ignored while another change is busy. */
    data class DecideFlag(val id: String, val decision: ProgressionFlagDecision) : TrainerEvent

    /** "Añadir un rango" on a no-range flag: asks the screen to open the routine editor on that exercise. */
    data class OpenRange(val id: String) : TrainerEvent

    /** "Bloque nuevo": opens "Antes de empezar"; ignored unless [TrainerUiState.Content.canStartNewBlock]. */
    data object OpenNewBlock : TrainerEvent

    /** "Mi entrenamiento": opens the training goal and preferences screen. */
    data object OpenMyTraining : TrainerEvent

    /** "Cambiar ejercicio" on the swap offer: opens the picker and loads its candidates. */
    data class OpenSwapPicker(val exerciseId: String) : TrainerEvent

    /** X, back or outside: closes the picker without changing anything. */
    data object DismissSwapPicker : TrainerEvent

    /** A candidate was tapped: swaps it in for the flagged exercise in every routine of the block. */
    data class PickSwap(val newExerciseId: String) : TrainerEvent

    /** "Ahora no" on the swap offer. */
    data class DeclineSwap(val exerciseId: String) : TrainerEvent

    /** Close on the health note. */
    data class DismissDiscomfortNote(val exerciseId: String) : TrainerEvent
}

/** Why an undo was refused or failed. */
enum class CoachChangeUndoFailure { Conflict, NotFound, Other }

/** One-off effects; the UI shows the message and fires the haptics. */
sealed interface TrainerEffect {
    /** The calorie proposal was applied; the Diario targets changed. */
    data object CalorieProposalApplied : TrainerEffect

    /** The calorie proposal was declined ("Ahora no"); nothing changed. */
    data object CalorieProposalDeclined : TrainerEffect

    /** The decision failed; [apply] says which action, [offline] picks the offline hint. */
    data class CalorieProposalFailed(val apply: Boolean, val offline: Boolean) : TrainerEffect

    /** The deload was cancelled (confirm haptic, no message: the row changes). */
    data object DeloadCancelled : TrainerEffect

    /** Cancelling failed (reject haptic and message); the list was reloaded when the server said it was stale. */
    data class DeloadCancelFailed(val offline: Boolean) : TrainerEffect

    /** The deload was requested (confirm haptic, no message: a row appears and the week chip changes). */
    data object DeloadRequested : TrainerEffect

    /** Requesting failed; [notAvailable] means one is already planned or ran (the weeks were reloaded). */
    data class DeloadRequestFailed(val offline: Boolean, val notAvailable: Boolean) : TrainerEffect

    /** The cardio minutes were stored and the sheet closed: confirm haptic, no message. */
    data object CardioSaved : TrainerEffect

    /** The diet break was saved: confirm haptic. */
    data object DietBreakSaved : TrainerEffect

    /** Saving the diet break failed and the switch snapped back: reject haptic; [offline] picks the offline hint. */
    data class DietBreakFailed(val offline: Boolean) : TrainerEffect

    /** Saving the cardio failed; the sheet stays open. [offline] picks the offline hint. */
    data class CardioFailed(val offline: Boolean) : TrainerEffect

    /** The block was removed and the hub reloaded: confirm haptic, no message (the outcome is visible). */
    data object BlockRemoved : TrainerEffect

    /** Removing the block failed; the dialog is closed and the block stays. [offline] picks the offline hint. */
    data class RemoveBlockFailed(val offline: Boolean) : TrainerEffect

    /** The change was undone (confirm haptic); [skipped] > 0 routines were edited since and kept (show a message). */
    data class CoachChangeUndone(val skipped: Int) : TrainerEffect

    /** The undo failed (reject haptic and message, no action button); the row stays as it was. */
    data class CoachChangeUndoFailed(val kind: CoachChangeUndoFailure, val offline: Boolean) : TrainerEffect

    /** The flag was decided (confirm haptic, no message: the row changes). */
    data class FlagDecided(val decision: ProgressionFlagDecision) : TrainerEffect

    /** The decision failed; [notPending] means it was already decided (the list was reloaded). */
    data class FlagDecideFailed(val offline: Boolean, val notPending: Boolean) : TrainerEffect

    /** Navigate to the editor of [routineId] with the range of [exerciseId] in focus. */
    data class OpenRoutineRange(val routineId: String, val exerciseId: String) : TrainerEffect

    /** Navigate to "Antes de empezar" in new-block mode for the accepted plan [planId]. */
    data class OpenNewBlock(val planId: String) : TrainerEffect

    /** Navigate to "Mi entrenamiento". */
    data object OpenMyTraining : TrainerEffect

    /** The swap was applied to the block (confirm haptic, R-12) and the hub reloaded. */
    data object SwapApplied : TrainerEffect

    /** Loading the candidates or applying the swap failed; the picker stays open when it was already showing. */
    data class SwapFailed(val offline: Boolean) : TrainerEffect

    /** "Ahora no" or closing the note could not be saved; the item is back. [offline] picks the offline hint. */
    data class FollowUpActionFailed(val offline: Boolean) : TrainerEffect
}
