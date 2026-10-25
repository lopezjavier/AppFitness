package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_assigned_to
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_group_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_letter_1
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_letter_2
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_letter_3
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_letter_4
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_letter_5
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_letter_6
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_letter_7
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_name_1
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_name_2
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_name_3
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_name_4
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_name_5
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_name_6
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_name_7
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val DayLetters: List<StringResource> = listOf(
    Res.string.weekday_letter_1,
    Res.string.weekday_letter_2,
    Res.string.weekday_letter_3,
    Res.string.weekday_letter_4,
    Res.string.weekday_letter_5,
    Res.string.weekday_letter_6,
    Res.string.weekday_letter_7,
)

private val DayNames: List<StringResource> = listOf(
    Res.string.weekday_name_1,
    Res.string.weekday_name_2,
    Res.string.weekday_name_3,
    Res.string.weekday_name_4,
    Res.string.weekday_name_5,
    Res.string.weekday_name_6,
    Res.string.weekday_name_7,
)

/** The Spanish full name of the ISO weekday [day] (1 = Lunes .. 7 = Domingo). */
@Composable
fun weekdayName(day: Int): String = stringResource(DayNames[day - 1])

/** The one-letter Spanish initial of the ISO weekday [day] ("L M X J V S D"). */
@Composable
fun weekdayLetter(day: Int): String = stringResource(DayLetters[day - 1])

/**
 * Seven equal day chips "L M X J V S D" (ISO days 1..7, multi-select). Each chip is a checkbox of at least 48 dp whose
 * description is the full day name. A day in [disabled] (day -> name of the comida that owns it) is inert and its
 * state says "Ya asignado a <comida>". The row scrolls horizontally when the seven targets do not fit (narrow screens,
 * large fonts) and never wraps. Chips are tagged `<testTagPrefix>_day_<n>`. Stateless.
 */
@Composable
fun WeekdaySelector(
    selected: Set<Int>,
    disabled: Map<Int, String>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "weekday_selector",
) {
    val group = stringResource(Res.string.weekday_group_cd)
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = group },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (day in 1..DayNames.size) {
            val owner = disabled[day]
            AppToggleChip(
                label = weekdayLetter(day),
                checked = owner == null && day in selected,
                onCheckedChange = { onToggle(day) },
                modifier = Modifier.testTag("${testTagPrefix}_day_$day"),
                contentDescription = weekdayName(day),
                enabled = owner == null,
                stateDescription = owner?.let { stringResource(Res.string.weekday_assigned_to, it) },
            )
        }
    }
}

@Preview
@Composable
private fun WeekdaySelectorPreview() {
    AppTheme {
        WeekdaySelector(selected = setOf(1, 3, 5), disabled = mapOf(2 to "Desayuno A"), onToggle = {})
    }
}
