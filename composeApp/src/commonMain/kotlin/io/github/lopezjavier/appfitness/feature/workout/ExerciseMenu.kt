package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_label
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_up
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_body_weight
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_plate_calc
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_rest
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_warm_up_calc
import io.github.lopezjavier.appfitness.composeapp.resources.live_rest_sheet_hint
import io.github.lopezjavier.appfitness.composeapp.resources.live_rest_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_note
import io.github.lopezjavier.appfitness.designsystem.AppActionSheet
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppSheetAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Overflow button of an exercise card (48 dp target); tagged `live_exercise_<position>_menu`. */
@Composable
internal fun ExerciseMenuButton(
    exerciseName: String,
    position: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.live_exercise_menu_cd, exerciseName)
    Box(
        modifier
            .size(AppTopBarDefaults.NavigationTouchTarget)
            .clip(AppShapes.full)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("live_exercise_${position}_menu"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_more_vert),
            contentDescription = null,
            tint = AppTheme.colors.textPrimary,
            modifier = Modifier.size(AppTopBarDefaults.NavigationIconSize),
        )
    }
}

/**
 * The exercise's actions sheet (action archetype): "Descanso", the warm-up calculator (all exercises), the plate
 * calculator (barbell and dumbbell exercises) and "Actualizar peso corporal" (bodyweight and assisted ones; disabled
 * with its reason as the hint when Health Connect owns today's weight).
 */
@Composable
@Suppress("LongParameterList") // One callback per action.
internal fun ExerciseMenuSheet(
    position: Int,
    exercise: LiveExercise,
    healthConnectToday: Boolean,
    onRest: () -> Unit,
    onWarmUpCalculator: () -> Unit,
    onPlateCalculator: () -> Unit,
    onBodyWeight: () -> Unit,
    onDismiss: () -> Unit,
) {
    val prefix = "live_exercise_$position"
    val note = stringResource(Res.string.weight_hc_note)
    val actions = buildList {
        add(
            AppSheetAction(
                Res.drawable.ic_edit,
                stringResource(Res.string.live_exercise_menu_rest),
                onClick = onRest,
                testTag = "${prefix}_menu_rest",
            ),
        )
        add(
            AppSheetAction(
                Res.drawable.ic_trending_up,
                stringResource(Res.string.live_exercise_menu_warm_up_calc),
                onClick = onWarmUpCalculator,
                testTag = "${prefix}_menu_warm_up_calc",
            ),
        )
        if (exercise.equipment == Equipment.Barbell || exercise.equipment == Equipment.Dumbbell) {
            add(
                AppSheetAction(
                    Res.drawable.ic_label,
                    stringResource(Res.string.live_exercise_menu_plate_calc),
                    onClick = onPlateCalculator,
                    testTag = "${prefix}_menu_plate_calc",
                ),
            )
        }
        if (exercise.metricType in BODY_WEIGHT_TYPES) {
            add(
                AppSheetAction(
                    Res.drawable.ic_edit,
                    stringResource(Res.string.live_exercise_menu_body_weight),
                    hint = note.takeIf { healthConnectToday },
                    onClick = onBodyWeight,
                    testTag = "${prefix}_menu_body_weight",
                    enabled = !healthConnectToday,
                ),
            )
        }
    }
    AppActionSheet(
        actions = actions,
        onDismiss = onDismiss,
        title = stringResource(Res.string.live_exercise_menu_title),
        testTagPrefix = "${prefix}_menu_sheet",
    )
}

/** Weighted exercises are excluded: they count the added load only, not the body weight. */
private val BODY_WEIGHT_TYPES = setOf(ExerciseMetricType.Bodyweight, ExerciseMetricType.Assisted)

/**
 * Rest of one exercise for this session only (edit archetype): a stepper in 10 s steps within [RoutineBounds] and
 * "Guardar". Nothing is sent until the user confirms; the typed value survives recreation.
 */
@Composable
internal fun ExerciseRestSheet(position: Int, currentSeconds: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var seconds by rememberSaveable { mutableIntStateOf(currentSeconds) }
    val tag = "live_exercise_${position}_rest"
    AppEditSheet(
        title = stringResource(Res.string.live_rest_sheet_title),
        commit = SheetCommit.Save,
        onCommit = { onSave(seconds) },
        onDismiss = onDismiss,
        testTagPrefix = "${tag}_sheet",
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NumberStepper(
                valueText = restDurationText(seconds),
                onDecrement = { seconds = (seconds - RoutineBounds.REST_STEP_SECONDS).coerceAtLeast(MIN) },
                onIncrement = { seconds = (seconds + RoutineBounds.REST_STEP_SECONDS).coerceAtMost(MAX) },
                decrementEnabled = seconds > MIN,
                incrementEnabled = seconds < MAX,
                decrementContentDescription = stringResource(Res.string.builder_rest_decrease_cd),
                incrementContentDescription = stringResource(Res.string.builder_rest_increase_cd),
                stateDescription = restStateDescription(seconds),
                testTagPrefix = tag,
            )
            Text(
                text = stringResource(Res.string.live_rest_sheet_hint),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

private const val MIN = RoutineBounds.REST_MIN_SECONDS
private const val MAX = RoutineBounds.REST_MAX_SECONDS
