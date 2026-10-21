@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.template_food_close_cd
import io.github.lopezjavier.appfitness.composeapp.resources.template_food_remove
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.feature.food.MicronutrientsCard
import io.github.lopezjavier.appfitness.feature.food.Preview
import io.github.lopezjavier.appfitness.feature.food.QuantityInput
import org.jetbrains.compose.resources.stringResource

private const val SHEET_TAG = "template_food_sheet"

/**
 * The sheet of one food of the comida: its grams (base unit only, straight into the draft; there is no per-sheet
 * save), the live nutrition preview, its micronutrients when known, and "Quitar de la comida" (hidden for the last
 * food). It closes by drag or the close button.
 */
@Composable
internal fun TemplateFoodSheet(
    open: OpenFood,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val row = open.row
    var microExpanded by rememberSaveable { mutableStateOf(false) }
    AppBottomSheet(onDismissRequest = onDismiss, testTagPrefix = SHEET_TAG) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            SheetHeader(row.name, onDismiss)
            QuantityInput(
                food = open.food.copy(portions = emptyList()),
                amountText = row.amountText,
                selectedPortionId = null,
                hasError = row.quantityError != null,
                onAmountChange = onAmountChange,
                onPortionSelect = {},
                testTagPrefix = SHEET_TAG,
            )
            Preview(row.kcal, row.proteinG, row.carbsG, row.fatG, "${SHEET_TAG}_preview")
            open.micronutrients?.let { micros ->
                Box(Modifier.testTag("${SHEET_TAG}_micronutrients")) {
                    MicronutrientsCard(micros, open.food.baseUnit, microExpanded, onExpandedChange = {
                        microExpanded =
                            it
                    })
                }
            }
            if (row.removable) {
                GhostButton(
                    text = stringResource(Res.string.template_food_remove),
                    onClick = onRemove,
                    tone = GhostTone.Destructive,
                    modifier = Modifier.fillMaxWidth().testTag("${SHEET_TAG}_remove"),
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(name: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("${SHEET_TAG}_name"),
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.template_food_close_cd),
            icon = Res.drawable.ic_close,
            testTag = "${SHEET_TAG}_close",
            onClick = onDismiss,
        )
    }
}
