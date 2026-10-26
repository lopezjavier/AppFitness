package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.save_template_title
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_item_amount
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CookingMethodSelector
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.NameSheet
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.feature.diary.amountText
import org.jetbrains.compose.resources.stringResource

private val SkeletonHeight = Spacing.xl * 2
private const val SHEET_PREFIX = "save_template_name"
private const val MAX_NAME_LENGTH = 60

/**
 * Save-as-comida sheet (US-21) on [NameSheet] ("Guardar como comida"): the focused name field with the duplicate
 * message "Ya existe una comida con ese nombre" (checked against the user's comidas), the solid foods of the section
 * with their cooking method, and "Guardar" (disabled offline, with the one offline hint). A failed save is reported by
 * the caller through the app messages (R-28). [onDismiss] runs when the sheet closes.
 */
@Composable
fun SaveTemplateSheet(
    state: SaveTemplateUiState,
    onEvent: (SaveTemplateEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held outside the sheet window so it is restored with the screen (ADR-40).
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    when (state) {
        SaveTemplateUiState.Loading -> AppInfoSheet(
            title = stringResource(Res.string.save_template_title),
            onDismiss = onDismiss,
            modifier = modifier,
            testTagPrefix = SHEET_PREFIX,
        ) {
            SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeight).testTag("save_template_loading"))
        }

        SaveTemplateUiState.Error -> AppInfoSheet(
            title = stringResource(Res.string.save_template_title),
            onDismiss = onDismiss,
            modifier = modifier,
            testTagPrefix = SHEET_PREFIX,
        ) {
            ErrorState(
                message = stringResource(Res.string.save_template_load_error),
                action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(SaveTemplateEvent.Retry) },
                testTagPrefix = "save_template_error_state",
            )
        }

        is SaveTemplateUiState.Content -> {
            val content = state.content
            NameSheet(
                title = stringResource(Res.string.save_template_title),
                label = stringResource(Res.string.save_template_name_label),
                itemLabel = "una comida",
                // A name the server refused as taken counts too (the check can be stale).
                existingNames = content.existingNames + listOfNotNull(content.name.takeIf { content.nameTaken }),
                onSave = {
                    onEvent(SaveTemplateEvent.NameChanged(it))
                    onEvent(SaveTemplateEvent.Submit)
                },
                onDismiss = onDismiss,
                modifier = modifier,
                saving = content.saving,
                commitHint = if (content.isOffline) offlineHint("guardar") else null,
                maxLength = MAX_NAME_LENGTH,
                testTagPrefix = SHEET_PREFIX,
                canSubmit = !content.isOffline && content.itemCount > 0,
                extra = {
                    CookableItems(content.items, expandedId, { expandedId = it }, onEvent, enabled = !content.saving)
                },
            )
        }
    }
}

/**
 * The solid foods of the section, each with name, amount and its method chip. Tapping a chip expands the selector under
 * that row (one at a time); the choice only fills the draft that "Guardar" stores. Drinks have no chip.
 */
@Composable
private fun CookableItems(
    items: List<SaveTemplateItemRow>,
    expandedId: String?,
    onExpandedChange: (String?) -> Unit,
    onEvent: (SaveTemplateEvent) -> Unit,
    enabled: Boolean,
) {
    val cookable = items.filter { it.canCook }
    if (cookable.isEmpty()) return
    Column(Modifier.padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        cookable.forEach { item ->
            val expanded = expandedId == item.entryId
            Column(Modifier.fillMaxWidth().testTag("save_template_item_${item.entryId}")) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.foodName,
                            style = AppTheme.typography.bodyLarge,
                            color = AppTheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                Res.string.template_details_item_amount,
                                amountText(item.amount),
                                item.baseUnit ?: "g",
                            ),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                    CookingMethodChip(
                        method = item.method,
                        onClick = { if (enabled) onExpandedChange(if (expanded) null else item.entryId) },
                        modifier = Modifier.testTag("save_template_cooking_chip_${item.entryId}"),
                    )
                }
                if (expanded) {
                    CookingMethodSelector(
                        selected = item.method,
                        onSelect = { onEvent(SaveTemplateEvent.MethodChanged(item.entryId, it)) },
                        tagPrefix = "save_template_cooking",
                        enabled = enabled,
                    )
                }
            }
        }
    }
}
