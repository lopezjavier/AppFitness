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

/** An exercise with its set count in a period; [name] is null when it is unknown (hidden or not in the library). */
data class StatsExercise(val exerciseId: String, val name: String?, val sets: Double, val sessions: Int)

/** Radar series over a fixed list of axes (every [MuscleGroup], catalog order); [previous] is null for "Todo". */
data class MuscleRadar(val current: List<MuscleGroupSets>, val previous: List<MuscleGroupSets>?)

/** Detail of the open muscle in the last 7 days; [exercises] is empty when the muscle was not trained. */
data class MuscleDetail(val group: MuscleGroup, val sets: Double, val exercises: List<StatsExercise>)

/**
 * State of the "Estadisticas" screen. [summary] is [SectionState.Empty] when the frame has no workouts (whole-screen
 * empty). [bodyMap] and [muscleDetail] cover the last 7 days and do not depend on [range].
 */
data class TrainingStatsUiState(
    val range: ProgressRange = TrainingStatsViewModel.DEFAULT_RANGE,
    val summary: SectionState<TrainingSummary> = SectionState.Loading,
    val setCounts: SectionState<List<SetCountPoint>> = SectionState.Loading,
    val muscleSets: SectionState<List<MuscleGroupSets>> = SectionState.Loading,
    val topExercises: SectionState<List<StatsExercise>> = SectionState.Loading,
    val radar: SectionState<MuscleRadar> = SectionState.Loading,
    val bodyMap: SectionState<List<MuscleGroupSets>> = SectionState.Loading,
    val openMuscle: MuscleGroup? = null,
    val muscleDetail: SectionState<MuscleDetail> = SectionState.Loading,
) {
    val isEmpty: Boolean get() = summary == SectionState.Empty
}

enum class StatsSection { Summary, Chart, Muscles, Exercises, Radar, Body, Sheet }

sealed interface TrainingStatsEvent {
    data class SelectRange(val range: ProgressRange) : TrainingStatsEvent

    /** Reloads one section (frame sections reload together) or, with null, everything. */
    data class Retry(val section: StatsSection? = null) : TrainingStatsEvent

    data class OpenMuscle(val group: MuscleGroup) : TrainingStatsEvent

    data object CloseMuscle : TrainingStatsEvent
}
