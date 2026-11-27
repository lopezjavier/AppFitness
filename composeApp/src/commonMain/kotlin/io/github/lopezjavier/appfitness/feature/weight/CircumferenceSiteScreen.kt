package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_chart_cd
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_history_header
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_margin_legend
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_one_point_note
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_row_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_row_edit_label
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_value_cm
import io.github.lopezjavier.appfitness.composeapp.resources.ic_straighten
import io.github.lopezjavier.appfitness.composeapp.resources.px_body_row_supporting
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppLineChart
import io.github.lopezjavier.appfitness.designsystem.AppLineChartDefaults
import io.github.lopezjavier.appfitness.designsystem.AppLineChartSkeleton
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.MeasurementRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceChange
import io.github.lopezjavier.appfitness.domain.usecase.CircumferenceSiteSummary
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

private val HeroSkeletonHeight = Spacing.xl * 4
private val RowSkeletonHeight = Spacing.minTouchTarget
private const val SKELETON_ROWS = 3

/**
 * The history of one circumference [site] (T-324): a screen (not a sheet, so the edit sheet opens over it and
 * it survives leaving the app as a route) titled with the site's name (R-01, R-04). Top to bottom: the hero (latest
 * value, its date and the change slot), the chart of every measurement day (the latest marked; for a site with a
 * margin, the margin around the compared value as a shaded band with its legend), then "Historial": every measurement
 * newest first as a [MeasurementRow] ("Ayer · −1,0 cm"), tap to edit, swipe to delete (R-17, R-20, R-21). On a wide
 * window the hero and chart sit left and the list right. There is no empty state (R-31): while the site's last value
 * is being deleted the last summary stays with an empty "Historial" until the root leaves, and
 * [CircumferenceSiteUiState.Gone] draws nothing (the root leaves at once).
 */
@Composable
@Suppress("LongParameterList") // The site, its state and its event sink, plus navigation.
fun CircumferenceSiteScreen(
    site: CircumferenceSite,
    state: CircumferenceSiteUiState,
    today: LocalDate,
    onEvent: (CircumferencesEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
        AppScreen(
            title = circumferenceSiteLabel(site),
            onBack = onBack,
            maxContentWidth = if (wide) {
                WindowWidthClassDefaults.TwoColumnContentMaxWidth
            } else {
                WindowWidthClassDefaults.FormContentMaxWidth
            },
            testTagPrefix = "circumference_site",
        ) {
            Box(Modifier.fillMaxSize().testTag("circumference_site_screen")) {
                when (state) {
                    CircumferenceSiteUiState.Loading -> SiteLoading()

                    CircumferenceSiteUiState.Error -> ErrorState(
                        message = stringResource(Res.string.circumference_error_message),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(CircumferencesEvent.Retry)
                        },
                        modifier = Modifier.align(Alignment.Center),
                        testTagPrefix = "circumference_site_error",
                    )

                    is CircumferenceSiteUiState.Content -> {
                        SiteContent(state, today, timeZone, onEvent, wide)
                        state.sheet?.let { CircumferenceSheet(it, state.isOnline, today, onEvent) }
                    }

                    CircumferenceSiteUiState.Gone -> Unit
                }
            }
        }
    }
}

@Composable
private fun SiteContent(
    state: CircumferenceSiteUiState.Content,
    today: LocalDate,
    timeZone: TimeZone,
    onEvent: (CircumferencesEvent) -> Unit,
    wide: Boolean,
) {
    val summary = state.summary
    val rows = state.rows
    val isOnline = state.isOnline
    if (wide) {
        Row(Modifier.fillMaxSize().testTag("circumference_site_two_column")) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                SiteHero(summary, today)
                SiteChartCard(summary)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight().testTag("circumference_site_history_list"),
                contentPadding = PaddingValues(Spacing.screenMargin),
            ) {
                historyItems(rows, isOnline, today, timeZone, onEvent)
            }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().testTag("circumference_site_list"),
            contentPadding = PaddingValues(Spacing.screenMargin),
        ) {
            item(key = "hero") { SiteHero(summary, today) }
            item(key = "chart") { SiteChartCard(summary, Modifier.padding(top = Spacing.cardGap)) }
            item(key = "gap") { Box(Modifier.height(Spacing.blockGap)) }
            historyItems(rows, isOnline, today, timeZone, onEvent)
        }
    }
}

/** The latest value as the hero figure, its date, and the change slot of the site row. */
@Composable
private fun SiteHero(summary: CircumferenceSiteSummary, today: LocalDate) {
    AppCard(Modifier.fillMaxWidth().testTag("circumference_site_hero")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                IconBadge(Res.drawable.ic_straighten, AppTheme.colors.athleticCyan)
                Column {
                    Text(
                        text = stringResource(Res.string.circumference_value_cm, decimalText(summary.latestCm)),
                        style = AppTheme.typography.headlineMetric,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.testTag("circumference_site_value"),
                    )
                    Text(
                        text = AppDateFormat.list(summary.latestDate, today),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
            if (summary.change is CircumferenceChange.Compared) {
                CircumferenceChangeLine(summary.change, summary.latestDate, today, tagPrefix = "circumference_site")
            }
        }
    }
}

/**
 * The chart of every measurement day, spaced by date (a daily axis joined across the gaps, as the weight chart):
 * a dot per measurement and the latest marked. With a margin, the band "margen de medida" around the compared value.
 * One measurement only: a note instead of a one-dot chart.
 */
@Composable
private fun SiteChartCard(summary: CircumferenceSiteSummary, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth().testTag("circumference_site_chart_card")) {
        if (summary.history.size < 2) {
            Text(
                text = stringResource(Res.string.circumference_one_point_note),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("circumference_site_one_point"),
            )
            return@AppCard
        }
        val first = summary.history.first()
        val days = first.date.daysUntil(summary.latestDate)
        val byDate = summary.history.associate { it.date to it.cm }
        val dates = (0..days).map { first.date.plus(it, DateTimeUnit.DAY) }
        val values = dates.map { byDate[it] }
        val compared = summary.change as? CircumferenceChange.Compared
        val band = compared?.marginCm?.let { (compared.refCm - it)..(compared.refCm + it) }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppLineChart(
                values = values,
                labels = dates.map { AppDateFormat.shortDate(it) },
                description = stringResource(
                    Res.string.circumference_chart_cd,
                    circumferenceSiteLabel(summary.site),
                    decimalText(first.cm),
                    AppDateFormat.shortDate(first.date),
                    decimalText(summary.latestCm),
                    AppDateFormat.shortDate(summary.latestDate),
                ),
                modifier = Modifier.fillMaxWidth().testTag("circumference_site_chart"),
                valueLabel = { decimalText(it) },
                dots = values,
                animated = LocalAnimationsEnabled.current,
                connectGaps = true,
                markers = listOf(values.lastIndex),
                band = band,
                domainStep = CM_DOMAIN_STEP,
            )
            compared?.marginCm?.let { MarginLegend(it) }
        }
    }
}

private const val CM_DOMAIN_STEP = 2.0

@Composable
private fun MarginLegend(marginCm: Double) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("circumference_site_margin_legend"),
    ) {
        Box(
            Modifier
                .size(Spacing.md)
                .background(
                    AppTheme.colors.athleticCyan.copy(alpha = AppLineChartDefaults.BAND_ALPHA * 2),
                    AppShapes.sm,
                ),
        )
        Text(
            text = stringResource(Res.string.circumference_margin_legend, decimalText(marginCm)),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/** "Historial": every measurement newest first, with its change against the one before it. */
private fun LazyListScope.historyItems(
    rows: List<BodyCircumference>,
    isOnline: Boolean,
    today: LocalDate,
    timeZone: TimeZone,
    onEvent: (CircumferencesEvent) -> Unit,
) {
    item(key = "history_header") {
        ListSectionHeader(
            title = stringResource(Res.string.circumference_history_header),
            modifier = Modifier.padding(bottom = Spacing.sm).testTag("circumference_site_history_header"),
            count = rows.size,
        )
    }
    items(rows.size, key = { rows[it].id }) { index ->
        HistoryRow(
            rows[index],
            rows.getOrNull(index + 1),
            isOnline,
            today,
            timeZone,
            onEvent,
            appAnimateItem(),
        )
    }
}

@Suppress("LongParameterList") // The row, its neighbour and the row context.
@Composable
private fun HistoryRow(
    row: BodyCircumference,
    previous: BodyCircumference?,
    isOnline: Boolean,
    today: LocalDate,
    timeZone: TimeZone,
    onEvent: (CircumferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = AppDateFormat.list(row.measuredAt.toLocalDateTime(timeZone).date, today)
    val value = stringResource(Res.string.circumference_value_cm, decimalText(row.valueCm))
    SwipeToDeleteRow(
        deleteLabel = stringResource(Res.string.circumference_row_delete_cd, circumferenceSiteLabel(row.site), value),
        enabled = isOnline,
        onDelete = { onEvent(CircumferencesEvent.Delete(row.id)) },
        backgroundTag = "circumference_row_delete_bg_${row.id}",
        modifier = modifier.testTag("circumference_row_${row.id}"),
        containerColor = AppTheme.colors.surfaceBase,
    ) {
        MeasurementRow(
            value = value,
            supporting = previous?.let {
                stringResource(
                    Res.string.px_body_row_supporting,
                    date,
                    stringResource(Res.string.circumference_value_cm, signedTenths(row.valueCm - it.valueCm)),
                )
            } ?: date,
            onClick = { onEvent(CircumferencesEvent.OpenEdit(row.id)) },
            clickLabel = stringResource(Res.string.circumference_row_edit_label),
        )
    }
}

@Composable
private fun SiteLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("circumference_site_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(HeroSkeletonHeight), AppShapes.lg)
        AppLineChartSkeleton()
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(RowSkeletonHeight), AppShapes.base) }
    }
}
