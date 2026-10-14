package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_dialog_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_bottom_sheet
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_confirm_dialog
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_fab
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_logo
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_sheet_close
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_sheet_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_state_pressed
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_topbar_count
import io.github.lopezjavier.appfitness.composeapp.resources.fab_label
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_message
import io.github.lopezjavier.appfitness.composeapp.resources.sign_out_dialog_title
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppLogo
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.UniversalFab
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Catalog sections of the overlay components: the FAB and the bottom sheet (T-027). */
internal fun LazyListScope.overlayItems() {
    item { FabSection() }
    item { BottomSheetSection() }
    item { ConfirmDialogSection() }
    item { LogoSection() }
}

@Composable
private fun FabSection() {
    var count by rememberSaveable { mutableIntStateOf(0) }
    val label = stringResource(Res.string.fab_label)
    val pressedSource = remember { MutableInteractionSource() }
    LaunchedEffect(pressedSource) { pressedSource.emit(PressInteraction.Press(Offset.Zero)) }
    CatalogSection(stringResource(Res.string.catalog_section_fab), "catalog_section_fab") {
        UniversalFab(label, onClick = { count++ }, modifier = Modifier.testTag("catalog_fab"))
        Text(
            text = stringResource(Res.string.catalog_topbar_count, count.toString()),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("catalog_fab_count"),
        )
        VariantCaption(stringResource(Res.string.catalog_state_pressed))
        UniversalFab(
            label,
            onClick = {},
            modifier = Modifier.testTag("catalog_fab_pressed"),
            interactionSource = pressedSource,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetSection() {
    var open by rememberSaveable { mutableStateOf(false) }
    CatalogSection(stringResource(Res.string.catalog_section_bottom_sheet), "catalog_section_bottom_sheet") {
        SecondaryButton(
            text = stringResource(Res.string.catalog_sheet_open),
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().testTag("catalog_sheet_open"),
        )
    }
    if (open) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        AppBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            testTagPrefix = "catalog_sheet",
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = Spacing.screenMargin, end = Spacing.screenMargin, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.catalog_sheet_title),
                    style = AppTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }.testTag("catalog_sheet_title"),
                )
                Text(
                    text = stringResource(Res.string.wip_message),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
                PrimaryButton(
                    text = stringResource(Res.string.catalog_sheet_close),
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { open = false } },
                    modifier = Modifier.fillMaxWidth().testTag("catalog_sheet_close"),
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialogSection() {
    var open by rememberSaveable { mutableStateOf(false) }
    CatalogSection(stringResource(Res.string.catalog_section_confirm_dialog), "catalog_section_confirm_dialog") {
        SecondaryButton(
            text = stringResource(Res.string.catalog_dialog_open),
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().testTag("catalog_dialog_open"),
        )
    }
    if (open) {
        AppConfirmDialog(
            title = stringResource(Res.string.sign_out_dialog_title),
            message = stringResource(Res.string.sign_out_dialog_message),
            confirmLabel = stringResource(Res.string.sign_out_dialog_confirm),
            cancelLabel = stringResource(Res.string.sign_out_dialog_cancel),
            onConfirm = { open = false },
            onDismiss = { open = false },
            testTagPrefix = "catalog_dialog",
        )
    }
}

@Composable
private fun LogoSection() {
    CatalogSection(stringResource(Res.string.catalog_section_logo), "catalog_section_logo") {
        AppLogo(modifier = Modifier.testTag("catalog_logo"))
    }
}
