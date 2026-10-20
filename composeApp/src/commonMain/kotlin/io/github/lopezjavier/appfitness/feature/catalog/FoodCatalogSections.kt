package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_amount_label_g
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_portion_count_label
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_portion_option
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_unit_g
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_favourite_toggle
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_portion_selector
import io.github.lopezjavier.appfitness.composeapp.resources.food_favourite_add_cd
import io.github.lopezjavier.appfitness.composeapp.resources.food_favourite_remove_cd
import io.github.lopezjavier.appfitness.designsystem.FavouriteToggle
import io.github.lopezjavier.appfitness.designsystem.PortionOption
import io.github.lopezjavier.appfitness.designsystem.PortionSelector
import org.jetbrains.compose.resources.stringResource

/** Catalog sections of the food components: the favourite star and the quantity / portion selector. */
internal fun LazyListScope.foodItems() {
    item { FavouriteToggleSection() }
    item { PortionSelectorSection() }
}

@Composable
private fun FavouriteToggleSection() {
    var selected by rememberSaveable { mutableStateOf(false) }
    val add = stringResource(Res.string.food_favourite_add_cd)
    val remove = stringResource(Res.string.food_favourite_remove_cd)
    CatalogSection(
        stringResource(Res.string.catalog_section_favourite_toggle),
        "catalog_section_favourite_toggle",
    ) {
        Row {
            FavouriteToggle(
                selected = selected,
                onToggle = { selected = !selected },
                contentDescription = if (selected) remove else add,
                modifier = Modifier.testTag("catalog_favourite_toggle"),
            )
            FavouriteToggle(
                selected = false,
                onToggle = {},
                contentDescription = add,
                modifier = Modifier.testTag("catalog_favourite_toggle_disabled"),
                enabled = false,
            )
        }
    }
}

@Composable
private fun PortionSelectorSection() {
    var amount by rememberSaveable { mutableStateOf("2") }
    var portion by rememberSaveable { mutableStateOf<String?>("egg") }
    CatalogSection(
        stringResource(Res.string.catalog_section_portion_selector),
        "catalog_section_portion_selector",
    ) {
        PortionSelector(
            amountText = amount,
            onAmountChange = { amount = it },
            amountLabel = stringResource(
                if (portion == null) Res.string.add_food_amount_label_g else Res.string.add_food_portion_count_label,
            ),
            baseLabel = stringResource(Res.string.add_food_unit_g),
            portions = listOf(
                PortionOption("egg", stringResource(Res.string.add_food_portion_option, "1 huevo", "55", "g")),
            ),
            selectedPortionId = portion,
            onPortionSelect = {
                portion = it
                amount = "1"
            },
            testTagPrefix = "catalog_portion",
        )
    }
}
