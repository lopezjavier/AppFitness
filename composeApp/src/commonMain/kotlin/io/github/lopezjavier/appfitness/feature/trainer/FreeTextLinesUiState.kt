package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine

/** The lines of one origin, newest first. */
data class FreeTextSection(val kind: FreeTextKind, val lines: List<FreeTextLine>)

sealed interface FreeTextLinesUiState {
    data object Loading : FreeTextLinesUiState

    /** Sections in screen order; a line's switch is on unless its status is `Excluded`. No sections = empty state. */
    data class Content(val sections: List<FreeTextSection>) : FreeTextLinesUiState {
        val isEmpty: Boolean get() = sections.isEmpty()
    }

    data object Offline : FreeTextLinesUiState

    data object Error : FreeTextLinesUiState
}

sealed interface FreeTextLinesEvent {
    data object Retry : FreeTextLinesEvent

    /** Flips the switch of a line (optimistic; rolled back when the server refuses). */
    data class Toggle(val kind: FreeTextKind, val sourceId: String) : FreeTextLinesEvent
}

sealed interface FreeTextLinesEffect {
    /** The switch was rolled back; [offline] picks the offline hint. */
    data class ToggleFailed(val offline: Boolean) : FreeTextLinesEffect
}
