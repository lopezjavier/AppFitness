package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_set_type
import io.github.lopezjavier.appfitness.composeapp.resources.help_set_type_body
import io.github.lopezjavier.appfitness.composeapp.resources.help_set_type_cd
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_picker_title
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.SetTypeBadge
import io.github.lopezjavier.appfitness.designsystem.SetTypePickerSheet
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.SetType
import org.jetbrains.compose.resources.stringResource

/** Catalog section of the set type badge and its picker sheet (T-089). */
internal fun LazyListScope.setTypeItems() {
    item { SetTypeSection() }
}

@Composable
private fun SetTypeSection() {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableStateOf(SetType.WarmUp.ordinal) }
    CatalogSection(stringResource(Res.string.catalog_section_set_type), "catalog_section_set_type") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            SetType.entries.forEachIndexed { index, type ->
                SetTypeBadge(type, (index + 1).toString(), Modifier.testTag("catalog_set_type_badge_${type.name}"))
            }
            SetTypeBadge(
                type = SetType.entries[chosen],
                number = "1",
                modifier = Modifier.testTag("catalog_set_type_badge_button"),
                onClick = { pickerOpen = true },
            )
        }
        GhostButton(
            text = stringResource(Res.string.set_type_picker_title),
            onClick = { pickerOpen = true },
            modifier = Modifier.testTag("catalog_set_type_picker_open"),
        )
        HelpButton(
            contentDescription = stringResource(Res.string.help_set_type_cd),
            title = stringResource(Res.string.set_type_picker_title),
            body = stringResource(Res.string.help_set_type_body),
            testTag = "catalog_help_button",
        )
    }
    if (pickerOpen) {
        SetTypePickerSheet(
            current = SetType.entries[chosen],
            onSelect = {
                chosen = it.ordinal
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}
