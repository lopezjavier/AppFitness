package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_activity
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_cardio_edit_label
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PlanActivity
import org.jetbrains.compose.resources.stringResource

/** The weekly cardio and steps line of the plan: a 48 dp button that opens the cardio sheet. */
@Composable
internal fun PlanCardioRow(activity: PlanActivity, onEditCardio: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .testTag("trainer_plan_cardio_row")
            .clickable(
                onClickLabel = stringResource(Res.string.trainer_plan_cardio_edit_label),
                role = Role.Button,
                onClick = onEditCardio,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(
                Res.string.trainer_plan_activity,
                AppNumberFormat.integer(activity.cardioMinWeek),
                AppNumberFormat.integer(activity.stepsDay),
            ),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.testTag("trainer_plan_activity"),
        )
    }
}
