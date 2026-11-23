package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/**
 * Sets per muscle group for each week of the window (planning/07-coach-design.md section 3 D6). [weeks] holds the
 * Mondays, oldest first (the last one is the current, partial week); every list in [series] has one value per week
 * (zero when the group was not trained, decimals of 0.5 when secondary muscles count). Groups without any sets are
 * absent and the map iterates in [MuscleGroup] order. There is no week-over-week delta in this model.
 */
data class MuscleSetsHistory(val weeks: List<LocalDate>, val series: Map<MuscleGroup, List<Double>>)
