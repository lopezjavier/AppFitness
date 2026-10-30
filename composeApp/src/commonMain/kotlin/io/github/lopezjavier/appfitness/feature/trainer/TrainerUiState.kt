package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CalorieChange
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.DeloadProposal
import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
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

/** Where the total expenditure comes from: the user's own data (Adaptive) or a formula (Estimated). */
enum class ExpenditureSource { Adaptive, Estimated }

sealed interface TrainerUiState {
    data object Loading : TrainerUiState

    /**
     * Derived values. [editSheet] is the activity level picked in the open edit sheet (null when the sheet is closed);
     * [confirming] is true while a confirm or save call is in flight. [allConfirmed] mirrors the values (plan gate).
     * [plan] and [calorieChange] degrade to none when their reads fail: they never block the derived values.
     * [decidingChange] is true while a calorie-change decision is in flight.
     */
    data class Content(
        val values: CoachDerivedValues,
        val confirming: Boolean = false,
        val editSheet: ActivityLevel? = null,
        /** Goal strategy that colours the weight trend; null when unknown (the trend is then neutral). */
        val strategy: GoalStrategy? = null,
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
    ) : TrainerUiState {
        val allConfirmed: Boolean get() = values.allConfirmed

        /** Source tag of the total expenditure; null while the kcal is unknown or asked again. */
        val expenditureSource: ExpenditureSource?
            get() {
                val expenditure = values.totalExpenditure
                return when {
                    expenditure.kcal == null || expenditure.reask -> null
                    expenditure.source == DerivedSource.Adaptive -> ExpenditureSource.Adaptive
                    expenditure.source == DerivedSource.Formula -> ExpenditureSource.Estimated
                    else -> null
                }
            }
    }

    data object Error : TrainerUiState

    data object Offline : TrainerUiState
}

sealed interface TrainerEvent {
    data object Load : TrainerEvent

    data object Retry : TrainerEvent

    /** The tab came back to the foreground: silent reload that keeps the shown content (T-177). */
    data object Refresh : TrainerEvent

    /** "Confirmar" on the card: accepts the values as they are. */
    data object Confirm : TrainerEvent

    data object OpenEdit : TrainerEvent

    /** A card tapped in the sheet: confirms every value with that level at once (R-12); ignored with it closed. */
    data class PickLevel(val level: ActivityLevel) : TrainerEvent

    data object CloseEdit : TrainerEvent

    /** "Aceptar" / "Descartar" on the calorie-change card. */
    data class DecideCalorieChange(val accept: Boolean) : TrainerEvent

    /** "Aceptar" / "Descartar" on the deload card. */
    data class DecideDeload(val accept: Boolean) : TrainerEvent
}

/** One-off effects; the UI shows the message and fires the haptics. */
sealed interface TrainerEffect {
    data object Confirmed : TrainerEffect

    /** [fromSheet] distinguishes the save message from the confirm message; [offline] picks the offline hint. */
    data class ConfirmFailed(val fromSheet: Boolean, val offline: Boolean) : TrainerEffect

    /** The calorie change was accepted or discarded ([accepted]); accepting changes the Diario targets. */
    data class CalorieChangeDecided(val accepted: Boolean) : TrainerEffect

    /** The decision failed; [accept] says which action, [offline] picks the offline hint. */
    data class CalorieChangeFailed(val accept: Boolean, val offline: Boolean) : TrainerEffect

    /** The deload was accepted or discarded ([accepted]). */
    data class DeloadDecided(val accepted: Boolean) : TrainerEffect

    /** The deload decision failed; [accept] says which action, [offline] picks the offline hint. */
    data class DeloadFailed(val accept: Boolean, val offline: Boolean) : TrainerEffect
}
