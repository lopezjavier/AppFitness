package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_goal_metrics
import io.github.lopezjavier.appfitness.designsystem.GoalCard
import io.github.lopezjavier.appfitness.designsystem.MetricInterpretationRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import org.jetbrains.compose.resources.stringResource

/** Catalog section of the goal card, the metric interpretation row and the comparison delta row. */
internal fun LazyListScope.goalMetricItems() {
    item { GoalMetricSection() }
}

@Composable
private fun GoalMetricSection() {
    CatalogSection(stringResource(Res.string.catalog_section_goal_metrics), "catalog_section_goal_metrics") {
        Column(Modifier.testTag("catalog_goal_metrics"), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            GoalCard(
                title = "Objetivo",
                targetText = "70,0 kg",
                distanceText = "faltan 4,2 kg",
                sinceFirstText = "Desde tu primer peso: -2,6 kg",
                emptyHint = "Fija un peso objetivo para ver cuánto te falta.",
                setLabel = "Fijar objetivo",
                changeLabel = "Cambiar objetivo",
                onSet = {},
                onChange = {},
                testTagPrefix = "catalog_goal",
            )
            MetricInterpretationRow(
                label = "IMC",
                valueText = "23,4",
                statusText = "Normal",
                tone = StatusTone.Good,
                explanation = "Tu peso es adecuado para tu estatura (IMC entre 18,5 y 24,9).",
            )
        }
    }
}
