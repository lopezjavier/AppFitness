package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.SplitOption

/** Which paid call a [BlockSplitsUiState.Failed] belongs to: "Reintentar" repeats that call and no other. */
enum class BlockSplitsStage { Options, Build }

sealed interface BlockSplitsUiState {
    /** Nothing asked yet: the screen sends [BlockSplitsEvent.Request] once. */
    data object Idle : BlockSplitsUiState

    /** Waiting for the coach's split options (indeterminate progress). */
    data object Requesting : BlockSplitsUiState

    /**
     * The 2-3 [options] with one [selectedId] (the recommended one at first). While [building] the block is being
     * made on the picked split and the choice is locked.
     */
    data class Options(val options: List<SplitOption>, val selectedId: String, val building: Boolean = false) :
        BlockSplitsUiState

    data class Failed(val kind: PlanFailureKind, val stage: BlockSplitsStage) : BlockSplitsUiState
}

sealed interface BlockSplitsEvent {
    /** Asks the coach for the options (costs money); ignored unless the state is [BlockSplitsUiState.Idle]. */
    data object Request : BlockSplitsEvent

    data class Pick(val id: String) : BlockSplitsEvent

    /** "Crear bloque": builds the block on the picked split (costs money). Nothing is saved yet. */
    data object Create : BlockSplitsEvent

    /** "Reintentar": repeats the failed call, keeping the pick. */
    data object Retry : BlockSplitsEvent
}

sealed interface BlockSplitsEffect {
    /** The block is stored: navigate to its preview. */
    data object Built : BlockSplitsEffect
}
