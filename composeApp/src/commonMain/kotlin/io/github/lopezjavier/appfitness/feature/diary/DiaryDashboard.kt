package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_goals_prompt_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.diary_goals_prompt_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flag
import io.github.lopezjavier.appfitness.composeapp.resources.macro_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.macro_carbs_short
import io.github.lopezjavier.appfitness.composeapp.resources.macro_fat
import io.github.lopezjavier.appfitness.composeapp.resources.macro_fat_short
import io.github.lopezjavier.appfitness.composeapp.resources.macro_protein
import io.github.lopezjavier.appfitness.composeapp.resources.macro_protein_short
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroProgressBar
import io.github.lopezjavier.appfitness.designsystem.MacroProgressBarDefaults
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.ProgressRing
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.bold
import io.github.lopezjavier.appfitness.designsystem.compactRingColumnWidth
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Font scale from which the hero falls back to the full ring over three bars, so no figure clips (R-68). */
private const val FULL_HERO_FONT_SCALE = 1.15f
private const val DOT_GAPS = 3

/**
 * The day's progress card (calorie ring and three macro bars), or the "set your goal" prompt without goals. With
 * [compactHero] (a phone width) and a font scale under 1.15 the ring is 132 dp with the macros stacked beside it, so
 * the first meal card stays above the fold; otherwise it is the full ring over the bars (R-68).
 */
@Composable
internal fun DiaryDashboard(
    content: DiaryContent,
    onEvent: (DiaryEvent) -> Unit,
    modifier: Modifier = Modifier,
    compactHero: Boolean = false,
) {
    when (val goals = content.goals) {
        is DiaryGoalsState.Set -> ProgressCard(
            goals.goals,
            content.dayTotals,
            !content.isToday,
            compactHero && LocalDensity.current.fontScale < FULL_HERO_FONT_SCALE,
            modifier,
        )

        DiaryGoalsState.NotSet -> GoalsPrompt({ onEvent(DiaryEvent.OpenGoals) }, modifier)
    }
}

@Composable
private fun ProgressCard(
    goals: DiaryGoals,
    totals: MealTotals,
    pastDay: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labels = listOf(
                stringResource(Res.string.macro_protein_short),
                stringResource(Res.string.macro_carbs_short),
                stringResource(Res.string.macro_fat_short),
            )
            // V-15: the stacked layout is used only when every short label and figure fits beside the ring.
            // V-15b: measured against the real ring column (the 132 dp ring or the wider status pill).
            val ringColumn = compactRingColumnWidth(totals.kcal, goals.targetKcal, pastDay)
            val stacked = compact && compactMacrosFit(labels, goals, totals, maxWidth - ringColumn - Spacing.lg)
            if (stacked) {
                Row(
                    Modifier.fillMaxWidth().testTag("diary_hero_compact"),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProgressRing(totals.kcal, goals.targetKcal, Modifier.testTag("diary_ring"), pastDay, compact = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        MacroBar(Macro.Protein, labels[0], totals.proteinG, goals.proteinG)
                        MacroBar(Macro.Carbs, labels[1], totals.carbsG, goals.carbsG)
                        MacroBar(Macro.Fat, labels[2], totals.fatG, goals.fatG)
                    }
                }
            } else {
                FullHero(goals, totals, pastDay)
            }
        }
    }
}

@Composable
private fun FullHero(goals: DiaryGoals, totals: MealTotals, pastDay: Boolean) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        ProgressRing(totals.kcal, goals.targetKcal, Modifier.testTag("diary_ring"), pastDay)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            MacroBar(Macro.Protein, stringResource(Res.string.macro_protein), totals.proteinG, goals.proteinG)
            MacroBar(Macro.Carbs, stringResource(Res.string.macro_carbs), totals.carbsG, goals.carbsG)
            MacroBar(Macro.Fat, stringResource(Res.string.macro_fat), totals.fatG, goals.fatG)
        }
    }
}

/** Whether the widest macro row (dot, short label, "consumed / target") fits in [available] without wrapping. */
@Composable
private fun compactMacrosFit(labels: List<String>, goals: DiaryGoals, totals: MealTotals, available: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val typography = AppTheme.typography
    val density = LocalDensity.current
    val labelStyle = MacroProgressBarDefaults.labelStyle(typography).bold()
    val valueStyle = MacroProgressBarDefaults.valueStyle(typography)
    val rows = listOf(
        labels[0] to MacroProgressBarDefaults.text(totals.proteinG, goals.proteinG),
        labels[1] to MacroProgressBarDefaults.text(totals.carbsG, goals.carbsG),
        labels[2] to MacroProgressBarDefaults.text(totals.fatG, goals.fatG),
    )
    val widest = rows.maxOf { (label, text) ->
        measurer.measure(label.uppercase(), labelStyle).size.width +
            measurer.measure(text.full, valueStyle).size.width
    }
    val fixed = with(density) { (MacroProgressBarDefaults.DotSize + Spacing.sm * DOT_GAPS).roundToPx() }
    // A small safety margin keeps a label that only just fits from breaking on a device (R-76).
    val margin = with(density) { Spacing.md.roundToPx() }
    return widest + fixed + margin <= with(density) { available.roundToPx() }
}

@Composable
private fun MacroBar(macro: Macro, label: String, consumedG: Double, targetG: Int) {
    // A zero gram target (a 0% split) has no progress to show.
    if (targetG <= 0) return
    MacroProgressBar(label, macro, consumedG, targetG, Modifier.testTag("diary_macro_${macro.name.lowercase()}"))
}

/** "Configura tu objetivo": a navigation row with the target icon and a chevron (R-73, C-42); no "?" help. */
@Composable
private fun GoalsPrompt(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        NavigationRow(
            label = stringResource(Res.string.diary_goals_prompt_title),
            onClick = onClick,
            modifier = Modifier.testTag("diary_goals_prompt"),
            supporting = stringResource(Res.string.diary_goals_prompt_supporting),
            leading = {
                Icon(
                    painter = painterResource(Res.drawable.ic_flag),
                    contentDescription = null,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(Spacing.xl),
                )
            },
        )
    }
}
