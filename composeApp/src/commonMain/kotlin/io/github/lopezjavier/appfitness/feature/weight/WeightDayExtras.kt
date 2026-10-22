@file:Suppress("MatchingDeclarationName") // The file groups the day sheet's goal extras around DaySheetGoal.

package io.github.lopezjavier.appfitness.feature.weight

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
import io.github.lopezjavier.appfitness.composeapp.resources.compare_action
import io.github.lopezjavier.appfitness.composeapp.resources.compare_back_cd
import io.github.lopezjavier.appfitness.composeapp.resources.compare_delta_less
import io.github.lopezjavier.appfitness.composeapp.resources.compare_delta_more
import io.github.lopezjavier.appfitness.composeapp.resources.compare_delta_same
import io.github.lopezjavier.appfitness.composeapp.resources.compare_fat_delta
import io.github.lopezjavier.appfitness.composeapp.resources.compare_fat_label
import io.github.lopezjavier.appfitness.composeapp.resources.compare_fat_points
import io.github.lopezjavier.appfitness.composeapp.resources.compare_loading
import io.github.lopezjavier.appfitness.composeapp.resources.compare_title
import io.github.lopezjavier.appfitness.composeapp.resources.compare_values
import io.github.lopezjavier.appfitness.composeapp.resources.compare_weight_delta
import io.github.lopezjavier.appfitness.composeapp.resources.compare_weight_label
import io.github.lopezjavier.appfitness.composeapp.resources.compare_weight_value
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_back
import io.github.lopezjavier.appfitness.composeapp.resources.metric_value_percent
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_bmr
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_bone
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_fat
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_lean_mass
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_water
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ComparisonDeltaRow
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * What the day sheet needs from the goal feature: the [goal] state (comparison selection), the id of the latest
 * measurement (nothing can be compared against itself), the day's manual row (for its composition) and the event sink.
 */
internal class DaySheetGoal(
    val goal: GoalContent,
    val latestId: String?,
    val manual: BodyMeasurement?,
    val onEvent: (GoalEvent) -> Unit,
) {
    fun canCompare(id: String): Boolean = latestId != null && id != latestId
}

/** Body fat, lean mass, water, bone and BMR of a day, only the ones it has, in this order everywhere. */
@Composable
internal fun CompositionLines(composition: LatestComposition, modifier: Modifier = Modifier) {
    val fat = composition.bodyFat?.let {
        stringResource(Res.string.weight_composition_fat, decimalText(it.value))
    }
    val lean = composition.leanMass?.let {
        stringResource(Res.string.weight_composition_lean_mass, decimalText(it.value))
    }
    val water = composition.water?.let { stringResource(Res.string.weight_composition_water, decimalText(it.value)) }
    val bone = composition.bone?.let { stringResource(Res.string.weight_composition_bone, decimalText(it.value)) }
    val bmr = composition.bmr?.let { stringResource(Res.string.weight_composition_bmr, it.value.toString()) }
    val lines = listOfNotNull(
        fat?.let { "weight_day_composition_body_fat" to it },
        lean?.let { "weight_day_composition_lean_mass" to it },
        water?.let { "weight_day_composition_water" to it },
        bone?.let { "weight_day_composition_bone" to it },
        bmr?.let { "weight_day_composition_bmr" to it },
    )
    if (lines.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        lines.forEach { (tag, text) ->
            Text(
                text = text,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

/** "Comparar" on a measurement that is not the latest; the comparison replaces the sheet's body. */
@Composable
internal fun CompareButton(id: String, daySheetGoal: DaySheetGoal, modifier: Modifier = Modifier) {
    if (!daySheetGoal.canCompare(id)) return
    GhostButton(
        text = stringResource(Res.string.compare_action),
        onClick = { daySheetGoal.onEvent(GoalEvent.OpenCompare(id)) },
        modifier = modifier.fillMaxWidth().testTag("weight_compare_button_$id"),
    )
}

/** The comparison of an older measurement with the latest: read-only, the back arrow returns to the day sheet. */
@Composable
internal fun ComparisonContent(comparison: ComparisonState?, onEvent: (GoalEvent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("weight_compare_sheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppIconButton(
                contentDescription = stringResource(Res.string.compare_back_cd),
                icon = Res.drawable.ic_arrow_back,
                testTag = "weight_compare_back_button",
                onClick = { onEvent(GoalEvent.CloseCompare) },
            )
            Text(
                text = stringResource(Res.string.compare_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (comparison == null) {
            Text(
                text = stringResource(Res.string.compare_loading),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        } else {
            ComparisonRows(comparison)
        }
    }
}

@Composable
private fun ComparisonRows(state: ComparisonState) = Column {
    val weightDelta = state.comparison.weightDeltaKg
    ComparisonDeltaRow(
        label = stringResource(Res.string.compare_weight_label),
        valuesText = stringResource(
            Res.string.compare_values,
            decimalText(state.older.weightKg),
            stringResource(Res.string.compare_weight_value, decimalText(state.latest.weightKg)),
        ),
        deltaText = stringResource(Res.string.compare_weight_delta, signedTenths(weightDelta)),
        deltaDescription = deltaDescription(
            weightDelta,
            stringResource(Res.string.compare_weight_value, decimalText(abs(weightDelta))),
        ),
        modifier = Modifier.testTag("weight_compare_weight_delta"),
    )
    val fatDelta = state.comparison.bodyFatDeltaPct
    val olderFat = state.older.bodyFatPct
    val latestFat = state.latest.bodyFatPct
    if (fatDelta != null && olderFat != null && latestFat != null) {
        ComparisonDeltaRow(
            label = stringResource(Res.string.compare_fat_label),
            valuesText = stringResource(
                Res.string.compare_values,
                decimalText(olderFat),
                stringResource(Res.string.metric_value_percent, decimalText(latestFat)),
            ),
            deltaText = stringResource(Res.string.compare_fat_delta, signedTenths(fatDelta)),
            deltaDescription = deltaDescription(
                fatDelta,
                stringResource(Res.string.compare_fat_points, decimalText(abs(fatDelta))),
            ),
            modifier = Modifier.testTag("weight_compare_fat_delta"),
        )
    }
}

@Composable
private fun deltaDescription(delta: Double, amount: String): String = when {
    isZeroTenths(delta) -> stringResource(Res.string.compare_delta_same)
    delta < 0 -> stringResource(Res.string.compare_delta_less, amount)
    else -> stringResource(Res.string.compare_delta_more, amount)
}
