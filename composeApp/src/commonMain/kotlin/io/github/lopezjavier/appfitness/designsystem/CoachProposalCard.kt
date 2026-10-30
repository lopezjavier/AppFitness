package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * A coach proposal waiting for a decision (E13 Q5): [title], the [payload] it would apply, the [reason] in plain
 * words and the [dataUsed] line, then "Aceptar" ([acceptText], primary) and "Descartar" ([discardText], ghost) side by
 * side. Both buttons are disabled while [busy] (the accepted one shows the progress); [acceptEnabled] lets a caller
 * block only "Aceptar", for example offline, and [footer] holds the hint that explains why.
 * Tags: the card is [testTagPrefix], the buttons `<testTagPrefix>_accept` and `<testTagPrefix>_discard`.
 */
@Composable
@Suppress("LongParameterList") // Slots fixed by the proposal contract: copy, state and the two actions.
fun CoachProposalCard(
    title: String,
    payload: String,
    reason: String,
    dataUsed: String,
    acceptText: String,
    discardText: String,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    acceptEnabled: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
) {
    AppCard(modifier.fillMaxWidth().testTag(testTagPrefix)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = payload, style = AppTheme.typography.titleLarge, color = AppTheme.colors.textPrimary)
            Text(text = reason, style = AppTheme.typography.bodyLarge, color = AppTheme.colors.textPrimary)
            Text(text = dataUsed, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            GhostButton(
                text = discardText,
                onClick = onDiscard,
                modifier = Modifier.weight(1f).testTag("${testTagPrefix}_discard"),
                enabled = !busy,
                tone = GhostTone.Neutral,
            )
            PrimaryButton(
                text = acceptText,
                onClick = onAccept,
                modifier = Modifier.weight(1f).testTag("${testTagPrefix}_accept"),
                enabled = !busy && acceptEnabled,
                loading = busy,
            )
        }
        footer?.invoke()
    }
}
