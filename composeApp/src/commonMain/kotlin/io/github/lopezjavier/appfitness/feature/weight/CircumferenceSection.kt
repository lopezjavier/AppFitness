package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_add_object
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_ratio_label
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_ratio_no_data
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_ratio_unavailable
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_arm
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_calf
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_chest
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_hip
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_neck
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_thigh
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_waist
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_straighten
import io.github.lopezjavier.appfitness.composeapp.resources.metric_tile_open_label
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.MetricTileRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** Columns of the circumference grid (T-326): the ratio card, then one cell per site. */
private const val GRID_COLUMNS = 2

/** Skeleton cells while loading: two rows of the grid. */
private const val SKELETON_CELLS = 4
private val SkeletonCellHeight = Spacing.xl * 5

/**
 * The "Circunferencias" section of the weight screen (T-326): a 2-column grid whose first cell is the waist-to-hip
 * ratio card, then one [CircumferenceSiteCell] per site with data, in site order (tap opens the site screen through
 * [onOpenSite]); an odd count leaves the last cell empty, and from font scale 1.3 the grid stacks into one column
 * (C-160, through [MetricTileRow]). "Añadir circunferencia" stays under the grid. Loading, error and empty are local
 * to the section, so the calendar and the weight stay usable if only the circumferences fail. [today] labels the
 * dates "Hoy" / "Ayer". [ratioGauge] is the ratio's status chip and gauge, when the goal state has the metric.
 */
@Composable
internal fun CircumferenceSection(
    state: CircumferencesUiState,
    today: LocalDate,
    onEvent: (CircumferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
    onRatioClick: (() -> Unit)? = null,
    ratioGauge: (@Composable () -> Unit)? = null,
    onOpenSite: (CircumferenceSite) -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().testTag("circumference_section"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ListSectionHeader(
            title = stringResource(Res.string.circumference_title),
            modifier = Modifier.testTag("circumference_title"),
        )
        when (state) {
            CircumferencesUiState.Loading -> SectionLoading()

            CircumferencesUiState.Error -> ErrorState(
                message = stringResource(Res.string.circumference_error_message),
                action = AppStateAction(stringResource(Res.string.action_retry)) {
                    onEvent(CircumferencesEvent.Retry)
                },
                modifier = Modifier.fillMaxWidth(),
                testTagPrefix = "circumference_error",
            )

            is CircumferencesUiState.Content -> SectionContent(
                state.content,
                today,
                onEvent,
                onRatioClick,
                ratioGauge,
                onOpenSite,
            )
        }
    }
}

/** Skeleton of the grid: two rows of two cells (one column from font scale 1.3, as the grid). */
@Composable
private fun SectionLoading() {
    val cells: List<@Composable (Modifier) -> Unit> = List(SKELETON_CELLS) {
        { cell -> SkeletonBox(cell.height(SkeletonCellHeight), AppShapes.lg) }
    }
    CellGrid(cells, Modifier.testTag("circumference_loading"))
}

@Suppress("LongParameterList") // The content plus the ratio card and site callbacks.
@Composable
private fun SectionContent(
    content: CircumferencesContent,
    today: LocalDate,
    onEvent: (CircumferencesEvent) -> Unit,
    onRatioClick: (() -> Unit)?,
    ratioGauge: (@Composable () -> Unit)?,
    onOpenSite: (CircumferenceSite) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (content.isEmpty) {
            EmptyState(
                icon = Res.drawable.ic_straighten,
                message = stringResource(Res.string.circumference_empty_message),
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.circumference_empty_title),
                testTagPrefix = "circumference_empty",
            )
        } else {
            val cells: List<@Composable (Modifier) -> Unit> = buildList {
                add { cell -> RatioCard(content.ratio, onRatioClick, ratioGauge, cell) }
                content.sites.forEach { summary ->
                    add { cell -> CircumferenceSiteCell(summary, today, { onOpenSite(summary.site) }, cell) }
                }
            }
            CellGrid(cells, Modifier.testTag("circumference_sites"))
        }
        AddItemButton(
            objectLabel = stringResource(Res.string.circumference_add_object),
            onClick = { onEvent(CircumferencesEvent.OpenAdd) },
            modifier = Modifier.testTag("circumference_add_button"),
        )
    }
}

/**
 * [cells] in rows of [GRID_COLUMNS] equal-width, equal-height cells ([MetricTileRow]; a short last row keeps an empty
 * slot), stacked into one column from font scale 1.3.
 */
@Composable
private fun CellGrid(cells: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        cells.chunked(GRID_COLUMNS).forEach { row ->
            MetricTileRow(row, spacing = Spacing.cardGap, columns = GRID_COLUMNS)
        }
    }
}

/**
 * The waist-to-hip ratio, first cell of the grid: label, value and [gauge] (status chip and gauge, like the BMI and
 * body-fat cards), or "—" and "Sin datos suficientes" without a waist and hip pair (then it opens nothing).
 */
@Composable
private fun RatioCard(
    ratio: Double?,
    onClick: (() -> Unit)?,
    gauge: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    MetricCard(
        label = stringResource(Res.string.circumference_ratio_label),
        value = ratio?.let(::ratioText) ?: stringResource(Res.string.circumference_ratio_unavailable),
        modifier = modifier.testTag("weight_whr_card"),
        supporting = if (ratio == null) {
            {
                Text(
                    text = stringResource(Res.string.circumference_ratio_no_data),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
        } else {
            gauge
        },
        onClick = if (ratio != null) onClick else null,
        clickLabel = stringResource(Res.string.metric_tile_open_label),
    )
}

/** The ratio with a decimal comma and two decimals: 0.86 -> "0,86". */
internal fun ratioText(ratio: Double): String {
    val hundredths = kotlin.math.round(ratio * HUNDREDTHS).toInt()
    return "${hundredths / HUNDREDTHS.toInt()},${(hundredths % HUNDREDTHS.toInt()).toString().padStart(2, '0')}"
}

private const val HUNDREDTHS = 100.0

@Composable
internal fun circumferenceSiteLabel(site: CircumferenceSite): String = stringResource(
    when (site) {
        CircumferenceSite.Neck -> Res.string.circumference_site_neck
        CircumferenceSite.Chest -> Res.string.circumference_site_chest
        CircumferenceSite.Waist -> Res.string.circumference_site_waist
        CircumferenceSite.Hip -> Res.string.circumference_site_hip
        CircumferenceSite.Arm -> Res.string.circumference_site_arm
        CircumferenceSite.Thigh -> Res.string.circumference_site_thigh
        CircumferenceSite.Calf -> Res.string.circumference_site_calf
    },
)
