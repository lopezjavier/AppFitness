package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_exercise_open_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_superset_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.live_object_set
import io.github.lopezjavier.appfitness.composeapp.resources.live_suggestion
import io.github.lopezjavier.appfitness.composeapp.resources.live_suggestion_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_suggestion_open_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_target_rpe
import io.github.lopezjavier.appfitness.composeapp.resources.note_edit
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseStatusChevron
import io.github.lopezjavier.appfitness.designsystem.ExerciseStatusInfoMarker
import io.github.lopezjavier.appfitness.designsystem.ExerciseStatusRow
import io.github.lopezjavier.appfitness.designsystem.SetFieldSpec
import io.github.lopezjavier.appfitness.designsystem.SetTableHeader
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.exerciseNoteSummary
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.feature.exercises.setFieldSpecs
import io.github.lopezjavier.appfitness.feature.weight.decimalText
import org.jetbrains.compose.resources.stringResource

/**
 * One exercise of the live session: a heading (superset label and name), the set table and "Añadir serie" at the
 * foot. The heading is the node tagged `live_exercise_<position>`.
 */
@Composable
internal fun LiveExerciseCard(
    exercise: LiveExercise,
    onEvent: (LiveSessionEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    options: VolumeOptionsUi = VolumeOptionsUi(),
    deleteEnabled: Boolean = true,
    online: Boolean = true,
    discomfortSaving: Boolean = false,
    discomfortLogs: Int = 0,
) {
    val position = exercise.position
    val name = exercise.name ?: stringResource(Res.string.exercise_unnamed)
    var sheet by rememberSaveable { mutableStateOf(ExerciseSheet.None) }
    OnDiscomfortStored(discomfortLogs) { if (sheet == ExerciseSheet.Discomfort) sheet = ExerciseSheet.None }
    AppCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseHeading(exercise, name, onOpenExercise, Modifier.weight(1f))
                ExerciseMenuButton(name, position, onClick = { sheet = ExerciseSheet.Menu })
            }
            exercise.targetRpeText?.let {
                Text(
                    text = stringResource(Res.string.live_target_rpe, it),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("live_exercise_${position}_target_rpe"),
                )
            }
            ExerciseStatusRows(exercise, online, onEvent)
            val fields = setFieldSpecs(exercise.metricType, exercise.equipment)
            LiveSetTableHeader(fields)
            exercise.sets.forEach {
                SessionSetRow(
                    position,
                    it,
                    onEvent,
                    metricType = exercise.metricType,
                    equipment = exercise.equipment,
                    deleteEnabled = deleteEnabled,
                )
            }
            AddItemButton(
                objectLabel = stringResource(Res.string.live_object_set),
                onClick = { onEvent(LiveSessionEvent.AddSet(position)) },
                modifier = Modifier.testTag("live_exercise_${position}_add_set"),
            )
        }
    }
    ExerciseSheets(
        sheet,
        exercise,
        options,
        onEvent,
        online,
        discomfortSaving,
        onSheetChange = { sheet = it },
        onSheetClose = { from -> if (sheet == from) sheet = ExerciseSheet.None },
    )
}

/**
 * Discomfort, routine note and suggestion as status rows (R-51), in that order; nothing is drawn (no empty gap)
 * when the exercise has none of them.
 */
@Composable
private fun ExerciseStatusRows(exercise: LiveExercise, online: Boolean, onEvent: (LiveSessionEvent) -> Unit) {
    val position = exercise.position
    val noteSummary = exerciseNoteSummary(exercise.routineNoteKind, exercise.routineNote)
    if (exercise.discomfort == null && noteSummary == null && exercise.suggestion == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        exercise.discomfort?.let {
            DiscomfortBlock(position, it, online, onResolve = { onEvent(LiveSessionEvent.ResolveDiscomfort(position)) })
        }
        noteSummary?.let {
            ExerciseStatusRow(
                text = it,
                marker = { ExerciseStatusInfoMarker() },
                testTag = "live_exercise_note_$position",
                onClick = {
                    onEvent(LiveSessionEvent.OpenRoutineNote(position))
                }.takeIf { exercise.routineNoteEditable },
                onClickLabel = stringResource(Res.string.note_edit),
            )
        }
        exercise.suggestion?.let { SuggestionRow(position, it, onEvent) }
    }
}

/** "Sugerencia: 82,5 kg × 6" (US-70): the whole row opens the reason sheet. */
@Composable
private fun SuggestionRow(position: Int, suggestion: ProgressionSuggestion, onEvent: (LiveSessionEvent) -> Unit) {
    val weight = AppNumberFormat.decimal(suggestion.weightKg, maxDecimals = 2)
    val reps = AppNumberFormat.integer(suggestion.goalReps)
    ExerciseStatusRow(
        text = stringResource(Res.string.live_suggestion, weight, reps),
        marker = { ExerciseStatusInfoMarker() },
        testTag = "live_exercise_${position}_suggestion",
        onClick = { onEvent(LiveSessionEvent.OpenProgressionHelp(position)) },
        onClickLabel = stringResource(Res.string.live_suggestion_open_label),
        trailing = { ExerciseStatusChevron() },
        contentDescription = stringResource(Res.string.live_suggestion_cd, weight, reps),
    )
}

@Composable
private fun LiveSetTableHeader(fields: List<SetFieldSpec>) {
    SetTableHeader(
        showRpe = true,
        fields = fields,
        showNote = true,
        showPrevious = false,
        testTagPrefix = "live_set_header",
    )
}

/** Runs [onStore] when [logs] changes (a discomfort was stored); a failed log leaves the sheet open with its draft. */
@Composable
private fun OnDiscomfortStored(logs: Int, onStore: () -> Unit) {
    var seen by remember { mutableIntStateOf(logs) }
    val current by rememberUpdatedState(onStore)
    LaunchedEffect(logs) {
        if (logs != seen) {
            seen = logs
            current()
        }
    }
}

private enum class ExerciseSheet { None, Menu, Rest, BodyWeight, Discomfort }

/** The heading is one tap target (min 48 dp) that opens the exercise; the menu button stays a sibling. */
@Composable
private fun ExerciseHeading(
    exercise: LiveExercise,
    name: String,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = exercise.position
    Box(
        modifier.heightIn(min = Spacing.minTouchTarget)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(Res.string.builder_exercise_open_cd, name),
                onClick = { onOpenExercise(exercise.exerciseId) },
            )
            .testTag("live_exercise_${position}_open"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.semantics(mergeDescendants = true) { heading() }.testTag("live_exercise_$position")) {
            exercise.supersetLabel?.let {
                Text(
                    text = stringResource(Res.string.builder_superset_label, it.letter.toString(), it.number),
                    style = AppTheme.typography.labelTechnical,
                    color = AppTheme.colors.athleticCyan,
                )
            }
            Text(text = name, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
        }
    }
}

/**
 * The open sheet of the card (menu, rest or body weight); [onSheetChange] moves between them, [onSheetClose] closes
 * the given one only if it is still open.
 */
@Composable
@Suppress("LongParameterList") // Sheet state, its two transitions and the card data.
private fun ExerciseSheets(
    sheet: ExerciseSheet,
    exercise: LiveExercise,
    options: VolumeOptionsUi,
    onEvent: (LiveSessionEvent) -> Unit,
    online: Boolean,
    discomfortSaving: Boolean,
    onSheetChange: (ExerciseSheet) -> Unit,
    onSheetClose: (ExerciseSheet) -> Unit,
) {
    val position = exercise.position
    when (sheet) {
        ExerciseSheet.None -> Unit

        ExerciseSheet.Menu -> ExerciseMenuSheet(
            position = position,
            exercise = exercise,
            healthConnectToday = options.healthConnectToday,
            onRest = { onSheetChange(ExerciseSheet.Rest) },
            onWarmUpCalculator = {
                onEvent(LiveSessionEvent.OpenCalculator(position, CalculatorKind.WarmUp))
                onSheetChange(ExerciseSheet.None)
            },
            onPlateCalculator = {
                onEvent(LiveSessionEvent.OpenCalculator(position, CalculatorKind.Discs))
                onSheetChange(ExerciseSheet.None)
            },
            onBodyWeight = {
                onEvent(LiveSessionEvent.BodyWeightDraftChanged(options.bodyWeightKg?.let(::decimalText).orEmpty()))
                onSheetChange(ExerciseSheet.BodyWeight)
            },
            online = online,
            onDiscomfort = { onSheetChange(ExerciseSheet.Discomfort) },
            onRoutineNote = if (exercise.routineNoteEditable) {
                { onEvent(LiveSessionEvent.OpenRoutineNote(position)) }
            } else {
                null
            },
            // The sheet closes after the tapped action ran: only close it if the action did not open another one.
            onDismiss = { onSheetClose(ExerciseSheet.Menu) },
        )

        ExerciseSheet.BodyWeight -> BodyWeightSheet(
            position = position,
            options = options,
            onEvent = onEvent,
            onClose = { onSheetChange(ExerciseSheet.None) },
        )

        ExerciseSheet.Discomfort -> DiscomfortSheet(
            position = position,
            online = online,
            saving = discomfortSaving,
            onSave = { level, note -> onEvent(LiveSessionEvent.LogDiscomfort(position, level, note)) },
            onDismiss = { onSheetChange(ExerciseSheet.None) },
        )

        ExerciseSheet.Rest -> ExerciseRestSheet(
            position = position,
            currentSeconds = exercise.restSeconds,
            onSave = {
                onEvent(LiveSessionEvent.SetExerciseRest(position, it))
                onSheetChange(ExerciseSheet.None)
            },
            onDismiss = { onSheetChange(ExerciseSheet.None) },
        )
    }
}
