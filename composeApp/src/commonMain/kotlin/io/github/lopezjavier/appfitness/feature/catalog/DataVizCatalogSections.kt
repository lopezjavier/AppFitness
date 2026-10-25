@file:Suppress("TooManyFunctions") // One small section composable per component.

package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_line_chart_cd
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_metric_fat
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_metric_fat_trend
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_metric_goal
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_ring_empty
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_ring_full
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_ring_normal
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_ring_over
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_line_chart
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_macro_bars
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_macro_sliders
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_macro_stacked_bar
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_metric_tiles
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_ring
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_trend_pills
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_state_disabled
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_text_pill
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_trend_caution
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_trend_favorable
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_trend_unfavorable
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_record_legend
import io.github.lopezjavier.appfitness.composeapp.resources.macro_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.macro_fat
import io.github.lopezjavier.appfitness.composeapp.resources.macro_protein
import io.github.lopezjavier.appfitness.composeapp.resources.weekly_rate_no_data
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppLineChart
import io.github.lopezjavier.appfitness.designsystem.AppLineChartBlock
import io.github.lopezjavier.appfitness.designsystem.AppLineChartDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroProgressBar
import io.github.lopezjavier.appfitness.designsystem.MacroSlider
import io.github.lopezjavier.appfitness.designsystem.MacroSliderDefaults
import io.github.lopezjavier.appfitness.designsystem.MacroStackedBar
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.ProgressRing
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TextPill
import io.github.lopezjavier.appfitness.designsystem.TrendDirection
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val DEMO_TARGET_KCAL = 2400
private const val RING_NORMAL_KCAL = 1840
private const val RING_OVER_KCAL = 2520
private const val DEFAULT_PROTEIN_PERCENT = 30
private const val DEFAULT_CARBS_PERCENT = 45
private const val DEFAULT_FAT_PERCENT = 25
private const val DISABLED_PROTEIN_PERCENT = 40
private const val PERCENT_BASE = 100f
private const val PROTEIN_CARBS_KCAL_PER_GRAM = 4f
private const val FAT_KCAL_PER_GRAM = 9f
private const val PROTEIN_CONSUMED_G = 142
private const val PROTEIN_TARGET_G = 180
private const val CARBS_CONSUMED_G = 210
private const val CARBS_TARGET_G = 270
private const val FAT_CONSUMED_G = 48
private const val FAT_TARGET_G = 67
private const val PROTEIN_OVER_CONSUMED_G = 200
private const val PROTEIN_DECIMAL_CONSUMED_G = 100.9
private const val PROTEIN_DECIMAL_TARGET_G = 200

/** Catalog sections of the data visualization components (T-024). */
internal fun LazyListScope.dataVizItems() {
    item { RingSection() }
    item { MacroBarsSection() }
    item { MacroSlidersSection() }
    item { MacroStackedBarSection() }
    item { MetricTilesSection() }
    item { TrendPillsSection() }
    item { LineChartSection() }
}

private val RingStates =
    listOf(
        Triple(Res.string.catalog_ring_empty, 0, "catalog_ring_empty"),
        Triple(Res.string.catalog_ring_normal, RING_NORMAL_KCAL, "catalog_ring_normal"),
        Triple(Res.string.catalog_ring_full, DEMO_TARGET_KCAL, "catalog_ring_full"),
        Triple(Res.string.catalog_ring_over, RING_OVER_KCAL, "catalog_ring_over"),
    )

@Composable
private fun RingSection() {
    CatalogSection(stringResource(Res.string.catalog_section_ring), "catalog_section_ring") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                RingStates.forEach { (caption, consumed, tag) ->
                    VariantCaption(stringResource(caption))
                    ProgressRing(consumed, DEMO_TARGET_KCAL, Modifier.testTag(tag))
                }
            }
        }
    }
}

@Composable
private fun MacroBarsSection() {
    CatalogSection(stringResource(Res.string.catalog_section_macro_bars), "catalog_section_macro_bars") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                MacroProgressBar(
                    stringResource(Res.string.macro_protein),
                    Macro.Protein,
                    PROTEIN_CONSUMED_G,
                    PROTEIN_TARGET_G,
                    Modifier.testTag("catalog_macro_bar_protein"),
                )
                MacroProgressBar(
                    stringResource(Res.string.macro_carbs),
                    Macro.Carbs,
                    CARBS_CONSUMED_G,
                    CARBS_TARGET_G,
                    Modifier.testTag("catalog_macro_bar_carbs"),
                )
                MacroProgressBar(
                    stringResource(Res.string.macro_fat),
                    Macro.Fat,
                    FAT_CONSUMED_G,
                    FAT_TARGET_G,
                    Modifier.testTag("catalog_macro_bar_fat"),
                )
                MacroProgressBar(
                    stringResource(Res.string.macro_protein),
                    Macro.Protein,
                    PROTEIN_OVER_CONSUMED_G,
                    PROTEIN_TARGET_G,
                    Modifier.testTag("catalog_macro_bar_over"),
                )
                MacroProgressBar(
                    stringResource(Res.string.macro_protein),
                    Macro.Protein,
                    PROTEIN_DECIMAL_CONSUMED_G,
                    PROTEIN_DECIMAL_TARGET_G,
                    Modifier.testTag("catalog_macro_bar_decimal"),
                )
            }
        }
    }
}

@Composable
private fun MacroStackedBarSection() {
    CatalogSection(
        stringResource(Res.string.catalog_section_macro_stacked_bar),
        "catalog_section_macro_stacked_bar",
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            MacroStackedBar(
                proteinPct = DEFAULT_PROTEIN_PERCENT,
                carbsPct = DEFAULT_CARBS_PERCENT,
                fatPct = DEFAULT_FAT_PERCENT,
                testTag = "catalog_macro_stacked_bar",
            )
        }
    }
}

@Composable
private fun MacroSlidersSection() {
    var protein by rememberSaveable { mutableIntStateOf(DEFAULT_PROTEIN_PERCENT) }
    var carbs by rememberSaveable { mutableIntStateOf(DEFAULT_CARBS_PERCENT) }
    var fat by rememberSaveable { mutableIntStateOf(DEFAULT_FAT_PERCENT) }
    CatalogSection(stringResource(Res.string.catalog_section_macro_sliders), "catalog_section_macro_sliders") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(MacroSliderDefaults.SliderGap)) {
                MacroSliderSample(
                    stringResource(Res.string.macro_protein),
                    Macro.Protein,
                    protein,
                    { protein = it },
                    "catalog_macro_slider_protein",
                )
                MacroSliderSample(
                    stringResource(Res.string.macro_carbs),
                    Macro.Carbs,
                    carbs,
                    { carbs = it },
                    "catalog_macro_slider_carbs",
                )
                MacroSliderSample(
                    stringResource(Res.string.macro_fat),
                    Macro.Fat,
                    fat,
                    { fat = it },
                    "catalog_macro_slider_fat",
                )
                VariantCaption(stringResource(Res.string.catalog_state_disabled))
                MacroSliderSample(
                    stringResource(Res.string.macro_protein),
                    Macro.Protein,
                    DISABLED_PROTEIN_PERCENT,
                    {},
                    "catalog_macro_slider_disabled",
                    enabled = false,
                )
            }
        }
    }
}

@Composable
private fun MacroSliderSample(
    label: String,
    macro: Macro,
    percent: Int,
    onPercentChange: (Int) -> Unit,
    testTag: String,
    enabled: Boolean = true,
) {
    MacroSlider(
        label = label,
        macro = macro,
        percent = percent,
        grams = demoGrams(percent, macro),
        kcal = demoKcal(percent),
        onPercentChange = onPercentChange,
        modifier = Modifier.testTag(testTag),
        enabled = enabled,
    )
}

// Demo-only copy of the 04 section 1.5 maths (kcal per gram 4/4/9); the real formula is E2 domain.
private fun demoKcal(percent: Int): Int = (DEMO_TARGET_KCAL * percent / PERCENT_BASE).roundToInt()

private fun demoGrams(percent: Int, macro: Macro): Int {
    val kcalPerGram = if (macro == Macro.Fat) FAT_KCAL_PER_GRAM else PROTEIN_CARBS_KCAL_PER_GRAM
    return (DEMO_TARGET_KCAL * percent / PERCENT_BASE / kcalPerGram).roundToInt()
}

@Composable
private fun MetricTilesSection() {
    CatalogSection(stringResource(Res.string.catalog_section_metric_tiles), "catalog_section_metric_tiles") {
        MetricTile(
            label = stringResource(Res.string.catalog_metric_fat),
            value = "14,8",
            unit = "%",
            modifier = Modifier.fillMaxWidth().testTag("catalog_metric_tile_fat"),
            supporting = {
                TrendPill(
                    stringResource(Res.string.catalog_metric_fat_trend),
                    TrendTone.Favorable,
                    direction = TrendDirection.Down,
                )
            },
        )
        MetricTile(
            label = stringResource(Res.string.catalog_metric_goal),
            value = "72,0",
            unit = "kg",
            modifier = Modifier.fillMaxWidth().testTag("catalog_metric_tile_goal"),
        )
        MetricTile(
            label = stringResource(Res.string.catalog_metric_fat),
            value = "14,8",
            unit = "%",
            modifier = Modifier.fillMaxWidth().testTag("catalog_metric_tile_raised"),
            containerColor = AppTheme.colors.surfaceRaised,
            supporting = {
                TrendPill(
                    stringResource(Res.string.catalog_metric_fat_trend),
                    TrendTone.Favorable,
                    direction = TrendDirection.Down,
                    containerColor = AppTheme.colors.surfaceBase,
                )
            },
        )
    }
}

@Composable
private fun TrendPillsSection() {
    CatalogSection(stringResource(Res.string.catalog_section_trend_pills), "catalog_section_trend_pills") {
        TrendPill(
            stringResource(Res.string.catalog_trend_favorable),
            TrendTone.Favorable,
            Modifier.testTag("catalog_trend_favorable"),
            TrendDirection.Down,
        )
        TrendPill(
            stringResource(Res.string.catalog_trend_unfavorable),
            TrendTone.Unfavorable,
            Modifier.testTag("catalog_trend_unfavorable"),
            TrendDirection.Up,
        )
        TrendPill(
            stringResource(Res.string.catalog_trend_caution),
            TrendTone.Caution,
            Modifier.testTag("catalog_trend_caution"),
            TrendDirection.Up,
        )
        TrendPill(
            stringResource(Res.string.weekly_rate_no_data),
            TrendTone.Neutral,
            Modifier.testTag("catalog_trend_neutral"),
        )
        TextPill(
            stringResource(Res.string.catalog_text_pill),
            Modifier.testTag("catalog_text_pill"),
        )
    }
}

private val DemoChartValues = listOf(100.0, 104.0, 102.5, 108.0, 113.5)
private val DemoMaValues = listOf(null, 102.0, 103.0, 104.5, 107.0)
private val DemoChartLabels = listOf("1 oct", "8 oct", "15 oct", "22 oct", "29 oct")

private val DemoGapValues = listOf(100.0, null, null, 103.0, null, null, null, 101.0)
private val DemoGapLabels = listOf("1 oct", "2 oct", "3 oct", "4 oct", "5 oct", "6 oct", "7 oct", "8 oct")

private const val DEMO_REFERENCE_LINE = 105.0

@Composable
private fun LineChartSection() {
    CatalogSection(stringResource(Res.string.catalog_section_line_chart), "catalog_section_line_chart") {
        AppCard(Modifier.fillMaxWidth()) {
            AppLineChart(
                values = DemoChartValues,
                labels = DemoChartLabels,
                description = stringResource(Res.string.catalog_line_chart_cd),
                modifier = Modifier.fillMaxWidth().testTag("catalog_line_chart"),
                valueLabel = { it.roundToInt().toString() },
            )
        }
        AppCard(Modifier.fillMaxWidth()) {
            AppLineChart(
                values = DemoMaValues,
                labels = DemoChartLabels,
                description = stringResource(Res.string.catalog_line_chart_cd),
                modifier = Modifier.fillMaxWidth().testTag("catalog_line_chart_dots"),
                valueLabel = { it.roundToInt().toString() },
                dots = DemoChartValues,
                lineThickness = AppLineChartDefaults.MaLineThickness,
                referenceLine = DEMO_REFERENCE_LINE,
            )
        }
        AppCard(Modifier.fillMaxWidth()) {
            AppLineChart(
                values = DemoGapValues,
                labels = DemoGapLabels,
                description = stringResource(Res.string.catalog_line_chart_cd),
                modifier = Modifier.fillMaxWidth().testTag("catalog_line_chart_gaps"),
                valueLabel = { it.roundToInt().toString() },
                dots = DemoGapValues,
                lineThickness = AppLineChartDefaults.MaLineThickness,
                connectGaps = true,
            )
        }
        MarkerChartCards()
    }
}

private val DemoSparseMarkers = listOf(0, 3)

/** Record markers: sparse non-adjacent indices with the legend, and a single marker. */
@Composable
private fun MarkerChartCards() = Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
    AppCard(Modifier.fillMaxWidth()) {
        AppLineChartBlock(
            title = stringResource(Res.string.catalog_section_line_chart),
            recordLegend = stringResource(Res.string.exercise_progress_record_legend),
        ) {
            AppLineChart(
                values = DemoChartValues,
                labels = DemoChartLabels,
                description = stringResource(Res.string.catalog_line_chart_cd),
                modifier = Modifier.fillMaxWidth().testTag("catalog_line_chart_markers"),
                valueLabel = { it.roundToInt().toString() },
                markers = DemoSparseMarkers,
            )
        }
    }
    AppCard(Modifier.fillMaxWidth()) {
        AppLineChart(
            values = DemoChartValues,
            labels = DemoChartLabels,
            description = stringResource(Res.string.catalog_line_chart_cd),
            modifier = Modifier.fillMaxWidth().testTag("catalog_line_chart_single_marker"),
            valueLabel = { it.roundToInt().toString() },
            markers = listOf(DemoChartValues.lastIndex),
        )
    }
}
