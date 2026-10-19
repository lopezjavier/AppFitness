package io.github.lopezjavier.appfitness.feature.workout

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month

/** How a session day is named in the history: relative for today and yesterday, else weekday, day and month. */
sealed interface DateLabel {
    data object Today : DateLabel

    data object Yesterday : DateLabel

    data class Other(val dayOfWeek: DayOfWeek, val day: Int, val month: Month) : DateLabel
}
