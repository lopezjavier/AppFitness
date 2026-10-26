package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_remove
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_quantity_error
import io.github.lopezjavier.appfitness.composeapp.resources.ic_add
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_error_taken
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_error_too_long
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_add
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_add_failed
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_add_food
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_delete
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_item_amount
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_save
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_unsaved_hint
import io.github.lopezjavier.appfitness.composeapp.resources.template_manage_failed
import io.github.lopezjavier.appfitness.composeapp.resources.template_swipe_delete_cd
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.feature.food.KcalText
import io.github.lopezjavier.appfitness.feature.food.MacroTexts
import org.jetbrains.compose.resources.stringResource

private const val LOADING_ROWS = 4

/** From this font scale the method chip sits under the name instead of beside the amount. */
private const val LARGE_FONT_SCALE = 1.3f
private val SkeletonHeight = Spacing.xl * 2

/**
 * The details of a comida, directly editable (US-21, US-47, US-53): the name field, the total kcal and macros of the
 * draft with "Añadir alimento" and the trash icon, one swipeable row per food (tap opens its sheet) and, pinned at the
 * bottom, "Guardar" (secondary) and "Añadir" (primary: logs the foods as shown). Nothing is stored until "Guardar";
 * writes are disabled offline. The top bar (title and back) belongs to the shell.
 */
@Composable
fun TemplateDetailsScreen(
    state: TemplateDetailsUiState,
    onEvent: (TemplateDetailsEvent) -> Unit,
    modifier: Modifier = Modifier,
    actionFailed: Boolean = false,
) {
    Box(modifier.fillMaxSize().testTag("template_details_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            when (state) {
                TemplateDetailsUiState.Loading -> DetailsLoading()

                TemplateDetailsUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.template_details_load_error),
                        action = AppStateAction(stringResource(Res.string.state_retry)) {
                            onEvent(TemplateDetailsEvent.Retry)
                        },
                        testTagPrefix = "template_details_error_state",
                    )
                }

                is TemplateDetailsUiState.Content -> DetailsContent(state, onEvent, actionFailed)
            }
        }
    }
}

@Composable
private fun DetailsLoading() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin).testTag("template_details_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(LOADING_ROWS) { SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeight)) }
    }
}

@Composable
private fun DetailsContent(
    content: TemplateDetailsUiState.Content,
    onEvent: (TemplateDetailsEvent) -> Unit,
    actionFailed: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(top = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            DetailsHeader(content, onEvent)
            if (content.isOffline) OfflineBanner(Modifier.fillMaxWidth(), testTagPrefix = "template_details_offline")
            if (content.addFailed) {
                Text(
                    text = stringResource(Res.string.template_details_add_failed),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("template_details_add_failed"),
                )
            } else if (actionFailed) {
                Text(
                    text = stringResource(Res.string.template_manage_failed),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("template_details_action_failed"),
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("template_details_list"),
            contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(content.rows, key = { it.index }) { row -> DetailsRowItem(row, onEvent) }
        }
        DetailsFooter(content, onEvent)
    }
    DetailsSheets(content, onEvent)
}

/** The bottom area: the unsaved-changes hint and the "Guardar" (secondary) and "Añadir" (primary) buttons. */
@Composable
private fun DetailsFooter(content: TemplateDetailsUiState.Content, onEvent: (TemplateDetailsEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenMargin)
            .padding(bottom = Spacing.lg)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (content.isDirty) {
            Text(
                text = stringResource(Res.string.template_details_unsaved_hint),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("template_details_unsaved_hint"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton(
                text = stringResource(Res.string.template_details_save),
                onClick = { onEvent(TemplateDetailsEvent.SaveClicked) },
                enabled = content.canSave,
                modifier = Modifier.weight(1f).testTag("template_details_save"),
            )
            PrimaryButton(
                text = stringResource(Res.string.template_details_add),
                onClick = { onEvent(TemplateDetailsEvent.Add) },
                enabled = content.canAdd,
                loading = content.adding,
                modifier = Modifier.weight(1f).testTag("template_details_add"),
            )
        }
    }
}

/** The name field with its inline hint, and under it the total kcal and macros of the draft with "Añadir alimento". */
@Composable
private fun DetailsHeader(content: TemplateDetailsUiState.Content, onEvent: (TemplateDetailsEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppTextField(
            value = content.name,
            onValueChange = { onEvent(TemplateDetailsEvent.NameChanged(it)) },
            label = stringResource(Res.string.template_details_name_label),
            errorText = content.nameError?.let { stringResource(nameErrorText(it)) },
            testTagPrefix = "template_details_name_field",
            errorTestTag = "template_details_name_hint",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KcalText(content.totalKcal, Modifier.testTag("template_details_kcal"))
                Box(Modifier.testTag("template_details_macros")) {
                    MacroTexts(content.totalProteinG, content.totalCarbsG, content.totalFatG)
                }
            }
            AppIconButton(
                contentDescription = stringResource(Res.string.template_details_add_food),
                icon = Res.drawable.ic_add,
                testTag = "template_details_add_food",
                onClick = { onEvent(TemplateDetailsEvent.AddFoodClicked) },
                enabled = !content.busy,
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.template_details_delete),
                icon = Res.drawable.ic_delete,
                testTag = "template_details_delete",
                onClick = { onEvent(TemplateDetailsEvent.Delete) },
                enabled = !content.isOffline && !content.busy,
            )
        }
    }
}

private fun nameErrorText(error: TemplateNameError) = when (error) {
    TemplateNameError.Taken -> Res.string.save_template_error_taken
    TemplateNameError.TooLong -> Res.string.save_template_error_too_long
}

/** One food, swipeable to the start to remove it: tap opens its sheet. */
@Composable
private fun DetailsRowItem(row: DetailsRow, onEvent: (TemplateDetailsEvent) -> Unit) {
    SwipeToDeleteRow(
        deleteLabel = stringResource(Res.string.template_swipe_delete_cd, row.name),
        enabled = row.removable,
        onDelete = { onEvent(TemplateDetailsEvent.RemoveFood(row.index)) },
        backgroundTag = "template_details_item_${row.index}_delete_bg",
        label = stringResource(Res.string.action_remove),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button) { onEvent(TemplateDetailsEvent.OpenFood(row.index)) }
                .padding(vertical = Spacing.sm)
                .testTag("template_details_item_${row.index}"),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            RowHeader(row) { onEvent(TemplateDetailsEvent.OpenFood(row.index)) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KcalText(row.kcal)
                MacroTexts(row.proteinG, row.carbsG, row.fatG)
            }
            if (row.quantityError != null) {
                Text(
                    text = stringResource(Res.string.add_food_quantity_error),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("template_details_item_${row.index}_error"),
                )
            }
        }
    }
}

/** The food sheet, only while a food is open. */
@Composable
private fun DetailsSheets(content: TemplateDetailsUiState.Content, onEvent: (TemplateDetailsEvent) -> Unit) {
    content.openFood?.let { open ->
        TemplateFoodSheet(
            open = open,
            onAmountChange = { onEvent(TemplateDetailsEvent.FoodAmountChanged(it)) },
            onMethodChange = { onEvent(TemplateDetailsEvent.MethodChanged(it)) },
            onSave = { onEvent(TemplateDetailsEvent.SaveFood) },
            onRemove = { onEvent(TemplateDetailsEvent.RemoveFood(open.row.index)) },
            onDismiss = { onEvent(TemplateDetailsEvent.DismissFood) },
        )
    }
}

/** Name, portion, method chip (beside the amount, or under the name at large fonts) and amount of a row. */
@Composable
private fun RowHeader(row: DetailsRow, onOpen: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    val chip: @Composable () -> Unit = {
        CookingMethodChip(
            method = row.cookingMethod,
            onClick = onOpen,
            modifier = Modifier.testTag("template_details_cooking_chip_${row.index}"),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            row.portionLabel?.let {
                Text(text = it, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            }
            if (row.canCook && largeFont) chip()
        }
        if (row.canCook && !largeFont) chip()
        Text(
            text = stringResource(Res.string.template_details_item_amount, row.amountText, row.unit),
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.textSecondary,
        )
    }
}
