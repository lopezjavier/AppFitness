package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.Situation

/** The rows of one origin in "Tus datos" ("Perfil", "Mi entrenamiento", "Pesaje", "Estimación del entrenador"). */
data class YourDataSection(val origin: PlanInputOrigin, val rows: List<PlanInputRow>)

sealed interface YourDataUiState {
    data object Loading : YourDataUiState

    /** [rows] merge the plan and block inputs (one per key). [sheet] is the open activity-level sheet, if any. */
    data class Content(
        val rows: List<PlanInputRow>,
        val sheet: ActivityLevel? = null,
        val savingLevel: Boolean = false,
        /** What the user said in "Para conocerte"; empty when nothing was saved (or it could not be read). */
        val intake: IntakeSummary = IntakeSummary(),
        /** The situation the coach works with (stated or derived from sessions); null when unknown. */
        val situation: Situation? = null,
        /** Rows whose value still comes from the intake: shown with "Provisional" until real data takes over. */
        val intakeSourcedKeys: Set<PlanInputKey> = emptySet(),
        /** The one-time note "Ahora usamos tus datos reales"; [handoverKcal] is the measured expenditure. */
        val showHandover: Boolean = false,
        val handoverKcal: Double? = null,
    ) : YourDataUiState {
        /** Non-empty sections in screen order. */
        val sections: List<YourDataSection> = PlanInputOrigin.entries
            .map { origin -> YourDataSection(origin, rows.filter { it.origin == origin }) }
            .filter { it.rows.isNotEmpty() }

        val provisionalCount: Int get() = rows.count { it.isProvisional }
    }

    data object Offline : YourDataUiState

    data object Error : YourDataUiState
}

sealed interface YourDataEvent {
    data object Load : YourDataEvent

    data object Retry : YourDataEvent

    /** Back from an editor: silent reload that keeps the shown content. */
    data object Refresh : YourDataEvent

    data object OpenSheet : YourDataEvent

    data class PickLevel(val level: ActivityLevel) : YourDataEvent

    data object CloseSheet : YourDataEvent

    /** The handover note was composed: tells the server once so it never appears again. Idempotent. */
    data object HandoverShown : YourDataEvent
}

sealed interface YourDataEffect {
    /** Saving the activity level failed; [offline] picks the offline hint. */
    data class SaveFailed(val offline: Boolean) : YourDataEffect
}
