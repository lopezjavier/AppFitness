package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.energy_adjust_value
import io.github.lopezjavier.appfitness.composeapp.resources.intake_maintenance_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_many
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_one
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal fun IntakeRowKind.intakeLabel(): StringResource = when (this) {
    IntakeRowKind.TrainingYears -> Res.string.intake_years_label
    IntakeRowKind.Maintenance -> Res.string.intake_maintenance_label
}

/** Test tag of the row on the screen [screen] ("before_start" or "your_data"). */
internal fun IntakeRowKind.rowTag(screen: String): String = when (this) {
    IntakeRowKind.TrainingYears -> "${screen}_row_training_years"
    IntakeRowKind.Maintenance -> "${screen}_row_maintenance"
}

/** The editor a row opens: the sheet of the screen. */
internal fun IntakeRowKind.fixTarget(): PlanFixTarget = when (this) {
    IntakeRowKind.TrainingYears -> PlanFixTarget.TrainingYears
    IntakeRowKind.Maintenance -> PlanFixTarget.Maintenance
}

/** The value of an intake row, or null when the user has not said it ("Sin indicar"). */
@Composable
internal fun IntakeRowKind.valueText(summary: IntakeSummary): String? = when (this) {
    IntakeRowKind.TrainingYears -> summary.trainingYears?.let { yearsText(it) }

    IntakeRowKind.Maintenance -> summary.maintenanceKcal?.let {
        stringResource(Res.string.energy_adjust_value, AppNumberFormat.integer(it))
    }
}

/** "1 año", "0,5 años", "4 años": training time in years. */
@Composable
internal fun yearsText(years: Double): String {
    val number = AppNumberFormat.decimal(years, maxDecimals = 1, grouping = false)
    return stringResource(if (years == 1.0) Res.string.intake_years_one else Res.string.intake_years_many, number)
}
