@file:Suppress("MatchingDeclarationName") // The file groups the day sheet's goal extras around DaySheetGoal.

package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_bmr
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_bone
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_fat
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_lean_mass
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_water
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import org.jetbrains.compose.resources.stringResource

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
