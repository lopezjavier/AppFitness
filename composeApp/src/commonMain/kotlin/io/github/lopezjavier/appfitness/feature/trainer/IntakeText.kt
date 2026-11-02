package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.intake_area_ankle
import io.github.lopezjavier.appfitness.composeapp.resources.intake_area_elbow
import io.github.lopezjavier.appfitness.composeapp.resources.intake_area_hip
import io.github.lopezjavier.appfitness.composeapp.resources.intake_area_knee
import io.github.lopezjavier.appfitness.composeapp.resources.intake_area_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_area_lower_back
import io.github.lopezjavier.appfitness.composeapp.resources.intake_area_other
import io.github.lopezjavier.appfitness.composeapp.resources.intake_area_shoulder
import io.github.lopezjavier.appfitness.composeapp.resources.intake_area_wrist
import io.github.lopezjavier.appfitness.composeapp.resources.intake_situation_continuous
import io.github.lopezjavier.appfitness.composeapp.resources.intake_situation_first_time
import io.github.lopezjavier.appfitness.composeapp.resources.intake_situation_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_situation_returning_break
import io.github.lopezjavier.appfitness.composeapp.resources.intake_situation_returning_injury
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_many
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_one
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.Situation
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Spanish label of the situation the user describes ("Vuelvo tras una pausa"). */
internal fun Situation.intakeLabel(): StringResource = when (this) {
    Situation.FirstTime -> Res.string.intake_situation_first_time
    Situation.Continuous -> Res.string.intake_situation_continuous
    Situation.ReturningBreak -> Res.string.intake_situation_returning_break
    Situation.ReturningInjury -> Res.string.intake_situation_returning_injury
}

/** Spanish label of the area with a discomfort ("Zona lumbar"). */
internal fun InjuredArea.intakeLabel(): StringResource = when (this) {
    InjuredArea.Shoulder -> Res.string.intake_area_shoulder
    InjuredArea.Elbow -> Res.string.intake_area_elbow
    InjuredArea.Wrist -> Res.string.intake_area_wrist
    InjuredArea.LowerBack -> Res.string.intake_area_lower_back
    InjuredArea.Hip -> Res.string.intake_area_hip
    InjuredArea.Knee -> Res.string.intake_area_knee
    InjuredArea.Ankle -> Res.string.intake_area_ankle
    InjuredArea.Other -> Res.string.intake_area_other
}

internal fun IntakeRowKind.intakeLabel(): StringResource = when (this) {
    IntakeRowKind.Situation -> Res.string.intake_situation_label
    IntakeRowKind.TrainingAge -> Res.string.intake_years_label
    IntakeRowKind.InjuredArea -> Res.string.intake_area_label
}

internal fun IntakeRowKind.rowTag(): String = when (this) {
    IntakeRowKind.Situation -> "before_start_situation_row"
    IntakeRowKind.TrainingAge -> "before_start_training_age_row"
    IntakeRowKind.InjuredArea -> "before_start_injured_area_row"
}

/** The value of an intake row, or null when the user has not said it ("Sin indicar"). */
@Composable
internal fun IntakeRowKind.valueText(summary: IntakeSummary): String? = when (this) {
    IntakeRowKind.Situation -> summary.situation?.let { stringResource(it.intakeLabel()) }
    IntakeRowKind.TrainingAge -> summary.trainingYears?.let { yearsText(it) }
    IntakeRowKind.InjuredArea -> summary.injuredArea?.let { stringResource(it.intakeLabel()) }
}

/** "1 año", "0,5 años", "4 años": training time in years. */
@Composable
internal fun yearsText(years: Double): String {
    val number = AppNumberFormat.decimal(years, maxDecimals = 1, grouping = false)
    return stringResource(if (years == 1.0) Res.string.intake_years_one else Res.string.intake_years_many, number)
}
