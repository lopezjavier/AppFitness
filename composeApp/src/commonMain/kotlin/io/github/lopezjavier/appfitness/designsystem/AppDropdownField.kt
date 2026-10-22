@file:OptIn(ExperimentalMaterial3Api::class) // ExposedDropdownMenuBox is experimental; confined to this file.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import org.jetbrains.compose.resources.painterResource

/** One entry of an [AppDropdownField]; [key] identifies it in the option test tag. */
data class AppDropdownOption(val key: String, val label: String)

object AppDropdownFieldDefaults {
    /** The field and every option are 48 dp touch targets. */
    val MinHeight = Spacing.minTouchTarget
    val Shape = AppShapes.base
}

/**
 * Single-choice dropdown (05 §126): a labelled field showing [selectedLabel] that opens a menu of [options]. The field
 * is tagged "[testTagPrefix]_field" and each option "[testTagPrefix]_option_<key>"; the selected option is exposed as
 * selected to accessibility.
 */
@Composable
fun AppDropdownField(
    label: String,
    options: List<AppDropdownOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.key == selectedKey }?.label.orEmpty()
    val colors = AppTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = label, style = AppTheme.typography.labelTechnical, color = colors.textSecondary)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            Row(
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .heightIn(min = AppDropdownFieldDefaults.MinHeight)
                    .clip(AppDropdownFieldDefaults.Shape)
                    .background(colors.surfaceOverlay)
                    .border(AppBorder.thin, colors.borderStrong, AppDropdownFieldDefaults.Shape)
                    .padding(horizontal = Spacing.md)
                    .semantics(mergeDescendants = true) {}
                    .testTag("${testTagPrefix}_field"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = selectedLabel, style = AppTheme.typography.bodyLarge, color = colors.textPrimary)
                Icon(
                    painter = painterResource(Res.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(Spacing.xl),
                )
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = colors.surfaceOverlay,
            ) {
                options.forEach { option ->
                    val isSelected = option.key == selectedKey
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                style = AppTheme.typography.bodyLarge,
                                color = if (isSelected) colors.neonLime else colors.textPrimary,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(option.key)
                        },
                        modifier = Modifier
                            .heightIn(min = AppDropdownFieldDefaults.MinHeight)
                            .semantics { selected = isSelected }
                            .testTag("${testTagPrefix}_option_${option.key}"),
                    )
                }
            }
        }
    }
}
