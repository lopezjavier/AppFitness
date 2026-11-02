package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CalorieChange
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.DeloadProposal
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import io.github.lopezjavier.appfitness.domain.model.PlanWeek

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

sealed interface TrainerUiState {
    data object Loading : TrainerUiState

    /**
     * The plan hub. [plan], [calorieChange], [block], [weeks] and [deload] degrade to none when their reads fail.
     * [provisionalCount] feeds the "Tus datos" row; null when the read failed (the row then shows no count).
     * [decidingChange] / [decidingDeload] are true while a decision is in flight.
     */
    data class Content(
        val plan: PlanSummary = PlanSummary.None,
        val calorieChange: CalorieChange? = null,
        val decidingChange: Boolean = false,
        /** The training block of the open [plan]; [BlockSummary.None] when there is no plan or the read failed. */
        val block: BlockSummary = BlockSummary.None,
        /** Weeks of the saved block with their adherence; empty without a saved block or when the read failed. */
        val weeks: List<PlanWeek> = emptyList(),
        /** The pending easier-week suggestion; null when none or when the read failed. */
        val deload: DeloadProposal? = null,
        val decidingDeload: Boolean = false,
        val provisionalCount: Int? = null,
        /** The open cardio-minutes sheet of the accepted plan ("Tu plan"); null when closed. */
        val cardioEdit: CardioEdit? = null,
    ) : TrainerUiState {
        private val accepted: CoachPlan? get() = (plan as? PlanSummary.Accepted)?.plan

        /** The accepted plan's phase for the "Tu plan" line; null without an accepted plan. */
        val planPhase: Phase? get() = accepted?.phase

        val planPhaseReason: PhaseReason? get() = accepted?.nutrition?.phaseReason
    }
}

sealed interface TrainerEvent {
    data object Load : TrainerEvent

    /** The tab came back to the foreground: silent reload that keeps the shown content (T-177). */
    data object Refresh : TrainerEvent

    /** "Aceptar" / "Descartar" on the calorie-change card. */
    data class DecideCalorieChange(val accept: Boolean) : TrainerEvent

    /** "Aceptar" / "Descartar" on the deload card. */
    data class DecideDeload(val accept: Boolean) : TrainerEvent

    /** Opens the cardio-minutes sheet of the accepted plan; ignored without an accepted plan with activity targets. */
    data object OpenCardioEdit : TrainerEvent

    /** The stepper moved: [minutes] is clamped to 0..600 in steps of 5. */
    data class CardioMinutesChanged(val minutes: Int) : TrainerEvent

    /** "Guardar" in the sheet; ignored while the draft equals the saved value. */
    data object SaveCardio : TrainerEvent

    /** X, back or "Cancelar": closes the sheet and drops the draft. */
    data object DismissCardioEdit : TrainerEvent
}

/** One-off effects; the UI shows the message and fires the haptics. */
sealed interface TrainerEffect {
    /** The calorie change was accepted or discarded ([accepted]); accepting changes the Diario targets. */
    data class CalorieChangeDecided(val accepted: Boolean) : TrainerEffect

    /** The decision failed; [accept] says which action, [offline] picks the offline hint. */
    data class CalorieChangeFailed(val accept: Boolean, val offline: Boolean) : TrainerEffect

    /** The deload was accepted or discarded ([accepted]). */
    data class DeloadDecided(val accepted: Boolean) : TrainerEffect

    /** The deload decision failed; [accept] says which action, [offline] picks the offline hint. */
    data class DeloadFailed(val accept: Boolean, val offline: Boolean) : TrainerEffect

    /** The cardio minutes were stored and the sheet closed: confirm haptic, no message. */
    data object CardioSaved : TrainerEffect

    /** Saving the cardio failed; the sheet stays open. [offline] picks the offline hint. */
    data class CardioFailed(val offline: Boolean) : TrainerEffect
}
