@file:Suppress("TooManyFunctions") // The screen, its blocks and the set summaries.

package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.delete_failed
import io.github.lopezjavier.appfitness.composeapp.resources.delete_offline
import io.github.lopezjavier.appfitness.composeapp.resources.ic_bar_chart
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_details_exercises
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_details_sets
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_error
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_exercises
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_last_done
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_never_done
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_sets_minutes
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_sets_reps
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_title_fallback
import io.github.lopezjavier.appfitness.composeapp.resources.routine_details_weight
import io.github.lopezjavier.appfitness.composeapp.resources.routines_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.routines_start
import io.github.lopezjavier.appfitness.composeapp.resources.workout_already_in_progress
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.HeroStat
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
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
    val fallbackTitle = stringResource(Res.string.routine_details_title_fallback)
    val routineTitle = if (content != null) content.name else fallbackTitle
    AppScreen(
        title = routineTitle,
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
                    action = AppStateAction(stringResource(Res.string.action_retry)) {
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
            DetailsSummary(content, lastDone)
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
                name = exercise.name,
                supporting = setsSummary(exercise.plannedSets),
                supportingMaxLines = 1,
                onOpen = { onOpenExercise(exercise.exerciseId) },
                tagPrefix = "${TAG}_exercise_${exercise.exerciseId}",
                modifier = appAnimateItem(),
                imageUri = exercise.imageKey?.let { mediaUris.start(it) },
            )
        }
    }
}

/** Hero of the details: exercises and planned sets as figures and when the routine was last done as a pill. */
@Composable
private fun DetailsSummary(content: RoutineDetailsUiState.Content, lastDone: String) {
    val sets = content.exercises.sumOf { it.plannedSets.size }
    AppCard(Modifier.fillMaxWidth().testTag("${TAG}_summary"), glow = AppTheme.colors.athleticCyan) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HeroStat(
                    icon = Res.drawable.ic_fitness_center,
                    value = AppNumberFormat.integer(content.exercises.size),
                    label = stringResource(Res.string.px_routines_details_exercises),
                    accent = AppTheme.colors.neonLime,
                    modifier = Modifier.weight(1f),
                )
                HeroStat(
                    icon = Res.drawable.ic_bar_chart,
                    value = AppNumberFormat.integer(sets),
                    label = stringResource(Res.string.px_routines_details_sets),
                    accent = AppTheme.colors.athleticCyan,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                IconBadge(
                    icon = Res.drawable.ic_calendar,
                    tint = AppTheme.colors.energyAmber,
                    size = IconBadgeDefaults.SmallSize,
                    iconSize = IconBadgeDefaults.SmallIconSize,
                )
                Text(
                    text = lastDone,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${TAG}_last_done"),
                )
            }
        }
    }
}

@Composable
private fun DetailsLoading() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
            .testTag("${TAG}_loading"),
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
 * One grammar (C-79, V-27): consecutive equal sets are grouped as "n × reps" with " · 60 kg" when they carry a
 * weight, groups joined by ", " ("1 × 10 · 40 kg, 3 × 8 · 60 kg"). A cardio set is planned in time: "2 × 1 min".
 */
@Composable
private fun setsSummary(sets: List<PlannedSet>): String = // A cardio set is planned in time, never "0 reps".
    if (sets.isNotEmpty() && sets.all { it.durationS != null }) minutesSummary(sets) else repsSummary(sets)

@Composable
private fun minutesSummary(sets: List<PlannedSet>): String =
    sets.groupConsecutiveBy { it.durationS }.map { (set, count) ->
        stringResource(
            Res.string.routine_details_sets_minutes,
            count,
            (set.durationS ?: 0) / RoutineBounds.SECONDS_PER_MINUTE,
        )
    }.joinToString(", ")

@Composable
private fun repsSummary(sets: List<PlannedSet>): String =
    sets.groupConsecutiveBy { it.weightKg to it.reps }.map { (set, count) ->
        val base = stringResource(Res.string.routine_details_sets_reps, count, set.reps)
        set.weightKg?.let {
            base + " · " +
                stringResource(Res.string.routine_details_weight, AppNumberFormat.decimal(it, maxDecimals = 2))
        } ?: base
    }.joinToString(", ")

/** Runs of consecutive sets with the same [key], each as its first set and the run length. */
private fun <K> List<PlannedSet>.groupConsecutiveBy(key: (PlannedSet) -> K): List<Pair<PlannedSet, Int>> {
    val groups = mutableListOf<Pair<PlannedSet, Int>>()
    for (set in this) {
        val last = groups.lastOrNull()
        if (last != null && key(last.first) == key(set)) {
            groups[groups.lastIndex] = last.first to last.second + 1
        } else {
            groups += set to 1
        }
    }
    return groups
}
