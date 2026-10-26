package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_add_object
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_ratio_label
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_ratio_no_data
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_ratio_unavailable
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_row_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_row_edit_label
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_arm
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_calf
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_chest
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_hip
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_neck
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_thigh
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_site_waist
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_title
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_value_cm
import io.github.lopezjavier.appfitness.composeapp.resources.metric_tile_open_label
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.MeasurementRow
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

private val SkeletonTileHeight = Spacing.xl * 4
private val SkeletonRowHeight = Spacing.minTouchTarget
private const val SKELETON_ROWS = 3

/**
 * The "Circunferencias" section of the weight screen: the waist-to-hip ratio tile and the history per site (newest
 * first, swipe to delete, tap to edit). Loading, error and empty are local to the section, so the calendar and the
 * weight stay usable if only the circumferences fail. [today] labels the dates "Hoy" / "Ayer".
 */
@Composable
internal fun CircumferenceSection(
    state: CircumferencesUiState,
    today: LocalDate,
    timeZone: TimeZone,
    onEvent: (CircumferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
    onRatioClick: (() -> Unit)? = null,
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
                action = AppStateAction(stringResource(Res.string.state_retry)) {
                    onEvent(CircumferencesEvent.Retry)
                },
                modifier = Modifier.fillMaxWidth(),
                testTagPrefix = "circumference_error",
            )

            is CircumferencesUiState.Content -> SectionContent(state.content, today, timeZone, onEvent, onRatioClick)
        }
    }
}

@Composable
private fun SectionLoading() {
    Column(
        Modifier.fillMaxWidth().testTag("circumference_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonTileHeight), AppShapes.lg)
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(SkeletonRowHeight), AppShapes.base) }
    }
}

@Composable
private fun SectionContent(
    content: CircumferencesContent,
    today: LocalDate,
    timeZone: TimeZone,
    onEvent: (CircumferencesEvent) -> Unit,
    onRatioClick: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (content.isEmpty) {
            EmptyState(
                message = stringResource(Res.string.circumference_empty_message),
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.circumference_empty_title),
                testTagPrefix = "circumference_empty",
            )
        } else {
            RatioCard(content.ratio, onRatioClick)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                content.groups.forEach { group -> SiteGroup(group, content.isOnline, today, timeZone, onEvent) }
            }
        }
        AddItemButton(
            objectLabel = stringResource(Res.string.circumference_add_object),
            onClick = { onEvent(CircumferencesEvent.OpenAdd) },
            modifier = Modifier.testTag("circumference_add_button"),
        )
    }
}

@Composable
private fun RatioCard(ratio: Double?, onClick: (() -> Unit)?) {
    MetricCard(
        label = stringResource(Res.string.circumference_ratio_label),
        value = ratio?.let(::ratioText) ?: stringResource(Res.string.circumference_ratio_unavailable),
        modifier = Modifier.testTag("weight_whr_card"),
        supporting = if (ratio == null) {
            {
                Text(
                    text = stringResource(Res.string.circumference_ratio_no_data),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
        } else {
            null
        },
        onClick = if (ratio != null) onClick else null,
        clickLabel = stringResource(Res.string.metric_tile_open_label),
    )
}

@Composable
private fun SiteGroup(
    group: SiteHistory,
    isOnline: Boolean,
    today: LocalDate,
    timeZone: TimeZone,
    onEvent: (CircumferencesEvent) -> Unit,
) {
    val siteLabel = circumferenceSiteLabel(group.site)
    Column(Modifier.fillMaxWidth().testTag("circumference_group_${group.site.name.lowercase()}")) {
        Text(
            text = siteLabel,
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        group.rows.forEach { row -> CircumferenceHistoryRow(row, isOnline, today, timeZone, onEvent) }
    }
}

@Composable
private fun CircumferenceHistoryRow(
    row: BodyCircumference,
    isOnline: Boolean,
    today: LocalDate,
    timeZone: TimeZone,
    onEvent: (CircumferencesEvent) -> Unit,
) {
    val local = row.measuredAt.toLocalDateTime(timeZone)
    val siteLabel = circumferenceSiteLabel(row.site)
    val value = stringResource(Res.string.circumference_value_cm, decimalText(row.valueCm))
    SwipeToDeleteRow(
        deleteLabel = stringResource(Res.string.circumference_row_delete_cd, siteLabel, value),
        enabled = isOnline,
        onDelete = { onEvent(CircumferencesEvent.Delete(row.id)) },
        backgroundTag = "circumference_row_delete_bg_${row.id}",
        modifier = Modifier.testTag("circumference_row_${row.id}"),
        containerColor = AppTheme.colors.surfaceBase,
    ) {
        MeasurementRow(
            value = value,
            supporting = AppDateFormat.list(local.date, today),
            onClick = { onEvent(CircumferencesEvent.OpenEdit(row.id)) },
            clickLabel = stringResource(Res.string.circumference_row_edit_label),
        )
    }
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
