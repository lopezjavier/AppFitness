package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Per completed session summary of one exercise; [bestOneRepMax] is null when no set was eligible. */
data class ExerciseHistoryPoint(
    val sessionId: String,
    val date: LocalDate,
    val bestOneRepMax: Double?,
    val totalVolume: Double,
)
