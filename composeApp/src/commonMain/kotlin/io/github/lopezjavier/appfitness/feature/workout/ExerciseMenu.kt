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
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_state
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_rest
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_rest_save
import io.github.lopezjavier.appfitness.composeapp.resources.live_rest_sheet_hint
import io.github.lopezjavier.appfitness.composeapp.resources.live_rest_sheet_title
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
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

/** The exercise's actions sheet: today only "Descanso", which [onRest] opens the rest sheet for. */
@Composable
internal fun ExerciseMenuSheet(position: Int, onRest: () -> Unit, onDismiss: () -> Unit) {
    ActionsSheet(
        title = stringResource(Res.string.live_exercise_menu_title),
        onDismiss = onDismiss,
        testTagPrefix = "live_exercise_${position}_menu_sheet",
    ) {
        SheetActionRow(
            label = stringResource(Res.string.live_exercise_menu_rest),
            onClick = onRest,
            testTag = "live_exercise_${position}_menu_rest",
        )
    }
}

/**
 * Rest of one exercise for this session only: a stepper in 15 s steps within [RoutineBounds] and "Guardar".
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
                    valueText = formatElapsed(seconds.toLong()),
                    onDecrement = { seconds = (seconds - RoutineBounds.REST_STEP_SECONDS).coerceAtLeast(MIN) },
                    onIncrement = { seconds = (seconds + RoutineBounds.REST_STEP_SECONDS).coerceAtMost(MAX) },
                    decrementEnabled = seconds > MIN,
                    incrementEnabled = seconds < MAX,
                    decrementContentDescription = stringResource(Res.string.builder_rest_decrease_cd),
                    incrementContentDescription = stringResource(Res.string.builder_rest_increase_cd),
                    stateDescription = stringResource(Res.string.builder_rest_state, seconds),
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
