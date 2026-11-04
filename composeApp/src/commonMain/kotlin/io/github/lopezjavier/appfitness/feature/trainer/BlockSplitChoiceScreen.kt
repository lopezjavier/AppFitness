package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_continue
import io.github.lopezjavier.appfitness.composeapp.resources.block_split_name_recommended
import io.github.lopezjavier.appfitness.composeapp.resources.block_split_recommended
import io.github.lopezjavier.appfitness.composeapp.resources.block_split_title
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.coach.builder.Split
import org.jetbrains.compose.resources.stringResource

/**
 * "Tipo de rutina" (US-72, US-93): one [SelectableCard] per split of the user's days, the recommended one first and
 * preselected, with the word "Recomendado" and its reason as text (R-41). No AI call and no wait.
 */
@Composable
internal fun BlockSplitChoiceScreen(
    state: BlockBuilderUiState.Splits,
    onEvent: (BlockBuilderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.block_split_title),
        modifier = modifier,
        onBack = { onEvent(BlockBuilderEvent.Back) },
        footer = {
            PrimaryButton(
                text = stringResource(Res.string.block_builder_continue),
                onClick = { onEvent(BlockBuilderEvent.Continue) },
                modifier = Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("block_split_continue"),
            )
        },
        testTagPrefix = "block_split",
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenMargin)
                .selectableGroup()
                .testTag("block_split_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        ) {
            state.splits.forEachIndexed { index, split ->
                SplitCard(split, selected = index == state.selectedIndex, index = index, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun SplitCard(split: Split, selected: Boolean, index: Int, onEvent: (BlockBuilderEvent) -> Unit) {
    val name = split.types.joinToString(" / ") { it.displayName }
    val title = if (split.recommended) {
        stringResource(
            Res.string.block_split_name_recommended,
            name,
            stringResource(Res.string.block_split_recommended),
        )
    } else {
        name
    }
    SelectableCard(
        title = title,
        selected = selected,
        onClick = { onEvent(BlockBuilderEvent.PickSplit(index)) },
        description = split.reason?.let { splitReasonText(it) }.orEmpty(),
        modifier = Modifier.testTag("block_split_card_$index"),
    )
}
