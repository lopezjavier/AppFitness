package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_target_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.macro_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.macro_fat
import io.github.lopezjavier.appfitness.composeapp.resources.macro_protein
import io.github.lopezjavier.appfitness.composeapp.resources.px_body_percent
import io.github.lopezjavier.appfitness.composeapp.resources.px_body_split_value
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Calories and macros of a day or a period average; grams keep decimals, the row shows whole numbers. */
@Immutable
data class HistoryTotals(val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double)

/** Daily targets the totals are measured against; a macro target of 0 shows no bar. */
@Immutable
data class HistoryTargets(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)

/**
 * One history figure: [title] (and an optional [subtitle]), the calories (against the target when there is one) and
 * the three macros as progress bars, or as plain "P 142 g" values without [targets]. Without [totals] it shows
 * [emptyText] ("Sin registros", tag `diary_history_empty`). With [onClick] the whole card is one tappable target (tag
 * `diary_history_row_open`), otherwise the card is tagged `diary_history_row`.
 */
@Composable
fun DiaryHistoryRow(
    title: String,
    totals: HistoryTotals?,
    targets: HistoryTargets?,
    emptyText: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    showSplit: Boolean = false,
    chart: (@Composable () -> Unit)? = null,
) {
    val tag = if (onClick != null) "diary_history_row_open" else "diary_history_row"
    val cardModifier = if (onClick != null) {
        modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    AppCard(cardModifier.testTag(tag), glow = AppTheme.colors.neonLime) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(title, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            subtitle?.let { Text(it, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary) }
            if (totals == null) {
                Text(
                    text = emptyText,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("diary_history_empty"),
                )
            } else {
                HistoryFigures(totals, targets, showSplit, chart)
            }
        }
    }
}

@Composable
private fun HistoryFigures(
    totals: HistoryTotals,
    targets: HistoryTargets?,
    showSplit: Boolean,
    chart: (@Composable () -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        KcalLine(totals, targets)
        chart?.invoke()
        if (showSplit) MacroSplit(totals)
        MacroLines(totals, targets)
    }
}

@Composable
private fun KcalLine(totals: HistoryTotals, targets: HistoryTargets?) {
    val kcal = AppNumberFormat.integer(totals.kcal.roundToInt())
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(Res.string.diary_meal_kcal, kcal),
                style = AppTheme.typography.displayHero,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("diary_history_kcal"),
            )
            targets?.let {
                Text(
                    text = stringResource(Res.string.diary_history_target_kcal, AppNumberFormat.integer(it.kcal)),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
        if (targets != null && targets.kcal > 0) {
            val fraction = (totals.kcal / targets.kcal).toFloat()
            val over = fraction > 1f
            StatRing(
                fraction = fraction.coerceIn(0f, 1f),
                color = if (over) AppTheme.colors.energyAmber else AppTheme.colors.neonLime,
            ) {
                Text(
                    text = stringResource(
                        Res.string.px_body_percent,
                        AppNumberFormat.integer((fraction * 100).roundToInt()),
                    ),
                    style = AppTheme.typography.labelNumeric,
                    color = AppTheme.colors.textPrimary,
                )
            }
        }
    }
}

private const val KCAL_PER_G_PROTEIN_CARBS = 4.0
private const val KCAL_PER_G_FAT = 9.0

/** The day's calories split by macro (4/4/9 kcal per gram) as a donut with its legend. */
@Composable
private fun MacroSplit(totals: HistoryTotals) {
    val colors = AppTheme.colors
    val parts = listOf(
        Triple(
            stringResource(Res.string.macro_protein),
            totals.proteinG to KCAL_PER_G_PROTEIN_CARBS,
            colors.macroProtein,
        ),
        Triple(stringResource(Res.string.macro_carbs), totals.carbsG to KCAL_PER_G_PROTEIN_CARBS, colors.macroCarbs),
        Triple(stringResource(Res.string.macro_fat), totals.fatG to KCAL_PER_G_FAT, colors.macroFat),
    )
    val total = parts.sumOf { it.second.first * it.second.second }
    if (total <= 0.0) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DonutChart(parts.map { DonutSlice((it.second.first * it.second.second).toFloat(), it.third) }) {
            Text(
                text = AppNumberFormat.integer(total.roundToInt()),
                style = AppTheme.typography.labelNumeric,
                color = AppTheme.colors.textPrimary,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            parts.forEach { (label, gramsAndFactor, color) ->
                val percent = (gramsAndFactor.first * gramsAndFactor.second / total * 100).roundToInt()
                LegendRow(
                    label = label,
                    value = stringResource(
                        Res.string.px_body_split_value,
                        AppNumberFormat.integer(gramsAndFactor.first.roundToInt()),
                        AppNumberFormat.integer(percent),
                    ),
                    color = color,
                )
            }
        }
    }
}

@Composable
private fun MacroLines(totals: HistoryTotals, targets: HistoryTargets?) {
    if (targets == null) {
        MacroLine(
            proteinG = AppNumberFormat.integer(totals.proteinG.roundToInt()),
            carbsG = AppNumberFormat.integer(totals.carbsG.roundToInt()),
            fatG = AppNumberFormat.integer(totals.fatG.roundToInt()),
            modifier = Modifier.fillMaxWidth(),
            style = AppTheme.typography.bodyMedium,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            MacroBar(Macro.Protein, stringResource(Res.string.macro_protein), totals.proteinG, targets.proteinG)
            MacroBar(Macro.Carbs, stringResource(Res.string.macro_carbs), totals.carbsG, targets.carbsG)
            MacroBar(Macro.Fat, stringResource(Res.string.macro_fat), totals.fatG, targets.fatG)
        }
    }
}

@Composable
private fun MacroBar(macro: Macro, label: String, grams: Double, targetG: Int) {
    if (targetG <= 0) return
    MacroProgressBar(label, macro, grams, targetG, Modifier.testTag("diary_history_macro_${macro.name.lowercase()}"))
}
