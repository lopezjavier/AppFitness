package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.action_edit
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_action_sheet_open
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_action_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_editor_closed
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_editor_field_label
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_editor_offline_switch
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_messages_sample
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_messages_show
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_offline_state_flip
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_offline_state_reloads
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_screen_sample_body
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_screen_sample_footer
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_action_sheet
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_editor
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_messages
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_offline_state
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_screen
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.designsystem.AppActionSheet
import io.github.lopezjavier.appfitness.designsystem.AppBorder
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppSheetAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppToggleChip
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.LocalPinnedFooter
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource

private val SampleHeight: Dp = Spacing.xl * 16

/** Catalog sections of the T-118 templates: screen, editor, messages, offline state and the actions sheet. */
internal fun LazyListScope.templateItems() {
    item { ScreenSection() }
    item { EditorSection() }
    item { MessagesSection() }
    item { OfflineStateSection() }
    item { ActionSheetSection() }
}

/** A fixed-height frame for an embedded template; its footer must not lift the shell's message host. */
@Composable
private fun SampleFrame(testTag: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPinnedFooter provides null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(SampleHeight)
                .border(AppBorder.thin, AppTheme.colors.borderSubtle, AppShapes.md)
                .testTag(testTag),
        ) { content() }
    }
}

@Composable
private fun ScreenSection() {
    CatalogSection(stringResource(Res.string.catalog_section_screen), "catalog_section_screen") {
        SampleFrame("catalog_screen_sample") {
            AppScreen(
                title = stringResource(Res.string.catalog_section_screen),
                onBack = {},
                footer = {
                    PrimaryButton(
                        text = stringResource(Res.string.catalog_screen_sample_footer),
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().testTag("catalog_screen_sample_footer_button"),
                    )
                },
                testTagPrefix = "catalog_screen_sample_bar",
            ) {
                Text(
                    text = stringResource(Res.string.catalog_screen_sample_body),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun EditorSection() {
    var name by rememberSaveable { mutableStateOf("") }
    var offline by rememberSaveable { mutableStateOf(false) }
    var round by rememberSaveable { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf(false) }
    val messages = LocalAppMessages.current
    val closedText = stringResource(Res.string.catalog_editor_closed)
    CatalogSection(stringResource(Res.string.catalog_section_editor), "catalog_section_editor") {
        AppToggleChip(
            label = stringResource(Res.string.catalog_editor_offline_switch),
            checked = offline,
            onCheckedChange = { offline = it },
            modifier = Modifier.testTag("catalog_editor_offline_chip"),
        )
        SampleFrame("catalog_editor_sample") {
            // A new key per round gives each save a fresh editor (its close latch is one-shot by design).
            key(round) {
                AppEditorScreen(
                    title = stringResource(Res.string.catalog_section_editor),
                    isDirty = name.isNotEmpty(),
                    isSaving = false,
                    isOnline = !offline,
                    saved = saved,
                    onSave = { saved = true },
                    onClose = {
                        messages.show(closedText)
                        name = ""
                        saved = false
                        round++
                    },
                    handleSystemBack = false,
                    testTagPrefix = "catalog_editor",
                ) {
                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(Res.string.catalog_editor_field_label),
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        testTagPrefix = "catalog_editor_field",
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagesSection() {
    val messages = LocalAppMessages.current
    val text = stringResource(Res.string.catalog_messages_sample)
    CatalogSection(stringResource(Res.string.catalog_section_messages), "catalog_section_messages") {
        SecondaryButton(
            text = stringResource(Res.string.catalog_messages_show),
            onClick = { messages.show(text) },
            modifier = Modifier.fillMaxWidth().testTag("catalog_messages_show"),
        )
    }
}

@Composable
private fun OfflineStateSection() {
    val connectivity = remember { MutableStateFlow(false) }
    var reloads by rememberSaveable { mutableIntStateOf(0) }
    CatalogSection(stringResource(Res.string.catalog_section_offline_state), "catalog_section_offline_state") {
        OfflineState(
            isOnline = connectivity,
            onReload = { reloads++ },
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(Res.string.catalog_offline_state_flip),
            onClick = { connectivity.value = !connectivity.value },
            modifier = Modifier.fillMaxWidth().testTag("catalog_offline_state_flip"),
        )
        Text(
            text = stringResource(Res.string.catalog_offline_state_reloads, reloads.toString()),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("catalog_offline_state_reloads"),
        )
    }
}

@Composable
private fun ActionSheetSection() {
    var open by rememberSaveable { mutableStateOf(false) }
    val edit = stringResource(Res.string.action_edit)
    val delete = stringResource(Res.string.action_delete)
    CatalogSection(stringResource(Res.string.catalog_section_action_sheet), "catalog_section_action_sheet") {
        SecondaryButton(
            text = stringResource(Res.string.catalog_action_sheet_open),
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().testTag("catalog_action_sheet_open"),
        )
    }
    if (open) {
        AppActionSheet(
            title = stringResource(Res.string.catalog_action_sheet_title),
            actions =
            listOf(
                AppSheetAction(Res.drawable.ic_edit, edit, onClick = {}),
                AppSheetAction(Res.drawable.ic_delete, delete, onClick = {}),
            ),
            onDismiss = { open = false },
        )
    }
}
