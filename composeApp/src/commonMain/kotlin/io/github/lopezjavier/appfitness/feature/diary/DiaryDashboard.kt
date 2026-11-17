package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_goals_prompt_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.diary_goals_prompt_title
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flag
import io.github.lopezjavier.appfitness.composeapp.resources.macro_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.macro_carbs_short
import io.github.lopezjavier.appfitness.composeapp.resources.macro_fat
import io.github.lopezjavier.appfitness.composeapp.resources.macro_fat_short
import io.github.lopezjavier.appfitness.composeapp.resources.macro_protein
import io.github.lopezjavier.appfitness.composeapp.resources.macro_protein_short
import io.github.lopezjavier.appfitness.composeapp.resources.px_diary_goals_prompt_cta
import io.github.lopezjavier.appfitness.composeapp.resources.px_diary_split_cd
import io.github.lopezjavier.appfitness.composeapp.resources.px_diary_split_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DiaryDot
import io.github.lopezjavier.appfitness.designsystem.DiarySplitBar
import io.github.lopezjavier.appfitness.designsystem.DiarySplitPart
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroProgressBar
import io.github.lopezjavier.appfitness.designsystem.MacroProgressBarDefaults
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
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
            content.meals,
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
    meals: List<MealSectionUi>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth(), glow = AppTheme.colors.neonLime) {
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                if (stacked) {
                    Row(
                        Modifier.fillMaxWidth().testTag("diary_hero_compact"),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProgressRing(
                            totals.kcal,
                            goals.targetKcal,
                            Modifier.testTag("diary_ring"),
                            pastDay,
                            compact = true,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            MacroBar(Macro.Protein, labels[0], totals.proteinG, goals.proteinG)
                            MacroBar(Macro.Carbs, labels[1], totals.carbsG, goals.carbsG)
                            MacroBar(Macro.Fat, labels[2], totals.fatG, goals.fatG)
                        }
                    }
                } else {
                    FullHero(goals, totals, pastDay)
                }
                MealSplit(meals)
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

/**
 * How the day's calories split by meal: a slim segmented bar always visible and, on demand, one legend line per meal
 * with its calories. Collapsed by default so the hero stays compact; the title row toggles the detail. Hidden until
 * something is logged.
 */
@Composable
private fun MealSplit(meals: List<MealSectionUi>) {
    if (meals.none { it.totals.kcal > 0.0 }) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(Res.string.px_diary_split_cd)
    val state = stringResource(if (expanded) Res.string.diary_section_expanded else Res.string.diary_section_collapsed)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = state }
                .testTag("diary_meal_split_toggle"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.px_diary_split_title),
                style = AppTheme.typography.titleSection,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(Res.drawable.ic_expand_more),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.lg).rotate(if (expanded) EXPANDED_CHEVRON_ROTATION else 0f),
            )
        }
        DiarySplitBar(
            meals.mapIndexed { index, meal -> DiarySplitPart(meal.totals.kcal.toFloat(), mealAccent(index)) },
            Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        )
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                meals.forEachIndexed { index, meal ->
                    if (meal.totals.kcal > 0.0) MealSplitLegend(meal, mealAccent(index))
                }
            }
        }
    }
}

@Composable
private fun MealSplitLegend(meal: MealSectionUi, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DiaryDot(color)
        Text(
            text = meal.slot.label,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f).padding(start = Spacing.sm),
            maxLines = 1,
        )
        Text(
            text = stringResource(Res.string.diary_meal_kcal, wholeHalfUp(meal.totals.kcal)),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            maxLines = 1,
        )
    }
}

/** "Configura tu objetivo": a hero card with a lit icon badge, the reason and a clear call to action. */
@Composable
private fun GoalsPrompt(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(
        modifier.fillMaxWidth().testTag("diary_goals_prompt"),
        glow = AppTheme.colors.neonLime,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            IconBadge(
                icon = Res.drawable.ic_flag,
                tint = AppTheme.colors.neonLime,
                size = IconBadgeDefaults.LargeSize,
                iconSize = IconBadgeDefaults.LargeIconSize,
            )
            Text(
                text = stringResource(Res.string.diary_goals_prompt_title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.diary_goals_prompt_supporting),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            PrimaryButton(
                text = stringResource(Res.string.px_diary_goals_prompt_cta),
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().testTag("diary_goals_prompt_cta"),
            )
        }
    }
}
