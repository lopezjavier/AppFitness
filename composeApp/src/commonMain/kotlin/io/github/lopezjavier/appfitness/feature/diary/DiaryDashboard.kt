package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_goals_prompt_hint
import io.github.lopezjavier.appfitness.composeapp.resources.diary_goals_prompt_title
import io.github.lopezjavier.appfitness.composeapp.resources.macro_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.macro_fat
import io.github.lopezjavier.appfitness.composeapp.resources.macro_protein
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroProgressBar
import io.github.lopezjavier.appfitness.designsystem.ProgressRing
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import org.jetbrains.compose.resources.stringResource

/** The day's progress card (calorie ring and three macro bars), or the "set your goal" prompt without goals. */
@Composable
internal fun DiaryDashboard(content: DiaryContent, onEvent: (DiaryEvent) -> Unit, modifier: Modifier = Modifier) {
    when (val goals = content.goals) {
        is DiaryGoalsState.Set -> ProgressCard(goals.goals, content.dayTotals, modifier)
        DiaryGoalsState.NotSet -> GoalsPrompt({ onEvent(DiaryEvent.OpenGoals) }, modifier)
    }
}

@Composable
private fun ProgressCard(goals: DiaryGoals, totals: MealTotals, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            ProgressRing(totals.kcal, goals.targetKcal, Modifier.testTag("diary_ring"))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                MacroBar(Macro.Protein, stringResource(Res.string.macro_protein), totals.proteinG, goals.proteinG)
                MacroBar(Macro.Carbs, stringResource(Res.string.macro_carbs), totals.carbsG, goals.carbsG)
                MacroBar(Macro.Fat, stringResource(Res.string.macro_fat), totals.fatG, goals.fatG)
            }
        }
    }
}

@Composable
private fun MacroBar(macro: Macro, label: String, consumedG: Double, targetG: Int) {
    // A zero gram target (a 0% split) has no progress to show.
    if (targetG <= 0) return
    MacroProgressBar(label, macro, consumedG, targetG, Modifier.testTag("diary_macro_${macro.name.lowercase()}"))
}

@Composable
private fun GoalsPrompt(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("diary_goals_prompt"),
    ) {
        Text(
            text = stringResource(Res.string.diary_goals_prompt_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.diary_goals_prompt_hint),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
    }
}
