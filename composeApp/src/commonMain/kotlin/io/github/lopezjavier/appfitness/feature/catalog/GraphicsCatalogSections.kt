@file:Suppress("MagicNumber") // Sample data of the catalog: made-up figures, never shown to a user.

package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_graphics_sample_label
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_graphics_sample_text
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_graphics_sample_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_celebration
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_graphics
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_screen_graphics
import io.github.lopezjavier.appfitness.composeapp.resources.ic_bar_chart
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flame_outlined
import io.github.lopezjavier.appfitness.composeapp.resources.ic_insights
import io.github.lopezjavier.appfitness.composeapp.resources.ic_live_trophy
import io.github.lopezjavier.appfitness.composeapp.resources.ic_restaurant_outlined
import io.github.lopezjavier.appfitness.designsystem.ActivityHeatmap
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CelebrationBurst
import io.github.lopezjavier.appfitness.designsystem.DiaryAnalysingGraphic
import io.github.lopezjavier.appfitness.designsystem.DiaryDot
import io.github.lopezjavier.appfitness.designsystem.DiaryShareBar
import io.github.lopezjavier.appfitness.designsystem.DiarySplitBar
import io.github.lopezjavier.appfitness.designsystem.DiarySplitPart
import io.github.lopezjavier.appfitness.designsystem.DonutChart
import io.github.lopezjavier.appfitness.designsystem.DonutSlice
import io.github.lopezjavier.appfitness.designsystem.GaugeBar
import io.github.lopezjavier.appfitness.designsystem.GaugeZone
import io.github.lopezjavier.appfitness.designsystem.HeroStat
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.LegendRow
import io.github.lopezjavier.appfitness.designsystem.LiveStatusStrip
import io.github.lopezjavier.appfitness.designsystem.MiniBarChart
import io.github.lopezjavier.appfitness.designsystem.RecordBadge
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.Sparkline
import io.github.lopezjavier.appfitness.designsystem.StartHeroStage
import io.github.lopezjavier.appfitness.designsystem.StartIntensityBars
import io.github.lopezjavier.appfitness.designsystem.StartShimmerBar
import io.github.lopezjavier.appfitness.designsystem.StartStepProgress
import io.github.lopezjavier.appfitness.designsystem.StartValueProp
import io.github.lopezjavier.appfitness.designsystem.StatRing
import io.github.lopezjavier.appfitness.designsystem.TrainerBeforeAfter
import io.github.lopezjavier.appfitness.designsystem.TrainerTintedRow
import io.github.lopezjavier.appfitness.designsystem.WeekStrip
import io.github.lopezjavier.appfitness.designsystem.WeekStripDay
import org.jetbrains.compose.resources.stringResource

/** Catalog sections of the premium graphics kit: data graphics, celebration, and the per-screen graphics. */
internal fun LazyListScope.graphicsItems() {
    item { DataGraphicsSection() }
    item { CelebrationSection() }
    item { ScreenGraphicsSection() }
    trainingPartIconItems()
}

private val SampleSeries = listOf(78.4, 78.1, 78.3, 77.8, 77.6, 77.2, 77.0)
private val SampleBars = listOf(1800f, 2100f, 2400f, 2650f, 1900f, 2200f, 2300f)
private val SampleHeatmap = List(56) { it * 7 % 5 }

@Composable
private fun DataGraphicsSection() {
    val colors = AppTheme.colors
    val label = stringResource(Res.string.catalog_graphics_sample_label)
    CatalogSection(stringResource(Res.string.catalog_section_graphics), "catalog_section_graphics") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Res.drawable.ic_fitness_center, colors.athleticCyan)
                    HeroStat(Res.drawable.ic_flame_outlined, "2150", label, colors.energyAmber)
                    StatRing(0.7f, color = colors.neonLime) { Text("70", color = colors.textPrimary) }
                }
                Sparkline(SampleSeries, Modifier.fillMaxWidth())
                MiniBarChart(
                    values = SampleBars,
                    modifier = Modifier.fillMaxWidth(),
                    labels = List(SampleBars.size) { (it + 1).toString() },
                    highlightIndex = 6,
                    target = 2400f,
                    overColor = colors.energyAmber,
                )
                WeekStrip(
                    List(7) { WeekStripDay((it + 1).toString(), done = it < 4, today = it == 4, progress = 0.5f) },
                    Modifier.fillMaxWidth(),
                )
                GaugeBar(
                    value = 23.0,
                    min = 15.0,
                    zones = listOf(
                        GaugeZone(18.5, colors.athleticCyan),
                        GaugeZone(25.0, colors.electricGreen),
                        GaugeZone(40.0, colors.error),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DonutChart(
                        slices = listOf(
                            DonutSlice(30f, colors.macroProtein),
                            DonutSlice(45f, colors.macroCarbs),
                            DonutSlice(25f, colors.macroFat),
                        ),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        LegendRow(label, "30", colors.macroProtein)
                        LegendRow(label, "45", colors.macroCarbs)
                        LegendRow(label, "25", colors.macroFat)
                    }
                }
                ActivityHeatmap(SampleHeatmap, Modifier.fillMaxWidth(), highlightIndex = SampleHeatmap.lastIndex)
            }
        }
    }
}

@Composable
private fun CelebrationSection() {
    CatalogSection(stringResource(Res.string.catalog_section_celebration), "catalog_section_celebration") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                CelebrationBurst(Res.drawable.ic_live_trophy)
                RecordBadge(stringResource(Res.string.catalog_graphics_sample_label))
            }
        }
    }
}

@Composable
private fun ScreenGraphicsSection() {
    val colors = AppTheme.colors
    val label = stringResource(Res.string.catalog_graphics_sample_label)
    val title = stringResource(Res.string.catalog_graphics_sample_title)
    val text = stringResource(Res.string.catalog_graphics_sample_text)
    CatalogSection(stringResource(Res.string.catalog_section_screen_graphics), "catalog_section_screen_graphics") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                StartHeroStage(Modifier) { IconBadge(Res.drawable.ic_insights, colors.neonLime) }
                StartStepProgress(step = 1, total = 4, Modifier.fillMaxWidth())
                StartIntensityBars(level = 3, color = colors.neonLime)
                StartShimmerBar()
                StartValueProp(Res.drawable.ic_restaurant_outlined, colors.neonLime, title, text)
                DiarySplitBar(listOf(DiarySplitPart(30f, colors.macroProtein), DiarySplitPart(70f, colors.macroCarbs)))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DiaryDot(colors.macroFat)
                    DiaryShareBar(0.4f, colors.macroFat, Modifier.weight(1f))
                }
                DiaryAnalysingGraphic(Res.drawable.ic_bar_chart)
                LiveStatusStrip(statusText = label, progressText = "12", paused = false, elapsedText = "32")
                TrainerBeforeAfter(100.0, 112.5, "100", "112")
                TrainerTintedRow(accent = colors.athleticCyan, leading = {
                    IconBadge(Res.drawable.ic_fitness_center, colors.athleticCyan)
                }) {
                    Text(title, color = colors.textPrimary, style = AppTheme.typography.titleMedium)
                }
            }
        }
    }
}
