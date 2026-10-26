@file:Suppress("MatchingDeclarationName") // The file holds the TimeRange enum and its selector.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_1m_desc
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_1w_desc
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_1y_desc
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_3m_desc
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_6m_desc
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_all_desc
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The ranges a chart or stat can cover, with their short symbol ([label], like a unit: "1S", "3M", "Todo"). */
enum class TimeRange(val label: String, internal val spoken: StringResource) {
    Week("1S", Res.string.timeframe_1w_desc),
    Month("1M", Res.string.timeframe_1m_desc),
    ThreeMonths("3M", Res.string.timeframe_3m_desc),
    SixMonths("6M", Res.string.timeframe_6m_desc),
    Year("1A", Res.string.timeframe_1y_desc),
    All("Todo", Res.string.timeframe_all_desc),
    ;

    companion object {
        /** A segmented control holds 2 to 5 options (R-33). */
        const val MIN_SHOWN = 2
        const val MAX_SHOWN = 5

        /** Fails fast when [ranges] cannot be one control or [selected] is not among them. */
        fun requireValid(ranges: List<TimeRange>, selected: TimeRange) {
            require(ranges.size in MIN_SHOWN..MAX_SHOWN) { "A range selector shows $MIN_SHOWN to $MAX_SHOWN ranges" }
            require(selected in ranges) { "$selected is not one of $ranges" }
        }
    }
}

/**
 * Range chooser of charts and stats (R-33, R-34) on [SegmentedSelector]. Each screen passes the [ranges] it
 * supports, in order, between 2 and 5 ("Todo" replaces a longer one); [selected] must be one of them. Segment
 * tags are `<tagPrefix>_<range name in lower case>`; a screen reader speaks "1 semana", "1 mes", ... "Todo".
 */
@Composable
fun TimeRangeSelector(
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit,
    ranges: List<TimeRange>,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    TimeRange.requireValid(ranges, selected)
    val spoken = ranges.map { stringResource(it.spoken) }
    val options = remember(ranges, spoken, tagPrefix) {
        ranges.mapIndexed { index, range ->
            AppOption(range.label, "${tagPrefix}_${range.name.lowercase()}", spoken[index])
        }
    }
    SegmentedSelector(
        options = options,
        selectedIndex = ranges.indexOf(selected),
        onSelect = { onSelect(ranges[it]) },
        modifier = modifier,
    )
}
