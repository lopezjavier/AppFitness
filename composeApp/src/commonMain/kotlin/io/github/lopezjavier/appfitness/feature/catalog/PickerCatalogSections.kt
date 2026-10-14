package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.activity_high_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_high_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_moderate_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_moderate_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_selectable_card
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_wheel_picker
import io.github.lopezjavier.appfitness.composeapp.resources.unit_cm
import io.github.lopezjavier.appfitness.composeapp.resources.unit_kg
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_height_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_state_cm
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_state_decimal
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_state_kg
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_weight_decimal_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wheel_weight_kg_cd
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WheelPicker
import org.jetbrains.compose.resources.stringResource

private const val HEIGHT_DEFAULT = 178
private const val HEIGHT_MIN = 100
private const val HEIGHT_MAX = 250
private const val WEIGHT_KG_DEFAULT = 74
private const val WEIGHT_KG_MIN = 20
private const val WEIGHT_KG_MAX = 400
private const val DECIMAL_DEFAULT = 2
private const val DECIMAL_MAX = 9
private const val MODERATE = 0
private const val HIGH = 1

/** Catalog sections of the pickers (T-035): the wheel and the radio-style card. */
internal fun LazyListScope.pickerItems() {
    item { WheelPickerSection() }
    item { SelectableCardSection() }
}

@Composable
private fun WheelPickerSection() {
    var height by rememberSaveable { mutableIntStateOf(HEIGHT_DEFAULT) }
    var kilos by rememberSaveable { mutableIntStateOf(WEIGHT_KG_DEFAULT) }
    var decimal by rememberSaveable { mutableIntStateOf(DECIMAL_DEFAULT) }
    val cm = stringResource(Res.string.unit_cm)
    val kg = stringResource(Res.string.unit_kg)
    val stateCm = stringResource(Res.string.wheel_state_cm, height)
    val stateKg = stringResource(Res.string.wheel_state_kg, kilos)
    val stateDecimal = stringResource(Res.string.wheel_state_decimal, decimal)
    CatalogSection(stringResource(Res.string.catalog_section_wheel_picker), "catalog_section_wheel_picker") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelPicker(
                range = HEIGHT_MIN..HEIGHT_MAX,
                value = height,
                onValueChange = { height = it },
                label = { it.toString() },
                contentDescription = stringResource(Res.string.wheel_height_cd),
                stateDescription = { stateCm },
                testTagPrefix = "catalog_wheel_height",
            )
            Text(cm, style = AppTheme.typography.labelTechnical, color = AppTheme.colors.textSecondary)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WheelPicker(
                range = WEIGHT_KG_MIN..WEIGHT_KG_MAX,
                value = kilos,
                onValueChange = { kilos = it },
                label = { it.toString() },
                contentDescription = stringResource(Res.string.wheel_weight_kg_cd),
                stateDescription = { stateKg },
                testTagPrefix = "catalog_wheel_weight_kg",
            )
            Text(",", style = AppTheme.typography.labelNumeric, color = AppTheme.colors.textPrimary)
            WheelPicker(
                range = 0..DECIMAL_MAX,
                value = decimal,
                onValueChange = { decimal = it },
                label = { it.toString() },
                contentDescription = stringResource(Res.string.wheel_weight_decimal_cd),
                stateDescription = { stateDecimal },
                testTagPrefix = "catalog_wheel_weight_decimal",
            )
            Text(kg, style = AppTheme.typography.labelTechnical, color = AppTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun SelectableCardSection() {
    var selected by rememberSaveable { mutableIntStateOf(MODERATE) }
    CatalogSection(stringResource(Res.string.catalog_section_selectable_card), "catalog_section_selectable_card") {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            SelectableCard(
                title = stringResource(Res.string.activity_moderate_title),
                description = stringResource(Res.string.activity_moderate_description),
                selected = selected == MODERATE,
                onClick = { selected = MODERATE },
                modifier = Modifier.testTag("catalog_selectable_card_moderate"),
            )
            SelectableCard(
                title = stringResource(Res.string.activity_high_title),
                description = stringResource(Res.string.activity_high_description),
                selected = selected == HIGH,
                onClick = { selected = HIGH },
                modifier = Modifier.testTag("catalog_selectable_card_high"),
            )
        }
    }
}
