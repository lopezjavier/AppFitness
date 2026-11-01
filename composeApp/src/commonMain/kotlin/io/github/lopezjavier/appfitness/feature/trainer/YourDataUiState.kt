package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel

/** The rows of one origin in "Tus datos" ("Perfil", "Mi entrenamiento", "Pesaje", "Estimación del entrenador"). */
data class YourDataSection(val origin: PlanInputOrigin, val rows: List<PlanInputRow>)

sealed interface YourDataUiState {
    data object Loading : YourDataUiState

    /** [rows] merge the plan and block inputs (one per key). [sheet] is the open activity-level sheet, if any. */
    data class Content(
        val rows: List<PlanInputRow>,
        val sheet: ActivityLevel? = null,
        val savingLevel: Boolean = false,
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
}

sealed interface YourDataEffect {
    /** Saving the activity level failed; [offline] picks the offline hint. */
    data class SaveFailed(val offline: Boolean) : YourDataEffect
}
