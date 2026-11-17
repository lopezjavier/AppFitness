package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_split_cd
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_split_sets
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_split_title
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_split_value
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DonutChart
import io.github.lopezjavier.appfitness.designsystem.DonutSlice
import io.github.lopezjavier.appfitness.designsystem.MuscleSplitBar
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.usecase.MuscleShare
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100

/** "Reparto muscular" of a finished workout: one bar per primary muscle group, most sets first. */
@Composable
internal fun MuscleSplitSection(split: List<MuscleShare>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().testTag("session_detail_split"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        Text(
            text = stringResource(Res.string.session_detail_split_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SplitDonut(split)
                split.forEach { share ->
                    val name = stringResource(share.group.label)
                    val percent = (share.fraction * PERCENT).roundToInt()
                    val sets = pluralStringResource(Res.plurals.session_detail_split_sets, share.sets, share.sets)
                    MuscleSplitBar(
                        label = name,
                        valueText = stringResource(Res.string.session_detail_split_value, sets, percent),
                        fraction = share.fraction.toFloat(),
                        description = stringResource(Res.string.session_detail_split_cd, name, sets, percent),
                        modifier = Modifier.testTag("session_detail_split_${share.group.name}"),
                    )
                }
            }
        }
    }
}

/** The split as a donut with the total sets in the centre; decorative, the bars below carry the numbers. */
@Composable
private fun SplitDonut(split: List<MuscleShare>) {
    val colors = AppTheme.colors
    val palette = listOf(
        colors.athleticCyan,
        colors.neonLime,
        colors.energyAmber,
        colors.macroFat,
        colors.macroProtein,
        colors.electricGreen,
    )
    val total = split.sumOf { it.sets }
    Box(Modifier.fillMaxWidth().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        DonutChart(
            slices = split.mapIndexed { index, share ->
                DonutSlice(share.sets.toFloat(), palette[index % palette.size])
            },
        ) {
            Text(
                text = AppNumberFormat.integer(total),
                style = AppTheme.typography.headlineMetric,
                color = colors.textPrimary,
            )
        }
    }
}
