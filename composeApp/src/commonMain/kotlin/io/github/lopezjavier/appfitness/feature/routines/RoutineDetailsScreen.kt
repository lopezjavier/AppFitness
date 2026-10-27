package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.action_edit
import io.github.lopezjavier.appfitness.composeapp.resources.delete_failed
import io.github.lopezjavier.appfitness.composeapp.resources.delete_offline
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_error
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_exercises
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_last_done
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_never_done
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_set_bodyweight
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_set_line
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_sets_reps
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_weight
import io.github.lopezjavier.appfitness.composeapp.resources.routines_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.routines_start
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.workout_already_in_progress
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "routine_details"
private const val SKELETON_ROWS = 4
private val SkeletonRowHeight = Spacing.xl * 2

/**
 * Stateful routine details: connects [RoutineDetailsViewModel] for [routineId], reloads on resume (coming back from
 * the builder) and routes the one-off results: the live screen once a session starts, [onBack] after a delete, and a
 * message plus haptic for refusals and failures. [onEdit] opens the builder.
 */
@Composable
fun RoutineDetailsRoot(
    routineId: String,
    onEdit: () -> Unit,
    onOpenLiveSession: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutineDetailsViewModel = koinViewModel(parameters = { parametersOf(routineId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOpenLive by rememberUpdatedState(onOpenLiveSession)
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    val failed = stringResource(Res.string.routines_action_failed)
    val deleteFailed = stringResource(Res.string.delete_failed)
    val deleteOffline = stringResource(Res.string.delete_offline)
    val inProgress = stringResource(Res.string.workout_already_in_progress)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RoutineDetailsEffect.OpenLiveSession -> currentOpenLive()

                RoutineDetailsEffect.Deleted -> {
                    haptics.confirm()
                    currentOnBack()
                }

                RoutineDetailsEffect.ActionFailed -> {
                    haptics.reject()
                    messages.show(deleteFailed)
                }

                RoutineDetailsEffect.StartFailed -> {
                    haptics.reject()
                    messages.show(failed)
                }

                RoutineDetailsEffect.SessionInProgress -> {
                    haptics.reject()
                    messages.show(inProgress)
                }

                // Starting is disabled offline, so the only refusal that can reach here is the delete.
                RoutineDetailsEffect.OfflineRefused -> {
                    haptics.reject()
                    messages.show(deleteOffline)
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(RoutineDetailsEvent.Refresh) }
    RoutineDetailsScreen(state, viewModel::onEvent, onEdit, onOpenExercise, onBack, modifier)
}

/**
 * Routine details (R-17): the routine name as title, edit and delete in the top bar (delete is immediate, no dialog),
 * the exercises with their planned sets, when it was last done and "Empezar" pinned at the bottom (disabled offline
 * with the hint). Loading shows a skeleton and a failed load offers "Reintentar".
 */
@Composable
@Suppress("LongParameterList") // State, events, navigation hooks and the injectable clock.
fun RoutineDetailsScreen(
    state: RoutineDetailsUiState,
    onEvent: (RoutineDetailsEvent) -> Unit,
    onEdit: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    today: LocalDate = Clock.System.todayIn(timeZone),
) {
    val content = state as? RoutineDetailsUiState.Content
    val editLabel = stringResource(Res.string.action_edit)
    val deleteLabel = stringResource(Res.string.action_delete)
    AppScreen(
        title = content?.name.orEmpty(),
        modifier = modifier,
        onBack = onBack,
        actions = if (content == null) {
            emptyList()
        } else {
            listOf(
                AppTopBarAction(Res.drawable.ic_edit, editLabel, onEdit, testTag = "${TAG}_edit"),
                AppTopBarAction(
                    Res.drawable.ic_delete,
                    deleteLabel,
                    { onEvent(RoutineDetailsEvent.Delete) },
                    testTag = "${TAG}_delete",
                ),
            )
        },
        footer = content?.let { { StartFooter(it, onEvent) } },
        testTagPrefix = TAG,
    ) {
        Box(Modifier.fillMaxSize().testTag("${TAG}_screen")) {
            when (state) {
                RoutineDetailsUiState.Loading -> DetailsLoading()

                RoutineDetailsUiState.Error -> ErrorState(
                    message = stringResource(Res.string.routine_details_error),
                    action = AppStateAction(stringResource(Res.string.state_retry)) {
                        onEvent(RoutineDetailsEvent.Retry)
                    },
                    modifier = Modifier.align(Alignment.Center),
                    testTagPrefix = "${TAG}_error",
                )

                is RoutineDetailsUiState.Content -> DetailsList(state, timeZone, today, onOpenExercise)
            }
        }
    }
}

@Composable
private fun StartFooter(content: RoutineDetailsUiState.Content, onEvent: (RoutineDetailsEvent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PrimaryButton(
            text = stringResource(Res.string.routines_start),
            onClick = { onEvent(RoutineDetailsEvent.Start) },
            modifier = Modifier.fillMaxWidth().testTag("${TAG}_start"),
            enabled = content.isOnline && !content.busy,
        )
        if (!content.isOnline) OfflineHint(action = "empezar")
    }
}

@Composable
private fun DetailsList(
    content: RoutineDetailsUiState.Content,
    timeZone: TimeZone,
    today: LocalDate,
    onOpenExercise: (String) -> Unit,
) {
    val lastDone = content.lastDone
        ?.let { stringResource(Res.string.routine_details_last_done, dateOf(it, timeZone, today)) }
        ?: stringResource(Res.string.routine_details_never_done)
    val mediaUris = LocalExerciseMediaUris.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("${TAG}_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "last_done") {
            Text(
                text = lastDone,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("${TAG}_last_done"),
            )
        }
        item(key = "header") {
            ListSectionHeader(
                title = stringResource(Res.string.routine_details_exercises),
                count = content.exercises.size,
                modifier = Modifier.testTag("${TAG}_exercises_header"),
            )
        }
        items(content.exercises, key = { it.exerciseId }) { exercise ->
            ExerciseRow(
                name = exercise.name ?: exercise.exerciseId,
                supporting = setsSummary(exercise.plannedSets),
                onOpen = { onOpenExercise(exercise.exerciseId) },
                tagPrefix = "${TAG}_exercise_${exercise.exerciseId}",
                imageUri = exercise.imageKey?.let { mediaUris.start(it) },
            )
        }
    }
}

@Composable
private fun DetailsLoading() {
    Column(
        Modifier.fillMaxSize().testTag("${TAG}_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(SKELETON_ROWS) {
            SkeletonBox(
                Modifier.fillMaxWidth().height(SkeletonRowHeight),
                AppShapes.lg,
            )
        }
    }
}

private fun dateOf(instant: Instant, timeZone: TimeZone, today: LocalDate): String =
    AppDateFormat.list(instant.toLocalDateTime(timeZone).date, today)

/**
 * "3 x 8" when every set is the same, with " · 60 kg" when they carry a weight; otherwise one entry per set
 * ("60 kg x 8 · 62,5 kg x 6", "10 reps" without weight), so per-set weights are visible.
 */
@Composable
private fun setsSummary(sets: List<PlannedSet>): String {
    if (sets.isEmpty()) return ""
    val first = sets.first()
    val weightOf = { set: PlannedSet -> set.weightKg?.let { AppNumberFormat.decimal(it) } }
    return if (sets.all { it.weightKg == first.weightKg && it.reps == first.reps }) {
        val base = stringResource(Res.string.routine_details_sets_reps, sets.size, first.reps)
        weightOf(first)?.let { base + " · " + stringResource(Res.string.routine_details_weight, it) } ?: base
    } else {
        sets.map { set ->
            weightOf(set)?.let { stringResource(Res.string.routine_details_set_line, it, set.reps) }
                ?: stringResource(Res.string.routine_details_set_bodyweight, set.reps)
        }.joinToString(" · ")
    }
}
