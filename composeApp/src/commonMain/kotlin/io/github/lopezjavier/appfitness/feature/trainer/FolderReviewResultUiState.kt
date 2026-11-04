package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup

/** Where a strength row names a routine and a main lift (names already resolved, R-54). */
data class RepLine(val routineName: String, val exerciseName: String, val repMin: Int, val repMax: Int)

/** A routine where [mainLift] comes after [earlier]; [mainLifts] are all its main lifts in order. */
data class OrderLine(val routineName: String, val mainLift: String, val earlier: String, val mainLifts: List<String>)

/** One row of the checklist, in display order. [met] drives the icon and the row's state description. */
sealed interface ReviewItem {
    val met: Boolean

    /** The weekly [sets] of [group] (fractional); [missing] sets reach the target, [none] when it has no sets. */
    data class Group(
        val group: MuscleGroup,
        val sets: Double,
        val missing: Int,
        val none: Boolean,
        override val met: Boolean,
    ) : ReviewItem

    data class Days(val days: Int, override val met: Boolean) : ReviewItem

    data class Reps(val lines: List<RepLine>) : ReviewItem {
        override val met: Boolean get() = lines.isEmpty()
    }

    data class Order(val lines: List<OrderLine>) : ReviewItem {
        override val met: Boolean get() = lines.isEmpty()
    }
}

/** The four bands of the score (05 section 2.5 "Score ramp"); the colour only reinforces the label. */
enum class ScoreBand {
    Poor,
    Fair,
    Good,
    Complete,
    ;

    companion object {
        private const val FAIR_FROM = 5.0
        private const val GOOD_FROM = 8.0
        private const val COMPLETE_AT = 10.0

        /** `< 5,0` Poor; `5,0 <= s < 8,0` Fair; `8,0 <= s < 10,0` Good; `10,0` Complete. */
        fun of(score: Double): ScoreBand = when {
            score >= COMPLETE_AT -> Complete
            score >= GOOD_FROM -> Good
            score >= FAIR_FROM -> Fair
            else -> Poor
        }
    }
}

sealed interface FolderReviewResultUiState {
    data object Loading : FolderReviewResultUiState

    /** The folder has no routines: no score is shown. */
    data object Empty : FolderReviewResultUiState

    /** The routines, the catalogue or the names could not be read. */
    data object Error : FolderReviewResultUiState

    /** [complete]: every row met (the `complete` line, with [liftingRoutines] days and the strength sentence). */
    data class Content(
        val folderName: String?,
        val score: Double,
        val strength: Boolean,
        val complete: Boolean,
        val liftingRoutines: Int,
        val items: List<ReviewItem>,
    ) : FolderReviewResultUiState {
        val band: ScoreBand get() = ScoreBand.of(score)
    }
}

sealed interface FolderReviewResultEvent {
    /** "Reintentar" after a failed load. */
    data object Retry : FolderReviewResultEvent
}
