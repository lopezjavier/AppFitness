package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_add_portion
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_portion_amount
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_portion_label
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_portion_amount_g
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_portion_amount_ml
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_portion_label
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_portion_remove_cd
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_portions
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PortionError
import org.jetbrains.compose.resources.stringResource

private const val MAX_LABEL_LENGTH = 100
private const val AMOUNT_DECIMALS = 1
private const val AMOUNT_WEIGHT = 0.7f

/** The named portions of the new food: one row per portion (label + amount in the base unit) and the add button. */
@Composable
internal fun PortionsBlock(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(Res.string.create_food_portions),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
        )
        state.portions.forEachIndexed { index, portion ->
            PortionRow(index, portion, state.baseUnit, state.errors.portions[index], onEvent)
        }
        SecondaryButton(
            text = stringResource(Res.string.create_food_add_portion),
            onClick = { onEvent(CreateFoodEvent.AddPortion) },
            modifier = Modifier.fillMaxWidth().testTag("create_food_add_portion_button"),
            enabled = state.canAddPortion,
        )
    }
}

@Composable
private fun PortionRow(
    index: Int,
    portion: PortionDraft,
    baseUnit: String,
    error: PortionError?,
    onEvent: (CreateFoodEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            AppTextField(
                value = portion.label,
                onValueChange = { onEvent(CreateFoodEvent.PortionLabelChanged(index, it)) },
                label = stringResource(Res.string.create_food_portion_label),
                modifier = Modifier.weight(1f),
                maxLength = MAX_LABEL_LENGTH,
                testTagPrefix = "create_food_portion_label_$index",
            )
            Column(Modifier.weight(AMOUNT_WEIGHT), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val amountLabel = stringResource(
                    if (baseUnit ==
                        "ml"
                    ) {
                        Res.string.create_food_portion_amount_ml
                    } else {
                        Res.string.create_food_portion_amount_g
                    },
                )
                Text(text = amountLabel, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                NumberInput(
                    value = portion.amount,
                    onValueChange = { onEvent(CreateFoodEvent.PortionAmountChanged(index, it)) },
                    label = amountLabel,
                    modifier = Modifier.fillMaxWidth().testTag("create_food_portion_amount_$index"),
                    maxDecimals = AMOUNT_DECIMALS,
                    showLabel = false,
                )
            }
            AppIconButton(
                contentDescription = stringResource(Res.string.create_food_portion_remove_cd),
                icon = Res.drawable.ic_delete,
                testTag = "create_food_portion_remove_$index",
                onClick = { onEvent(CreateFoodEvent.RemovePortion(index)) },
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        error?.let {
            FieldErrorText(
                stringResource(
                    if (it == PortionError.LabelInvalid) {
                        Res.string.create_food_error_portion_label
                    } else {
                        Res.string.create_food_error_portion_amount
                    },
                ),
                "create_food_portion_error_$index",
            )
        }
    }
}
