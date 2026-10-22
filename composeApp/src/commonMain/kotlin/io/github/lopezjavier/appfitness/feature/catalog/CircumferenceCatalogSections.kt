package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_circumference_row
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_dropdown_field
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_field_site
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_row_edit_label
import io.github.lopezjavier.appfitness.designsystem.AppDropdownField
import io.github.lopezjavier.appfitness.designsystem.AppDropdownOption
import io.github.lopezjavier.appfitness.designsystem.CircumferenceRow
import org.jetbrains.compose.resources.stringResource

private val CatalogSites = listOf(
    AppDropdownOption("waist", "Cintura"),
    AppDropdownOption("hip", "Cadera"),
    AppDropdownOption("thigh", "Muslo"),
)

/** Catalog sections of the circumference components: the dropdown field and the history row. */
internal fun LazyListScope.circumferenceItems() {
    item { DropdownFieldSection() }
    item { CircumferenceRowSection() }
}

@Composable
private fun DropdownFieldSection() {
    var selected by rememberSaveable { mutableStateOf("waist") }
    CatalogSection(
        stringResource(Res.string.catalog_section_dropdown_field),
        "catalog_section_dropdown_field",
    ) {
        AppDropdownField(
            label = stringResource(Res.string.circumference_field_site),
            options = CatalogSites,
            selectedKey = selected,
            onSelect = { selected = it },
            testTagPrefix = "catalog_dropdown",
        )
    }
}

@Composable
private fun CircumferenceRowSection() {
    CatalogSection(
        stringResource(Res.string.catalog_section_circumference_row),
        "catalog_section_circumference_row",
    ) {
        Column(Modifier.testTag("catalog_circumference_rows")) {
            val clickLabel = stringResource(Res.string.circumference_row_edit_label)
            CircumferenceRow("84,5 cm", "12 oct · 08:30", clickLabel, onClick = {})
            CircumferenceRow("85,0 cm", "5 oct · 07:45", clickLabel, onClick = {})
        }
    }
}
