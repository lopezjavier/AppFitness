package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_circumference_row
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_row_edit_label
import io.github.lopezjavier.appfitness.designsystem.CircumferenceRow
import org.jetbrains.compose.resources.stringResource

/** Catalog section of the circumference history row. */
internal fun LazyListScope.circumferenceItems() {
    item { CircumferenceRowSection() }
}

@Composable
private fun CircumferenceRowSection() {
    CatalogSection(
        stringResource(Res.string.catalog_section_circumference_row),
        "catalog_section_circumference_row",
    ) {
        Column(Modifier.testTag("catalog_circumference_rows")) {
            val clickLabel = stringResource(Res.string.circumference_row_edit_label)
            CircumferenceRow("84,5 cm", "12 oct", clickLabel, onClick = {})
            CircumferenceRow("85,0 cm", "5 oct", clickLabel, onClick = {})
        }
    }
}
