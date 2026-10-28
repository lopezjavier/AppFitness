@file:Suppress("MatchingDeclarationName") // The file holds the sheet and its option model.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import org.jetbrains.compose.resources.painterResource

/** One row of [ChartMetricPickerSheet]: the [value] it selects, its spoken/visible [label] and its [tag] suffix. */
data class ChartMetricOption<T>(val value: T, val label: String, val tag: String)

/**
 * The picker of the metric a chart plots (picker archetype, R-07): one row per option, the [selected] one checked. A
 * tap applies at once through [onSelect]; the caller closes the sheet. Tags: `<tagPrefix>_picker_sheet` (+ `_close`)
 * and `<tagPrefix>_<option.tag>` per row.
 */
@Composable
@Suppress("LongParameterList") // The texts, the options, the selection, two actions and the tag prefix.
fun <T> ChartMetricPickerSheet(
    title: String,
    options: List<ChartMetricOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    AppPickerSheet(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
        testTagPrefix = "${tagPrefix}_picker_sheet",
    ) {
        options.forEach { option ->
            AppListRow(
                headline = option.label,
                modifier = Modifier
                    .testTag("${tagPrefix}_${option.tag}")
                    .semantics { this.selected = option.value == selected },
                onClick = { onSelect(option.value) },
                trailing = if (option.value == selected) {
                    {
                        Icon(
                            painter = painterResource(Res.drawable.ic_check),
                            contentDescription = null,
                            tint = AppTheme.colors.athleticCyan,
                            modifier = Modifier.size(Spacing.xl),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
