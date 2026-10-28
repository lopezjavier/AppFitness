package io.github.lopezjavier.appfitness.feature.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_delta_kg
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_delta_same
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_delta_sets
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_delta_workouts
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_energy_group_cd
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_energy_label
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_energy_option_cd
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_form_header
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_notes_counter
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_notes_label
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_notes_too_long
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_records_empty
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_records_header
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_sets_label
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_sets_value
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_summary_caption
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_summary_error
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_summary_header
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_title
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_volume_label
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_volume_value
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_weight_delta_caption
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_weight_label
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_weight_not_enough
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_weight_value
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_workouts_label
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_workouts_value
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.WeekRecord
import io.github.lopezjavier.appfitness.domain.model.WeeklySummary
import io.github.lopezjavier.appfitness.domain.model.WeeklyWeightTrend
import io.github.lopezjavier.appfitness.feature.exercises.valueText
import io.github.lopezjavier.appfitness.feature.workout.label
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

private const val NOTES_MIN_LINES = 4
private const val TENTHS = 10.0

/**
 * The weekly check-in (US-59) on [AppEditorScreen]: the closed week in figures, a slot for the coach (E13, empty) and
 * the energy/notes form above a pinned "Guardar". The summary has its own loading and error states and never blocks
 * the form. The template owns the discard dialog, the offline hint and the Confirm haptic when the editor closes after
 * [CheckInEffect.Saved]; a failed save is a Reject haptic plus a message (R-28, R-37).
 */
@Composable
fun CheckInScreen(
    state: CheckInUiState,
    onEvent: (CheckInEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<CheckInEffect> = emptyFlow(),
) {
    var saved by rememberSaveable { mutableStateOf(false) }
    CheckInEffects(effects) { saved = true }
    val title = stringResource(Res.string.checkin_title)
    if (state !is CheckInUiState.Ready) {
        CheckInPlaceholder(state, onEvent, onClose, title, modifier)
        return
    }
    AppEditorScreen(
        title = title,
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        isOnline = !state.isOffline,
        onSave = { onEvent(CheckInEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        saved = saved,
        canSave = state.energy != null && !state.notesTooLong,
        testTagPrefix = "checkin",
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("checkin_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            SummaryBlock(state, onEvent)
            // Reserved for the coach's weekly read (E13, US-71): no UI yet.
            Box(Modifier.testTag("checkin_coach_slot"))
            FormBlock(state, onEvent)
        }
    }
}

@Composable
private fun SummaryBlock(state: CheckInUiState.Ready, onEvent: (CheckInEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        ListSectionHeader(stringResource(Res.string.checkin_summary_header), Modifier.testTag("checkin_summary_header"))
        if (state.window.day.dayOfWeek != DayOfWeek.SUNDAY) {
            Text(
                text = stringResource(Res.string.checkin_summary_caption),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
        when (val summary = state.summary) {
            CheckInSummaryState.Loading -> Column(
                Modifier.testTag("checkin_summary_loading"),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) { SummarySkeleton() }

            CheckInSummaryState.Failed -> ErrorState(
                message = stringResource(Res.string.checkin_summary_error),
                action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(CheckInEvent.RetrySummary) },
                modifier = Modifier.fillMaxWidth(),
                actionTestTag = "checkin_summary_retry",
                testTagPrefix = "checkin_summary_error",
            )

            is CheckInSummaryState.Loaded -> SummaryCards(summary.summary, state.exerciseNames)
        }
    }
}

@Composable
private fun SummaryCards(summary: WeeklySummary, names: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        val current = summary.training.current
        val previous = summary.training.previous
        val volume = current.volume.roundToInt()
        val volumeDelta = volume - previous.volume.roundToInt()
        SummaryCard(
            label = stringResource(Res.string.checkin_volume_label),
            value = stringResource(Res.string.checkin_volume_value, AppNumberFormat.integer(volume)),
            delta = if (volumeDelta == 0) {
                stringResource(Res.string.checkin_delta_same)
            } else {
                stringResource(Res.string.checkin_delta_kg, AppNumberFormat.signedInteger(volumeDelta))
            },
            tag = "checkin_summary_volume",
        )
        val workoutsDelta = current.workouts - previous.workouts
        SummaryCard(
            label = stringResource(Res.string.checkin_workouts_label),
            value = pluralStringResource(Res.plurals.checkin_workouts_value, current.workouts, current.workouts),
            delta = if (workoutsDelta == 0) {
                stringResource(Res.string.checkin_delta_same)
            } else {
                pluralStringResource(
                    Res.plurals.checkin_delta_workouts,
                    abs(workoutsDelta),
                    AppNumberFormat.signedInteger(workoutsDelta),
                )
            },
            tag = "checkin_summary_workouts",
        )
        val setsDelta = current.sets - previous.sets
        SummaryCard(
            label = stringResource(Res.string.checkin_sets_label),
            value = pluralStringResource(Res.plurals.checkin_sets_value, current.sets, current.sets),
            delta = if (setsDelta == 0) {
                stringResource(Res.string.checkin_delta_same)
            } else {
                pluralStringResource(
                    Res.plurals.checkin_delta_sets,
                    abs(setsDelta),
                    AppNumberFormat.signedInteger(setsDelta),
                )
            },
            tag = "checkin_summary_sets",
        )
        RecordsCard(summary.records, names)
        WeightCard(summary.weightTrend)
    }
}

/** One figure with its difference to the week before as text (sign and words, never colour alone). */
@Composable
private fun SummaryCard(label: String, value: String, delta: String, tag: String) {
    MetricCard(
        label = label,
        value = value,
        modifier = Modifier.testTag(tag).semantics(mergeDescendants = true) {
            contentDescription =
                "$label $value, $delta"
        },
        supporting = { DeltaText(delta) },
    )
}

@Composable
private fun DeltaText(text: String) {
    Text(text = text, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
}

@Composable
private fun WeightCard(trend: WeeklyWeightTrend) {
    val label = stringResource(Res.string.checkin_weight_label)
    when (trend) {
        is WeeklyWeightTrend.Delta -> {
            val tenths = (trend.kgDelta * TENTHS).roundToInt() / TENTHS
            val signed = (if (tenths > 0) "+" else "") + AppNumberFormat.decimal(tenths)
            SummaryCard(
                label = label,
                value = stringResource(Res.string.checkin_weight_value, signed),
                delta = stringResource(Res.string.checkin_weight_delta_caption),
                tag = "checkin_summary_weight",
            )
        }

        WeeklyWeightTrend.NotEnoughData -> MetricCard(
            label = label,
            value = stringResource(Res.string.checkin_weight_not_enough),
            modifier = Modifier.testTag("checkin_summary_weight").semantics(mergeDescendants = true) {},
        )
    }
}

@Composable
private fun RecordsCard(records: List<WeekRecord>, names: Map<String, String>) {
    AppCard(Modifier.fillMaxWidth().testTag("checkin_summary_records")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ListSectionHeader(stringResource(Res.string.checkin_records_header))
            if (records.isEmpty()) {
                Text(
                    text = stringResource(Res.string.checkin_records_empty),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("checkin_summary_records_empty"),
                )
            }
            val unnamed = stringResource(Res.string.exercise_unnamed)
            records.forEachIndexed { index, record ->
                val value = PersonalRecord(record.recordType, record.value, null, record.sessionDate).valueText()
                AppListRow(
                    headline = names[record.exerciseId] ?: unnamed,
                    supporting = "${record.recordType.label()} · $value",
                    modifier = Modifier.testTag("checkin_summary_record_$index").semantics(mergeDescendants = true) {},
                )
            }
        }
    }
}

@Composable
private fun FormBlock(state: CheckInUiState.Ready, onEvent: (CheckInEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        ListSectionHeader(stringResource(Res.string.checkin_form_header))
        val groupCd = stringResource(Res.string.checkin_energy_group_cd)
        Column(
            Modifier.testTag("checkin_energy").semantics { contentDescription = groupCd },
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(Res.string.checkin_energy_label),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            val options = CHECK_IN_ENERGY_RANGE.map { level ->
                AppOption(
                    label = level.toString(),
                    testTag = "checkin_energy_$level",
                    contentDescription = stringResource(Res.string.checkin_energy_option_cd, level),
                )
            }
            SegmentedSelector(
                options = options,
                selectedIndex = state.energy?.let { CHECK_IN_ENERGY_RANGE.indexOf(it) },
                onSelect = { onEvent(CheckInEvent.SelectEnergy(CHECK_IN_ENERGY_RANGE.elementAt(it))) },
                enabled = !state.isSaving,
            )
        }
        AppTextField(
            value = state.notes,
            onValueChange = { onEvent(CheckInEvent.SetNotes(it)) },
            label = stringResource(Res.string.checkin_notes_label),
            maxLength = Int.MAX_VALUE,
            errorText = if (state.notesTooLong) stringResource(Res.string.checkin_notes_too_long) else null,
            enabled = !state.isSaving,
            testTagPrefix = "checkin_notes",
            imeAction = ImeAction.Default,
            singleLine = false,
            minLines = NOTES_MIN_LINES,
        )
        Text(
            text = stringResource(Res.string.checkin_notes_counter, state.notes.length),
            style = AppTheme.typography.bodySmall,
            color = if (state.notesTooLong) AppTheme.colors.error else AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("checkin_notes_counter"),
        )
    }
}
