package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** How a [CoachChangeRow] reads: decides the tone of the status chip (the chip text itself is the caller's). */
enum class CoachChangeRowState { Applied, Undone, Pending, NoRange, Kept, Decided }

/** One text action of a [CoachChangeRow]: [primary] fills it, [loading] shows the progress on it. */
@Immutable
data class CoachChangeRowAction(
    val text: String,
    val testTag: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val loading: Boolean = false,
    val contentDescription: String? = null,
)

/**
 * A coach load change as a card row (T-228, ADR-88): the exercise [headline] (a tap on it calls [onHeadlineClick]),
 * the [reason] in plain words, an optional [detail] line and, for a closed change, a [statusText] chip ("Deshecho",
 * "Subida aceptada"; text, never colour alone). [actions] are text buttons below; all are disabled when [enabled] is
 * false (busy or offline) and [footer] holds the hint that says why. The card is tagged [testTagPrefix], the status
 * chip `<testTagPrefix>_status`.
 */
@Composable
@Suppress("LongParameterList") // Slots fixed by the row contract: copy, state and actions.
fun CoachChangeRow(
    headline: String,
    reason: String,
    state: CoachChangeRowState,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    statusText: String? = null,
    actions: List<CoachChangeRowAction> = emptyList(),
    enabled: Boolean = true,
    onHeadlineClick: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    AppCard(modifier.fillMaxWidth().testTag(testTagPrefix)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (headline.isNotBlank()) {
                    Text(
                        text = headline,
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                            .then(
                                if (onHeadlineClick != null) {
                                    Modifier.defaultMinSize(minHeight = Spacing.minTouchTarget)
                                        .clickable(role = Role.Button, onClick = onHeadlineClick)
                                        .testTag("${testTagPrefix}_headline")
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
                if (statusText != null) {
                    StatusChip(
                        text = statusText,
                        tone = if (state == CoachChangeRowState.Decided) StatusTone.Good else StatusTone.Caution,
                        modifier = Modifier.testTag("${testTagPrefix}_status"),
                    )
                }
            }
            Text(text = reason, style = AppTheme.typography.bodyLarge, color = AppTheme.colors.textSecondary)
            detail?.let {
                Text(text = it, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
            actions.forEach { CoachChangeActionButton(it, enabled) }
            footer?.invoke()
        }
    }
}

@Composable
private fun CoachChangeActionButton(action: CoachChangeRowAction, enabled: Boolean) {
    val modifier = Modifier.fillMaxWidth().testTag(action.testTag).let { base ->
        action.contentDescription?.let { cd -> base.semantics { contentDescription = cd } } ?: base
    }
    if (action.primary) {
        PrimaryButton(
            action.text,
            action.onClick,
            modifier,
            enabled = enabled && !action.loading,
            loading = action.loading,
        )
    } else {
        GhostButton(
            action.text,
            action.onClick,
            modifier,
            enabled = enabled && !action.loading,
            loading = action.loading,
        )
    }
}
