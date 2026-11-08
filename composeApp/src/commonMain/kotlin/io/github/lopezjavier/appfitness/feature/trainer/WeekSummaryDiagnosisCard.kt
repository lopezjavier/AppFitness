package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_header
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_help_cd
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.NutritionDiagnosis
import org.jetbrains.compose.resources.stringResource

/**
 * "¿Por qué no avanza tu peso?" (US-113, US-114): one read-only card with the first cause the nutrition diagnosis
 * found and one action. [hasProposal]: the week carries a calorie proposal for the plateau and no-cause actions to
 * point at.
 */
// SpreadOperator: stringResource takes varargs and the argument lists hold at most three short strings.
@Suppress("SpreadOperator")
@Composable
internal fun DiagnosisCard(
    diagnosis: NutritionDiagnosis,
    hasProposal: Boolean,
    testTagPrefix: String = "week_summary",
) {
    val line = WeekSummaryText.diagnosisLine(diagnosis, hasProposal)
    val header = stringResource(Res.string.week_summary_diagnosis_header)
    val cause = stringResource(line.cause, *line.causeArgs.toTypedArray())
    val causeText = line.note?.let { "$cause ${stringResource(it)}" } ?: cause
    AppCard(Modifier.fillMaxWidth().testTag("${testTagPrefix}_section_diagnosis")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ListSectionHeader(header, Modifier.weight(1f))
                HelpButton(
                    contentDescription = stringResource(Res.string.week_summary_diagnosis_help_cd),
                    title = header,
                    body = stringResource(Res.string.week_summary_diagnosis_help_body),
                    testTag = "${testTagPrefix}_diagnosis_help",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = causeText,
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.testTag("${testTagPrefix}_diagnosis_cause"),
                )
                Text(
                    text = stringResource(line.action, *line.actionArgs.toTypedArray()),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${testTagPrefix}_diagnosis_action"),
                )
            }
        }
    }
}
