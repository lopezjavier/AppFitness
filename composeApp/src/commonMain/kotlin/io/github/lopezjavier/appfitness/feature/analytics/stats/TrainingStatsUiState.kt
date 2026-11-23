package io.github.lopezjavier.appfitness.feature.analytics.stats

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary

/** Load state of one independent block of the statistics screen. */
sealed interface SectionState<out T> {
    data object Loading : SectionState<Nothing>

    /** The call worked but there is nothing to show (no data in the period). */
    data object Empty : SectionState<Nothing>

    /** The data could not be read (also when offline); [TrainingStatsEvent.Retry] tries again. */
    data object Error : SectionState<Nothing>

    data class Content<T>(val data: T) : SectionState<T>
}

/**
 * An exercise with its set count in a period; [name] is always resolved (an unnamed exercise fails the section).
 * [imageKey] is the bundled-picture key, null for a custom exercise (the UI draws the placeholder).
 */
data class StatsExercise(
    val exerciseId: String,
    val name: String,
    val sets: Double,
    val sessions: Int,
    val imageKey: String? = null,
    /** "Ejercicios principales" (D4): the change of the estimated 1RM over the range; null when [compared] is false. */
    val changeKg: Double? = null,
    val compared: Boolean = false,
)

/**
 * Radar series over a fixed list of axes (every [MuscleGroup], catalog order); [previous] is null for "Todo" and
 * when the previous window could not be loaded ([previousFailed]).
 */
data class MuscleRadar(
    val current: List<MuscleGroupSets>,
    val previous: List<MuscleGroupSets>?,
    val previousFailed: Boolean = false,
)

/** Detail of the open muscle in the last 7 days; [exercises] is empty when the muscle was not trained. */
data class MuscleDetail(val group: MuscleGroup, val sets: Double, val exercises: List<StatsExercise>)

/**
 * State of the "Estadisticas" screen. [summary] is [SectionState.Empty] when the frame has no workouts (whole-screen
 * empty). [bodyMap] and [muscleDetail] cover the last 7 days and do not depend on [range].
 */
data class TrainingStatsUiState(
    val range: ProgressRange = TrainingStatsViewModel.DEFAULT_RANGE,
    val summary: SectionState<TrainingSummary> = SectionState.Loading,
    val progress: SectionState<PeriodProgressUi> = SectionState.Loading,
    val records: SectionState<PeriodRecordsUi> = SectionState.Loading,
    val setCounts: SectionState<List<SetCountPoint>> = SectionState.Loading,
    val muscleSets: SectionState<List<MuscleGroupSets>> = SectionState.Loading,
    val topExercises: SectionState<List<StatsExercise>> = SectionState.Loading,
    val radar: SectionState<MuscleRadar> = SectionState.Loading,
    val bodyMap: SectionState<List<MuscleGroupSets>> = SectionState.Loading,
    val openMuscle: MuscleGroup? = null,
    val muscleDetail: SectionState<MuscleDetail> = SectionState.Loading,
) {
    val isEmpty: Boolean get() = summary == SectionState.Empty

    /** Every block failed to load (R-114): the screen shows one error instead of one per block. */
    val allFailed: Boolean
        get() = listOf(summary, progress, records, setCounts, muscleSets, topExercises, radar, bodyMap).all {
            it ==
                SectionState.Error
        }

    /** The records card exists only with records: an Error or Empty records section hides it (no error card). */
    val recordsCardVisible: Boolean get() = records is SectionState.Content

    /** One error covers progress and records together; the screen never shows a second one for the records. */
    val progressErrorVisible: Boolean get() = progress == SectionState.Error

    /** The frame is empty and the 7-day body map has no sets either: one empty state says it all (R-31). */
    val isFullyEmpty: Boolean
        get() = isEmpty && when (bodyMap) {
            SectionState.Empty -> true
            is SectionState.Content -> bodyMap.data.all { it.sets <= 0.0 }
            else -> false
        }
}

enum class StatsSection { Summary, Progress, Records, Chart, Muscles, Exercises, Radar, Body, Sheet }

sealed interface TrainingStatsEvent {
    data class SelectRange(val range: ProgressRange) : TrainingStatsEvent

    /** Reloads one section (frame sections reload together) or, with null, everything. */
    data class Retry(val section: StatsSection? = null) : TrainingStatsEvent

    data class OpenMuscle(val group: MuscleGroup) : TrainingStatsEvent

    data object CloseMuscle : TrainingStatsEvent
}
