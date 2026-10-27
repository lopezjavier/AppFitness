package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_add_exercise
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_description
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_duration
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_gym
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_name
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_name_error
import io.github.lopezjavier.appfitness.composeapp.resources.workout_form_no_exercises
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.RpePickerSheet
import io.github.lopezjavier.appfitness.designsystem.SetTypePickerSheet
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraftBounds
import io.github.lopezjavier.appfitness.domain.usecase.WorkoutDraftIssue
import org.jetbrains.compose.resources.stringResource

private const val NO_PICKER = -1
private const val SETS_PER_SLOT = 100

private fun slot(index: Int, setIndex: Int): Int = index * SETS_PER_SLOT + setIndex

/**
 * The fields shared by the edit and the past-workout screens (US-37), in this order: name, [dateRow], duration, gym,
 * description, the exercise cards and "Añadir ejercicio". Edits go out as [WorkoutFormEvent]s; every tag starts with
 * [tagPrefix]. Invalid fields are flagged once [showIssues] (the user edited the form, R-27). The open set-type
 * and RPE pickers survive recreation (ADR-40).
 */
@Composable
@Suppress("LongMethod", "LongParameterList") // The fields of the form in order, one block each.
internal fun WorkoutFormBody(
    view: WorkoutFormView,
    tagPrefix: String,
    onEvent: (WorkoutFormEvent) -> Unit,
    onAddExercise: () -> Unit,
    showIssues: Boolean,
    modifier: Modifier = Modifier,
    dateRow: @Composable () -> Unit,
) {
    val form = view.form
    var typePicker by rememberSaveable { mutableIntStateOf(NO_PICKER) }
    var rpePicker by rememberSaveable { mutableIntStateOf(NO_PICKER) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        AppTextField(
            value = form.name,
            onValueChange = { onEvent(WorkoutFormEvent.NameChanged(it)) },
            label = stringResource(Res.string.workout_form_name),
            maxLength = WorkoutDraftBounds.NAME_MAX_LENGTH,
            errorText = if (showIssues && WorkoutDraftIssue.Name in view.issues) {
                stringResource(Res.string.workout_form_name_error)
            } else {
                null
            },
            testTagPrefix = "${tagPrefix}_name",
        )
        dateRow()
        NumberInput(
            value = form.duration,
            onValueChange = { onEvent(WorkoutFormEvent.DurationChanged(it)) },
            label = stringResource(Res.string.workout_form_duration),
            modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_duration"),
            maxIntegerDigits = WorkoutFormLimits.MINUTES_MAX_CHARS,
        )
        AppTextField(
            value = form.gym,
            onValueChange = { onEvent(WorkoutFormEvent.GymChanged(it)) },
            label = stringResource(Res.string.workout_form_gym),
            maxLength = WorkoutDraftBounds.GYM_MAX_LENGTH,
            testTagPrefix = "${tagPrefix}_gym",
        )
        AppTextField(
            value = form.description,
            onValueChange = { onEvent(WorkoutFormEvent.DescriptionChanged(it)) },
            label = stringResource(Res.string.workout_form_description),
            maxLength = WorkoutDraftBounds.DESCRIPTION_MAX_LENGTH,
            testTagPrefix = "${tagPrefix}_description",
        )
        if (form.exercises.isEmpty()) {
            Text(
                text = stringResource(Res.string.workout_form_no_exercises),
                style = AppTheme.typography.bodyMedium,
                color = if (showIssues) AppTheme.colors.error else AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("${tagPrefix}_empty_hint"),
            )
        }
        form.exercises.forEachIndexed { index, exercise ->
            val flagged = if (showIssues) {
                view.issues.filterIsInstance<WorkoutDraftIssue.SetValue>()
                    .filter { it.exercise == index }.map { it.set }.toSet()
            } else {
                emptySet()
            }
            FormExerciseCard(
                exercise = exercise,
                name = view.names[exercise.exerciseId],
                card = FormCardState(index, index == 0, index == form.exercises.lastIndex, flagged),
                tagPrefix = tagPrefix,
                onEvent = onEvent,
                onOpenTypePicker = { typePicker = slot(index, it) },
                onOpenRpePicker = { rpePicker = slot(index, it) },
            )
        }
        GhostButton(
            text = stringResource(Res.string.builder_add_exercise),
            onClick = onAddExercise,
            modifier = Modifier.fillMaxWidth().testTag("${tagPrefix}_add_exercise"),
        )
    }
    FormPickers(form, typePicker, rpePicker, onEvent, { typePicker = NO_PICKER }, { rpePicker = NO_PICKER })
}

/** The set-type and RPE pickers, whichever is open; a target that no longer exists shows nothing. */
@Composable
@Suppress("LongParameterList") // The form, both picker targets and their hooks.
private fun FormPickers(
    form: WorkoutForm,
    typeSlot: Int,
    rpeSlot: Int,
    onEvent: (WorkoutFormEvent) -> Unit,
    closeType: () -> Unit,
    closeRpe: () -> Unit,
) {
    form.exercises.getOrNull(typeSlot / SETS_PER_SLOT)?.sets?.getOrNull(typeSlot % SETS_PER_SLOT)?.takeIf {
        typeSlot >= 0
    }?.let { set ->
        SetTypePickerSheet(
            current = set.type,
            onSelect = {
                onEvent(WorkoutFormEvent.TypeChanged(typeSlot / SETS_PER_SLOT, typeSlot % SETS_PER_SLOT, it))
                closeType()
            },
            onDismiss = closeType,
        )
    }
    form.exercises.getOrNull(rpeSlot / SETS_PER_SLOT)?.sets?.getOrNull(rpeSlot % SETS_PER_SLOT)?.takeIf {
        rpeSlot >= 0
    }?.let { set ->
        RpePickerSheet(
            current = set.rpe,
            setNumber = rpeSlot % SETS_PER_SLOT + 1,
            // Picking the value the set already has clears it.
            onSelect = {
                val value = if (it == set.rpe) null else it
                onEvent(WorkoutFormEvent.RpeChanged(rpeSlot / SETS_PER_SLOT, rpeSlot % SETS_PER_SLOT, value))
                closeRpe()
            },
            onDismiss = closeRpe,
        )
    }
}
