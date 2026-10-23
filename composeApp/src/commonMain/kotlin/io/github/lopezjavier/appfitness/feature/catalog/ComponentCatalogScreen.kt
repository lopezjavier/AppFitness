package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_button_ghost
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_button_primary
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_button_secondary
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_card_body
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_card_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_buttons
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_cards
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_chips
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_segmented
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_tabs
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_state_disabled
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_state_enabled
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_state_pressed
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_history
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_how_to
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_summary
import io.github.lopezjavier.appfitness.composeapp.resources.goal_strategy_deficit
import io.github.lopezjavier.appfitness.composeapp.resources.goal_strategy_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.goal_strategy_surplus
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_1m
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_1m_desc
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_1w
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_1w_desc
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_1y
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_1y_desc
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_3m
import io.github.lopezjavier.appfitness.composeapp.resources.timeframe_3m_desc
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTabRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private const val DEFAULT_TIMEFRAME_INDEX = 1
private const val DEFAULT_STRATEGY_INDEX = 1

/**
 * Debug component catalog (local flavor only, opened from Profile). Each design-system component
 * gets a section here; later tasks add theirs through [CatalogSection].
 */
@Composable
fun ComponentCatalogScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.testTag("catalog_screen"),
        contentPadding =
        PaddingValues(
            start = Spacing.screenMargin,
            end = Spacing.screenMargin,
            top = Spacing.lg,
            bottom = Spacing.blockGap,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        item { CardsSection() }
        item { ButtonsSection() }
        item { ChipsSection() }
        item { SegmentedSection() }
        item { TabsSection() }
        dataVizItems()
        workoutInputItems()
        pickerItems()
        exerciseMediaItems()
        stateMessageItems()
        foodItems()
        diaryHistoryItems()
        circumferenceItems()
        goalMetricItems()
        shellItems()
        themeItems()
    }
}

@Composable
internal fun CatalogSection(title: String, testTag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        Text(
            text = title,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() }.testTag(testTag),
        )
        content()
    }
}

@Composable
private fun CardsSection() {
    CatalogSection(stringResource(Res.string.catalog_section_cards), "catalog_section_cards") {
        AppCard(Modifier.fillMaxWidth().testTag("catalog_card")) {
            Text(
                text = stringResource(Res.string.catalog_card_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.catalog_card_body),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ButtonsSection() {
    val enabled = stringResource(Res.string.catalog_state_enabled)
    val disabled = stringResource(Res.string.catalog_state_disabled)
    val pressed = stringResource(Res.string.catalog_state_pressed)
    // The pressed samples are forced by emitting a Press on their own interaction sources.
    val pressedSources = remember { List(BUTTON_VARIANTS) { MutableInteractionSource() } }
    LaunchedEffect(pressedSources) {
        pressedSources.forEach { it.emit(PressInteraction.Press(Offset.Zero)) }
    }
    val full = Modifier.fillMaxWidth()
    CatalogSection(stringResource(Res.string.catalog_section_buttons), "catalog_section_buttons") {
        VariantCaption(stringResource(Res.string.catalog_button_primary))
        PrimaryButton(enabled, {}, full.testTag("catalog_button_primary_enabled"))
        PrimaryButton(disabled, {}, full.testTag("catalog_button_primary_disabled"), enabled = false)
        PrimaryButton(
            pressed,
            {},
            full.testTag("catalog_button_primary_pressed"),
            interactionSource = pressedSources[0],
        )
        VariantCaption(stringResource(Res.string.catalog_button_secondary))
        SecondaryButton(enabled, {}, full.testTag("catalog_button_secondary_enabled"))
        SecondaryButton(disabled, {}, full.testTag("catalog_button_secondary_disabled"), enabled = false)
        SecondaryButton(
            pressed,
            {},
            full.testTag("catalog_button_secondary_pressed"),
            interactionSource = pressedSources[1],
        )
        VariantCaption(stringResource(Res.string.catalog_button_ghost))
        GhostButton(enabled, {}, full.testTag("catalog_button_ghost_enabled"))
        GhostButton(disabled, {}, full.testTag("catalog_button_ghost_disabled"), enabled = false)
        GhostButton(
            pressed,
            {},
            full.testTag("catalog_button_ghost_pressed"),
            interactionSource = pressedSources[2],
        )
    }
}

private const val BUTTON_VARIANTS = 3

@Composable
internal fun VariantCaption(text: String) {
    Text(text = text, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
}

@Composable
private fun ChipsSection() {
    var selected by rememberSaveable { mutableIntStateOf(DEFAULT_TIMEFRAME_INDEX) }
    val labels = listOf(
        stringResource(Res.string.timeframe_1w),
        stringResource(Res.string.timeframe_1m),
        stringResource(Res.string.timeframe_3m),
        stringResource(Res.string.timeframe_1y),
    )
    val descriptions = listOf(
        stringResource(Res.string.timeframe_1w_desc),
        stringResource(Res.string.timeframe_1m_desc),
        stringResource(Res.string.timeframe_3m_desc),
        stringResource(Res.string.timeframe_1y_desc),
    )
    val options =
        remember(labels, descriptions) {
            listOf("1w", "1m", "3m", "1y").mapIndexed { index, key ->
                AppOption(labels[index], "catalog_chip_$key", descriptions[index])
            }
        }
    CatalogSection(stringResource(Res.string.catalog_section_chips), "catalog_section_chips") {
        FilterChipRow(
            options = options,
            selectedIndex = selected,
            onSelect = { selected = it },
            modifier = Modifier.testTag("catalog_chip_row"),
        )
    }
}

@Composable
private fun SegmentedSection() {
    var selected by rememberSaveable { mutableIntStateOf(DEFAULT_STRATEGY_INDEX) }
    val deficit = stringResource(Res.string.goal_strategy_deficit)
    val maintenance = stringResource(Res.string.goal_strategy_maintenance)
    val surplus = stringResource(Res.string.goal_strategy_surplus)
    val options =
        remember(deficit, maintenance, surplus) {
            listOf(
                AppOption(deficit, "catalog_segment_deficit"),
                AppOption(maintenance, "catalog_segment_maintenance"),
                AppOption(surplus, "catalog_segment_surplus"),
            )
        }
    CatalogSection(stringResource(Res.string.catalog_section_segmented), "catalog_section_segmented") {
        SegmentedSelector(
            options = options,
            selectedIndex = selected,
            onSelect = { selected = it },
            modifier = Modifier.testTag("catalog_segmented"),
        )
    }
}

@Composable
private fun TabsSection() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val summary = stringResource(Res.string.exercise_tab_summary)
    val history = stringResource(Res.string.exercise_tab_history)
    val howTo = stringResource(Res.string.exercise_tab_how_to)
    val options =
        remember(summary, history, howTo) {
            listOf(
                AppOption(summary, "catalog_tab_summary"),
                AppOption(history, "catalog_tab_history"),
                AppOption(howTo, "catalog_tab_how_to"),
            )
        }
    CatalogSection(stringResource(Res.string.catalog_section_tabs), "catalog_section_tabs") {
        AppTabRow(
            options = options,
            selectedIndex = selected,
            onSelect = { selected = it },
            modifier = Modifier.testTag("catalog_tabs"),
        )
    }
}

@Preview
@Composable
private fun ComponentCatalogScreenPreview() {
    AppTheme { ComponentCatalogScreen() }
}
