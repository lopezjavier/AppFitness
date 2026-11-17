package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_auto_awesome

/**
 * A coach proposal waiting for a decision (E13 Q5): [title], the [payload] it would apply, the [reason] in plain
 * words and the [dataUsed] line, then the accept ([acceptText], primary) and discard ([discardText], ghost) buttons
 * side by side, labelled by the caller ("Aplicar" / "Ahora no" for calories, "Aceptar" / "Descartar" for plans).
 * Both buttons are disabled while [busy] (the accepted one shows the progress); [acceptEnabled] lets a caller block
 * only the accept button, for example offline, and [footer] holds the hint that explains why.
 * When [content] is set it replaces the title, payload, reason and data lines (for example the phase row of the
 * end-of-block review, 05 section 9.1).
 * [highlighted] marks the card as waiting for a decision (R-94). Buttons come from [DecisionButtonRow].
 * Tags: the card is [testTagPrefix], the buttons `<testTagPrefix>_accept` and `<testTagPrefix>_discard`.
 */
@Composable
@Suppress("LongParameterList") // Slots fixed by the proposal contract: copy, state and the two actions.
fun CoachProposalCard(
    acceptText: String,
    discardText: String,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    title: String = "",
    payload: String = "",
    reason: String = "",
    dataUsed: String = "",
    busy: Boolean = false,
    acceptEnabled: Boolean = true,
    highlighted: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    AppCard(
        modifier.fillMaxWidth().testTag(testTagPrefix),
        highlighted = highlighted,
        glow = AppTheme.colors.neonLime,
    ) {
        if (content != null) {
            content()
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Res.drawable.ic_auto_awesome, AppTheme.colors.neonLime)
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(text = payload, style = AppTheme.typography.headlineMetric, color = AppTheme.colors.neonLime)
                Text(text = reason, style = AppTheme.typography.bodyLarge, color = AppTheme.colors.textPrimary)
                Text(text = dataUsed, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
        }
        DecisionButtonRow(
            acceptText = acceptText,
            discardText = discardText,
            onAccept = onAccept,
            onDiscard = onDiscard,
            testTagPrefix = testTagPrefix,
            busy = busy,
            acceptEnabled = acceptEnabled,
        )
        footer?.invoke()
    }
}
