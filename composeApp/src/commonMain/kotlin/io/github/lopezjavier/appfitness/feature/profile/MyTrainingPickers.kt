@file:Suppress("MatchingDeclarationName") // The option holder sits next to the picker sheet that renders it.

package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.painterResource

/** One row of a [MyTrainingPickerSheet]: its [tag] suffix, visible [label] and whether it is the chosen value. */
internal class PickerOption(val tag: String, val label: String, val isSelected: Boolean, val onPick: () -> Unit)

/**
 * Picker archetype shared by the three "Mi entrenamiento" rows: a tap applies the option to the draft and closes
 * (R-12); the chosen row shows a check and is scrolled into view. Rows are tagged `<tagPrefix>_option_<tag>`.
 */
@Composable
internal fun MyTrainingPickerSheet(
    title: String,
    tagPrefix: String,
    options: List<PickerOption>,
    onClose: () -> Unit,
) {
    AppPickerSheet(title = title, onDismiss = onClose, testTagPrefix = "${tagPrefix}_sheet") {
        options.forEach { option ->
            PickerRow(tagPrefix, option, onClose)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class) // Scrolls the chosen row into view when the sheet opens.
private fun PickerRow(tagPrefix: String, option: PickerOption, onClose: () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    if (option.isSelected) LaunchedEffect(Unit) { requester.bringIntoView() }
    AppListRow(
        headline = option.label,
        modifier = Modifier
            .bringIntoViewRequester(requester)
            .testTag("${tagPrefix}_option_${option.tag}")
            .semantics { selected = option.isSelected },
        onClick = {
            option.onPick()
            onClose()
        },
        trailing = if (option.isSelected) {
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
