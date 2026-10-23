package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_animation_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_link
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_record_value_kg
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_record_value_km
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_record_value_m
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_record_value_reps
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_records_empty
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_records_error
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_records_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseAnimation
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TextPill
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.usecase.GhostData
import io.github.lopezjavier.appfitness.domain.usecase.SetEntryText
import io.github.lopezjavier.appfitness.feature.workout.formatElapsed
import io.github.lopezjavier.appfitness.feature.workout.label
import io.github.lopezjavier.appfitness.feature.workout.sessionDateLabel
import io.github.lopezjavier.appfitness.feature.workout.text
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val TENTHS = 10.0
private const val METRES_PER_KM = 1000.0

/**
 * "Resumen" tab (US-28): the demonstration, header, progress link and personal records. On [wide] windows the media
 * sits left of the text blocks.
 */
@Suppress("LongParameterList") // The tab renders three independent blocks.
@Composable
internal fun ExerciseSummaryTab(
    detail: ExerciseDetail,
    records: RecordsState,
    today: LocalDate,
    wide: Boolean,
    onOpenProgress: () -> Unit,
    onRetryRecords: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val name = detail.name.ifBlank { stringResource(Res.string.exercise_unnamed) }
    Column(
        modifier
            .widthIn(max = ExerciseLayoutDefaults.ContentMaxWidth)
            .verticalScroll(scrollState)
            .padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                Box(Modifier.weight(1f)) { DetailMedia(detail, name) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                    SummaryText(detail, name, records, today, onOpenProgress, onRetryRecords)
                }
            }
        } else {
            DetailMedia(detail, name)
            SummaryText(detail, name, records, today, onOpenProgress, onRetryRecords)
        }
    }
}

@Suppress("LongParameterList") // Mirrors the tab parameters.
@Composable
private fun SummaryText(
    detail: ExerciseDetail,
    name: String,
    records: RecordsState,
    today: LocalDate,
    onOpenProgress: () -> Unit,
    onRetryRecords: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
        DetailHeader(detail, name)
        SecondaryButton(
            text = stringResource(Res.string.exercise_progress_link),
            onClick = onOpenProgress,
            modifier = Modifier.fillMaxWidth().testTag("exercise_detail_progress_link"),
        )
        RecordsBlock(records, today, onRetryRecords)
    }
}

@Composable
private fun DetailMedia(detail: ExerciseDetail, name: String) {
    val key = detail.exercise.key
    val info = detail.info
    val uris = LocalExerciseMediaUris.current
    val startUri = if (key != null && info != null) uris.start(key) else null
    if (key != null && info != null && startUri != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            ExerciseAnimation(
                startUri = startUri,
                peakUri = if (info.hasPeak) uris.peak(key) else null,
                animationsEnabled = LocalAnimationsEnabled.current,
                testTagPrefix = "exercise_detail",
                contentDescription = stringResource(Res.string.exercise_detail_animation_cd, name),
                modifier = Modifier.widthIn(max = ExerciseLayoutDefaults.AnimationMaxWidth),
            )
        }
    }
}

@Composable
private fun DetailHeader(detail: ExerciseDetail, name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = name,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextPill(stringResource(detail.exercise.muscleGroup.label))
            TextPill(stringResource(detail.exercise.equipment.label))
        }
    }
}

@Composable
private fun RecordsBlock(records: RecordsState, today: LocalDate, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().testTag("exercise_detail_records"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(Res.string.exercise_records_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        when (records) {
            RecordsState.Loading -> SkeletonBox(Modifier.fillMaxWidth().padding(Spacing.xl))

            RecordsState.Empty -> Text(
                text = stringResource(Res.string.exercise_records_empty),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("exercise_detail_records_empty"),
            )

            RecordsState.Error -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(Res.string.exercise_records_error),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
                GhostButton(
                    text = stringResource(Res.string.state_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag("exercise_detail_records_retry"),
                )
            }

            is RecordsState.Content -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                records.records.forEach { RecordCard(it, today) }
            }
        }
    }
}

@Composable
private fun RecordCard(record: PersonalRecord, today: LocalDate) {
    val date = record.date?.let { sessionDateLabel(it, today).text() }
    AppCard(
        Modifier.fillMaxWidth().testTag("exercise_detail_record_${record.type.key}")
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(record.type.label(), style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                if (date != null) {
                    Text(date, style = AppTheme.typography.labelTechnical, color = AppTheme.colors.textSecondary)
                }
            }
            Text(
                text = record.valueText(),
                style = AppTheme.typography.headlineMetric,
                color = AppTheme.colors.athleticCyan,
            )
        }
    }
}

/** The record value with its unit: kg (one decimal at most), mm:ss, m or km, or a whole count. */
@Composable
private fun PersonalRecord.valueText(): String = when (type) {
    PersonalRecordType.HeaviestLift,
    PersonalRecordType.BestSet,
    PersonalRecordType.Best1Rm,
    -> stringResource(
        Res.string.exercise_record_value_kg,
        GhostData.weightText((value * TENTHS).roundToLong() / TENTHS),
    )

    PersonalRecordType.MaxVolume ->
        stringResource(Res.string.exercise_record_value_kg, AppNumberFormat.integer(value.roundToInt()))

    PersonalRecordType.BestDuration -> formatElapsed(value.toLong())

    PersonalRecordType.BestDistance ->
        if (value < METRES_PER_KM) {
            stringResource(Res.string.exercise_record_value_m, AppNumberFormat.integer(value.roundToInt()))
        } else {
            stringResource(Res.string.exercise_record_value_km, SetEntryText.kmText(value))
        }

    PersonalRecordType.TotalReps ->
        stringResource(Res.string.exercise_record_value_reps, AppNumberFormat.integer(value.roundToInt()))

    PersonalRecordType.MostSteps,
    PersonalRecordType.MostFloors,
    -> AppNumberFormat.integer(value.roundToInt())
}
