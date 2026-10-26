@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its content model.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_help

/** Text of a help sheet: [paragraphs] first, then an optional bulleted list of [bullets]. */
@Immutable
data class HelpContent(val paragraphs: List<String>, val bullets: List<String> = emptyList()) {
    companion object {
        /** A single paragraph: the content of the `body: String` overload of [HelpButton]. */
        fun fromBody(body: String): HelpContent = HelpContent(listOf(body))
    }
}

/**
 * Small "?" icon button (48 dp target) that opens a read-only [AppInfoSheet] with a short [title] and [body].
 * The button is tagged [testTag], the sheet `<testTag>_sheet` and its close button `<testTag>_sheet_close`.
 * [contentDescription] reads "Ayuda: <tema>". The open state survives recreation. Place it as an icon in a
 * title or header row, never as a full-width button.
 */
@Composable
fun HelpButton(
    contentDescription: String,
    title: String,
    body: String,
    testTag: String,
    modifier: Modifier = Modifier,
) = HelpButton(contentDescription, title, HelpContent.fromBody(body), testTag, modifier)

/**
 * [HelpButton] with structured [content]: the paragraphs and bullets sit in one column tagged
 * `<testTag>_sheet_body`; bullet `n` (from 1) is tagged `<testTag>_sheet_bullet_<n>`.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
fun HelpButton(
    contentDescription: String,
    title: String,
    content: HelpContent,
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
        AppInfoSheet(title = title, onDismiss = { open = false }, testTagPrefix = "${testTag}_sheet") {
            Column(
                Modifier.semantics(mergeDescendants = true) {}.testTag("${testTag}_sheet_body"),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                content.paragraphs.forEach { HelpText(it) }
                content.bullets.forEachIndexed { index, bullet ->
                    Row(
                        Modifier.testTag("${testTag}_sheet_bullet_${index + 1}"),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text("•", style = AppTheme.typography.bodyLarge, color = AppTheme.colors.textSecondary)
                        HelpText(bullet, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
        modifier = modifier,
    )
}
