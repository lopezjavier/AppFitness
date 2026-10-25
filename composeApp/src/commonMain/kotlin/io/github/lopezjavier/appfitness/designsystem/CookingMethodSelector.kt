package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.cooking_method_air_fryer
import io.github.lopezjavier.appfitness.composeapp.resources.cooking_method_fried
import io.github.lopezjavier.appfitness.composeapp.resources.cooking_method_griddle
import io.github.lopezjavier.appfitness.composeapp.resources.cooking_method_group_cd
import io.github.lopezjavier.appfitness.composeapp.resources.cooking_method_raw
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** From this font scale the four options go in a 2x2 grid instead of a wrapping row. */
private const val GRID_FONT_SCALE = 1.3f
private const val GRID_COLUMNS = 2

/** The Spanish label of [this] method. */
internal fun CookingMethod.labelRes(): StringResource = when (this) {
    CookingMethod.Raw -> Res.string.cooking_method_raw
    CookingMethod.Griddle -> Res.string.cooking_method_griddle
    CookingMethod.AirFryer -> Res.string.cooking_method_air_fryer
    CookingMethod.Fried -> Res.string.cooking_method_fried
}

/**
 * Single-select choice of the cooking method of a food (ADR-60): "Crudo", "A la plancha", "Air fryer" and "Frito".
 * Stateless. Options are tagged `<tagPrefix>_<wireName>` (`raw`, `griddle`, `air_fryer`, `fried`); from a font scale of
 * `GRID_FONT_SCALE` (1.3) they sit in a 2x2 grid so no label is truncated. Tapping the
 * selected option does nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CookingMethodSelector(
    selected: CookingMethod,
    onSelect: (CookingMethod) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "cooking",
    enabled: Boolean = true,
) {
    val group = stringResource(Res.string.cooking_method_group_cd)
    val groupModifier = modifier.selectableGroup().semantics { contentDescription = group }
    val labelled = CookingMethod.entries.map { it to stringResource(it.labelRes()) }
    val chip: @Composable (Pair<CookingMethod, String>) -> Unit = { (method, label) ->
        AppChip(
            label = label,
            selected = method == selected,
            onClick = { if (enabled && method != selected) onSelect(method) },
            modifier = Modifier.testTag("${tagPrefix}_${method.wireName}"),
        )
    }
    if (LocalDensity.current.fontScale >= GRID_FONT_SCALE) {
        Column(groupModifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            labelled.chunked(GRID_COLUMNS).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { rowItems.forEach { chip(it) } }
            }
        }
    } else {
        FlowRow(
            groupModifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) { labelled.forEach { chip(it) } }
    }
}

@Preview
@Composable
private fun CookingMethodSelectorPreview() {
    AppTheme { CookingMethodSelector(selected = CookingMethod.Griddle, onSelect = {}) }
}
