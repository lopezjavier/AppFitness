@file:Suppress("MatchingDeclarationName") // The file holds the dialog and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Shape, tags and date conversions of [AppDatePickerDialog]. */
object AppDatePickerDialogDefaults {
    val Shape = AppShapes.lg

    fun confirmTag(prefix: String): String = "${prefix}_confirm"

    fun dismissTag(prefix: String): String = "${prefix}_dismiss"

    /** The picker works in UTC milliseconds of the start of the day. */
    fun toMillis(date: LocalDate): Long = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    fun toDate(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

    /** Days after [maxDate] cannot be selected; a null [maxDate] allows every day. */
    @OptIn(ExperimentalMaterial3Api::class)
    fun selectableDates(maxDate: LocalDate?): SelectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
            maxDate == null || toDate(utcTimeMillis) <= maxDate

        override fun isSelectableYear(year: Int): Boolean = maxDate == null || year <= maxDate.year
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    internal fun colors(): DatePickerColors {
        val colors = AppTheme.colors
        return DatePickerDefaults.colors(
            containerColor = colors.surfaceOverlay,
            titleContentColor = colors.textSecondary,
            headlineContentColor = colors.textPrimary,
            weekdayContentColor = colors.textSecondary,
            subheadContentColor = colors.textSecondary,
            navigationContentColor = colors.textPrimary,
            yearContentColor = colors.textPrimary,
            disabledYearContentColor = colors.ghostData,
            currentYearContentColor = colors.athleticCyan,
            selectedYearContentColor = colors.onNeonLime,
            selectedYearContainerColor = colors.neonLime,
            dayContentColor = colors.textPrimary,
            disabledDayContentColor = colors.ghostData,
            selectedDayContentColor = colors.onNeonLime,
            selectedDayContainerColor = colors.neonLime,
            todayContentColor = colors.athleticCyan,
            todayDateBorderColor = colors.athleticCyan,
            dividerColor = colors.divider,
        )
    }
}

/**
 * Date picker dialog (US-37): the platform calendar in the app's colours. It only reports the picked day: the screen
 * that opened it keeps the date in its draft and saves with its own button. [maxDate] is the latest selectable day (a
 * past-only date field passes today). The root is tagged [testTagPrefix], with `<prefix>_confirm` and
 * `<prefix>_dismiss` buttons; "Aceptar" stays disabled until a day is selected. Back and a scrim tap dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Labels and callbacks of a dialog.
@Composable
fun AppDatePickerDialog(
    selected: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    maxDate: LocalDate? = null,
    testTagPrefix: String = "date_picker",
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selected?.let(AppDatePickerDialogDefaults::toMillis),
        selectableDates = AppDatePickerDialogDefaults.selectableDates(maxDate),
    )
    val picked = state.selectedDateMillis?.let(AppDatePickerDialogDefaults::toDate)
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .semantics { testTagsAsResourceId = true }
                .testTag(testTagPrefix),
            shape = AppDatePickerDialogDefaults.Shape,
            color = AppTheme.colors.surfaceOverlay,
            border = BorderStroke(AppBorder.thin, AppTheme.colors.borderStrong),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.md)) {
                DatePicker(
                    state = state,
                    showModeToggle = false,
                    colors = AppDatePickerDialogDefaults.colors(),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                ) {
                    GhostButton(
                        text = dismissLabel,
                        onClick = onDismiss,
                        modifier = Modifier.testTag(AppDatePickerDialogDefaults.dismissTag(testTagPrefix)),
                        tone = GhostTone.Neutral,
                    )
                    GhostButton(
                        text = confirmLabel,
                        onClick = { picked?.let(onConfirm) },
                        modifier = Modifier.testTag(AppDatePickerDialogDefaults.confirmTag(testTagPrefix)),
                        enabled = picked != null,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AppDatePickerDialogPreview() {
    AppTheme {
        AppDatePickerDialog(
            selected = LocalDate(2026, 10, 1),
            onConfirm = {},
            onDismiss = {},
            confirmLabel = "Aceptar",
            dismissLabel = "Cancelar",
            maxDate = LocalDate(2026, 10, 20),
        )
    }
}
