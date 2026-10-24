package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.template_add_failed
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_item_amount
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.template_preview_add
import io.github.lopezjavier.appfitness.composeapp.resources.template_preview_edit
import io.github.lopezjavier.appfitness.composeapp.resources.template_preview_empty
import io.github.lopezjavier.appfitness.composeapp.resources.template_preview_total
import io.github.lopezjavier.appfitness.designsystem.AppButtonDefaults
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import io.github.lopezjavier.appfitness.feature.food.KcalText
import io.github.lopezjavier.appfitness.feature.food.MacroTexts
import io.github.lopezjavier.appfitness.feature.food.formatNumber
import org.jetbrains.compose.resources.stringResource

private const val LOADING_ROWS = 3
private val SkeletonHeaderHeight = Spacing.xl * 2
private val SkeletonRowHeight = Spacing.xl * 2

/**
 * The read-only details of a comida (US-53): its name, totals and one row per food, with "Añadir" (logs it) and
 * "Editar" (opens the editor) pinned at the bottom. Nothing here changes the comida. The top bar belongs to the shell.
 */
@Composable
fun TemplatePreviewScreen(
    state: TemplatePreviewUiState,
    onEvent: (TemplatePreviewEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().testTag("template_preview_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            when (state) {
                TemplatePreviewUiState.Loading -> PreviewLoading()

                TemplatePreviewUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.template_details_load_error),
                        action = AppStateAction(stringResource(Res.string.state_retry)) {
                            onEvent(TemplatePreviewEvent.Retry)
                        },
                        testTagPrefix = "template_preview_error_state",
                    )
                }

                is TemplatePreviewUiState.Content -> PreviewContent(state, onEvent)
            }
        }
    }
}

@Composable
private fun PreviewLoading() {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("template_preview_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeaderHeight))
        repeat(LOADING_ROWS) { SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonRowHeight)) }
    }
}

@Composable
private fun PreviewContent(content: TemplatePreviewUiState.Content, onEvent: (TemplatePreviewEvent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (content.isOffline) {
            OfflineBanner(Modifier.fillMaxWidth(), testTagPrefix = "template_preview_offline")
        }
        PreviewHeader(content)
        if (content.rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    message = stringResource(Res.string.template_preview_empty),
                    testTagPrefix = "template_preview_empty",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("template_preview_list"),
                contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                itemsIndexed(content.rows) { index, row -> PreviewRowItem(index, row) }
            }
        }
        PreviewFooter(content, onEvent)
    }
}

/** The comida name as heading, with its total kcal and macros under it. */
@Composable
private fun PreviewHeader(content: TemplatePreviewUiState.Content) {
    Column(
        Modifier.fillMaxWidth().padding(
            horizontal = Spacing.screenMargin,
        ).padding(top = Spacing.lg, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = content.name,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() }.testTag("template_preview_name"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.template_preview_total, wholeHalfUp(content.totalKcal)),
                style = AppTheme.typography.labelNumeric,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("template_preview_kcal"),
            )
            Box(Modifier.testTag("template_preview_macros")) {
                MacroTexts(content.totalProteinG, content.totalCarbsG, content.totalFatG)
            }
        }
    }
}

/** One food, not interactive: name and amount, then the portion label, kcal and macros. */
@Composable
private fun PreviewRowItem(index: Int, row: PreviewRow) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .background(AppTheme.colors.surfaceRaised, AppShapes.lg)
            .padding(Spacing.lg)
            .semantics(mergeDescendants = true) {}
            .testTag("template_preview_item_$index"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
            Text(
                text = row.name,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.template_details_item_amount, formatNumber(row.amount), row.unit),
                style = AppTheme.typography.labelNumeric,
                color = AppTheme.colors.textSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.portionLabel.orEmpty(),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            KcalText(row.kcal)
            MacroTexts(row.proteinG, row.carbsG, row.fatG)
        }
    }
}

/** "Editar" on the left (wraps its content) and "Añadir" filling the rest, pinned under the list. */
@Composable
private fun PreviewFooter(content: TemplatePreviewUiState.Content, onEvent: (TemplatePreviewEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenMargin)
            .padding(top = Spacing.sm, bottom = Spacing.lg)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (content.addFailed) {
            Text(
                text = stringResource(Res.string.template_add_failed),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("template_preview_add_failed"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(
                text = stringResource(Res.string.template_preview_edit),
                onClick = { onEvent(TemplatePreviewEvent.Edit) },
                modifier = Modifier.widthIn(min = AppButtonDefaults.CompactMinWidth).testTag("template_preview_edit"),
            )
            PrimaryButton(
                text = stringResource(Res.string.template_preview_add),
                onClick = { onEvent(TemplatePreviewEvent.Add) },
                enabled = content.canAdd,
                loading = content.adding,
                modifier = Modifier.weight(1f).testTag("template_preview_add"),
            )
        }
    }
}
