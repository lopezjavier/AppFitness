package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Raw `weekly_muscle_volume` row: kilos lifted by [muscleGroup] in the ISO week starting [weekStart] (a Monday). */
data class WeeklyMuscleVolumeRow(val weekStart: LocalDate, val muscleGroup: MuscleGroup, val totalVolume: Double)

/**
 * Volume per muscle group from the first recorded week (max 8) to the current week. [weeks] holds the Mondays, oldest
 * first (the last one is the current week); every list in [series] has one value per week (zero when the group was
 * not trained). Groups without any volume are absent and the map iterates in [MuscleGroup] order.
 */
data class MuscleVolumeHistory(val weeks: List<LocalDate>, val series: Map<MuscleGroup, List<Double>>)
