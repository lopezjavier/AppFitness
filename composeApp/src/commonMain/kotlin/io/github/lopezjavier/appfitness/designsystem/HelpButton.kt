package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.help_close_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.ic_help
import org.jetbrains.compose.resources.stringResource

/**
 * Small "?" icon button (48 dp target) that opens a read-only [AppBottomSheet] with a short [title] and [body].
 * The button is tagged [testTag], the sheet `<testTag>_sheet` and its close button `<testTag>_sheet_close`.
 * [contentDescription] reads "Ayuda: <tema>". The open state survives recreation. Place it as an icon in a
 * title or header row, never as a full-width button.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
fun HelpButton(
    contentDescription: String,
    title: String,
    body: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    AppIconButton(
        contentDescription = contentDescription,
        icon = Res.drawable.ic_help,
        testTag = testTag,
        onClick = { open = true },
        modifier = modifier,
        tint = AppTheme.colors.textSecondary,
    )
    if (open) {
        AppBottomSheet(onDismissRequest = { open = false }, testTagPrefix = "${testTag}_sheet") {
            Column(
                Modifier.padding(
                    horizontal = Spacing.screenMargin,
                ).padding(bottom = Spacing.lg).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = AppTheme.typography.titleLarge,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    AppIconButton(
                        contentDescription = stringResource(Res.string.help_close_cd),
                        icon = Res.drawable.ic_close,
                        testTag = "${testTag}_sheet_close",
                        onClick = { open = false },
                    )
                }
                Text(
                    text = body,
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${testTag}_sheet_body"),
                )
            }
        }
    }
}
