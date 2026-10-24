package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_body_weight
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_plate_calc
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_rest
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_warm_up_calc
import io.github.lopezjavier.appfitness.composeapp.resources.live_rest_save
import io.github.lopezjavier.appfitness.composeapp.resources.live_rest_sheet_hint
import io.github.lopezjavier.appfitness.composeapp.resources.live_rest_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_note
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.feature.routines.ActionsSheet
import io.github.lopezjavier.appfitness.feature.routines.SheetActionRow
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
 * The exercise's actions sheet: "Descanso", the warm-up calculator
 * (all exercises), the plate calculator (barbell and dumbbell exercises) and "Actualizar peso
 * corporal" (bodyweight and assisted ones; disabled with its reason when Health Connect owns today's weight).
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
    ActionsSheet(
        title = stringResource(Res.string.live_exercise_menu_title),
        onDismiss = onDismiss,
        testTagPrefix = "${prefix}_menu_sheet",
    ) {
        SheetActionRow(
            label = stringResource(Res.string.live_exercise_menu_rest),
            onClick = onRest,
            testTag = "${prefix}_menu_rest",
        )
        SheetActionRow(
            label = stringResource(Res.string.live_exercise_menu_warm_up_calc),
            onClick = onWarmUpCalculator,
            testTag = "${prefix}_menu_warm_up_calc",
        )
        if (exercise.equipment == Equipment.Barbell || exercise.equipment == Equipment.Dumbbell) {
            SheetActionRow(
                label = stringResource(Res.string.live_exercise_menu_plate_calc),
                onClick = onPlateCalculator,
                testTag = "${prefix}_menu_plate_calc",
            )
        }
        if (exercise.metricType in BODY_WEIGHT_TYPES) {
            val note = stringResource(Res.string.weight_hc_note)
            SheetActionRow(
                label = stringResource(Res.string.live_exercise_menu_body_weight),
                onClick = onBodyWeight,
                testTag = "${prefix}_menu_body_weight",
                modifier = if (healthConnectToday) Modifier.semantics { stateDescription = note } else Modifier,
                enabled = !healthConnectToday,
            )
            if (healthConnectToday) {
                Text(
                    text = note,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${prefix}_body_weight_hc_note"),
                )
            }
        }
    }
}

/** Weighted exercises are excluded: they count the added load only, not the body weight. */
private val BODY_WEIGHT_TYPES = setOf(ExerciseMetricType.Bodyweight, ExerciseMetricType.Assisted)

/**
 * Rest of one exercise for this session only: a stepper in 10 s steps within [RoutineBounds] and "Guardar".
 * Nothing is sent until the user confirms.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
internal fun ExerciseRestSheet(
    position: Int,
    currentSeconds: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var seconds by remember { mutableIntStateOf(currentSeconds) }
    val tag = "live_exercise_${position}_rest"
    AppBottomSheet(onDismissRequest = onDismiss, testTagPrefix = "${tag}_sheet") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = WindowWidthClassDefaults.CardColumnMaxWidth)
                    .padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.live_rest_sheet_title),
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
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
                PrimaryButton(
                    text = stringResource(Res.string.live_rest_save),
                    onClick = { onSave(seconds) },
                    modifier = Modifier.fillMaxWidth().testTag("${tag}_confirm"),
                )
            }
        }
    }
}

private const val MIN = RoutineBounds.REST_MIN_SECONDS
private const val MAX = RoutineBounds.REST_MAX_SECONDS
