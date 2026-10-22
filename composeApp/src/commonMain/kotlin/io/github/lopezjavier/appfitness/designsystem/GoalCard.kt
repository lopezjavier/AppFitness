package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit

/**
 * The weight goal: [title] and, once a target exists, a compact edit icon in the header ([changeLabel] is its spoken
 * description); the [targetText] with the [distanceText] and an optional [sinceFirstText] under it. Without a target
 * ([targetText] null) it shows [emptyHint] and the [setLabel] button. Tags: `<prefix>_card`, `_set_button`,
 * `_change_button`, `_distance`, `_since_first`.
 */
// Every slot of the card is a plain value or event; grouping them would only add a holder type.
@Suppress("LongParameterList")
@Composable
fun GoalCard(
    title: String,
    targetText: String?,
    distanceText: String?,
    sinceFirstText: String?,
    emptyHint: String,
    setLabel: String,
    changeLabel: String,
    onSet: () -> Unit,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "goal",
) {
    AppCard(modifier.fillMaxWidth().testTag("${testTagPrefix}_card")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (targetText != null) {
                AppIconButton(
                    contentDescription = changeLabel,
                    icon = Res.drawable.ic_edit,
                    testTag = "${testTagPrefix}_change_button",
                    onClick = onChange,
                    tint = AppTheme.colors.textSecondary,
                )
            }
        }
        if (targetText == null) {
            EmptyGoal(emptyHint, setLabel, onSet, testTagPrefix)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = targetText, style = AppTheme.typography.headlineMetric, color = AppTheme.colors.textPrimary)
                distanceText?.let {
                    Text(
                        text = it,
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("${testTagPrefix}_distance"),
                    )
                }
                sinceFirstText?.let {
                    Text(
                        text = it,
                        style = AppTheme.typography.bodyMedium.copy(fontFeatureSettings = NUMERIC_FONT_FEATURES),
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("${testTagPrefix}_since_first"),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGoal(hint: String, setLabel: String, onSet: () -> Unit, prefix: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(text = hint, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        SecondaryButton(
            text = setLabel,
            onClick = onSet,
            modifier = Modifier.fillMaxWidth().testTag("${prefix}_set_button"),
        )
    }
}

@Preview
@Composable
private fun GoalCardPreview() {
    AppTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            GoalCard(
                title = "Objetivo",
                targetText = "70,0 kg",
                distanceText = "faltan 4,2 kg",
                sinceFirstText = "Desde tu primer peso: -2,6 kg",
                emptyHint = "",
                setLabel = "Fijar objetivo",
                changeLabel = "Cambiar objetivo",
                onSet = {},
                onChange = {},
            )
            GoalCard("Objetivo", null, null, null, "Fija un peso objetivo.", "Fijar objetivo", "Cambiar objetivo", {
            }, {})
        }
    }
}
