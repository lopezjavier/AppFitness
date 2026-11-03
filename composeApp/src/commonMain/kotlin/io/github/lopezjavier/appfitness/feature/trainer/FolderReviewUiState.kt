package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.FolderChange
import io.github.lopezjavier.appfitness.domain.model.FolderFalta
import io.github.lopezjavier.appfitness.domain.model.FolderReview
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose

/** A "Falta" item with the names the screen shows; [routineName] is null when the folder's routine is unknown. */
data class FaltaItem(val falta: FolderFalta, val routineName: String?, val exerciseName: String?)

/** One change of the checklist: its typed one-line description, the coach's reason and whether it is checked. */
data class ChangeRow(val change: FolderChange, val line: ChangeLine, val checked: Boolean) {
    val id: String get() = change.id
    val reason: String get() = change.reason
}

sealed interface FolderReviewUiState {
    /** Nothing asked yet: the screen sends [FolderReviewEvent.Request] once (a paid call). */
    data object Idle : FolderReviewUiState

    /** Waiting for the coach (indeterminate progress), or for the stored review after a recreation. */
    data object Requesting : FolderReviewUiState

    /**
     * The review of the folder. [accepted] are the checked change ids; [allFalta] has every hard failure of the
     * folder and [falta] only those no accepted change fixes. [working] is true while the copy or the block is made.
     * [blockedFalta] is the list the server returned when it refused the block (empty otherwise); it clears on the
     * next toggle.
     */
    data class Content(
        val review: FolderReview,
        val accepted: Set<String>,
        val rows: List<ChangeRow>,
        val allFalta: List<FaltaItem>,
        val working: Boolean = false,
        val blockedFalta: List<FaltaItem> = emptyList(),
    ) : FolderReviewUiState {
        val purpose: ReviewPurpose get() = review.purpose

        /** The hard failures no accepted change fixes (the "Falta" section). */
        val falta: List<FaltaItem> get() = allFalta.filter { it.falta.code in openFaltaCodes(review, accepted) }

        /** An open Falta that no change of the review could fix: the user must edit the routines. */
        val hasUnfixableFalta: Boolean
            get() = falta.any { item -> review.changes.none { it.fixes == item.falta.code } }

        /** The footer exists: the apply copy needs changes to offer; the block can always be asked. */
        val showsFooter: Boolean get() = purpose == ReviewPurpose.Block || review.changes.isNotEmpty()

        val canApply: Boolean
            get() = purpose == ReviewPurpose.Review && accepted.isNotEmpty() && !working

        val canCreateBlock: Boolean
            get() = purpose == ReviewPurpose.Block && falta.isEmpty() && !working
    }

    /** The review, or its names, could not be shown. [changed] means the folder was edited since the review. */
    data class Failed(val kind: PlanFailureKind, val changed: Boolean = false) : FolderReviewUiState
}

sealed interface FolderReviewEvent {
    /** Asks the coach (costs money); ignored unless the state is [FolderReviewUiState.Idle]. */
    data object Request : FolderReviewEvent

    data class Toggle(val id: String) : FolderReviewEvent

    /** "Aplicar en una copia" (review purpose). */
    data object Apply : FolderReviewEvent

    /** "Crear bloque" (block purpose). */
    data object Continue : FolderReviewEvent

    /** "Reintentar": shows the kept review again, or asks for a new one when there is none. */
    data object Retry : FolderReviewEvent
}

sealed interface FolderReviewEffect {
    /** The copy exists; [folderName] is its name when it could be read. */
    data class Applied(val folderId: String, val folderName: String?) : FolderReviewEffect

    /** The block is stored: navigate to its preview. */
    data object BlockReady : FolderReviewEffect

    /** Applying or creating failed; nothing changed. [offline] picks the offline hint. */
    data class ActionFailed(val offline: Boolean) : FolderReviewEffect
}
