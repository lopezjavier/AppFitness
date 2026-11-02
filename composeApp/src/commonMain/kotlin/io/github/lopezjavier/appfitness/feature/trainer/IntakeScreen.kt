package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.app.IntakeHost
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.intake_best_unknown_exercise
import io.github.lopezjavier.appfitness.composeapp.resources.intake_extract
import io.github.lopezjavier.appfitness.composeapp.resources.intake_extract_failed
import io.github.lopezjavier.appfitness.composeapp.resources.intake_extract_offline
import io.github.lopezjavier.appfitness.composeapp.resources.intake_extract_offline_action
import io.github.lopezjavier.appfitness.composeapp.resources.intake_extracting
import io.github.lopezjavier.appfitness.composeapp.resources.intake_free_text_counter
import io.github.lopezjavier.appfitness.composeapp.resources.intake_free_text_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.intake_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.intake_skip
import io.github.lopezjavier.appfitness.composeapp.resources.intake_title
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EditorSecondaryAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StreamProgressState
import io.github.lopezjavier.appfitness.domain.model.PreBreakBest
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_FIELDS = 5
private const val FREE_TEXT_MIN_LINES = 4

/**
 * Stateful "Para conocerte" (US-81): connects [IntakeViewModel], resolves exercise names for the best lifts, hands the
 * exercise the picker returned to the screen and leaves through [IntakeHost] (saved or "Ahora no" continue, closing
 * returns).
 */
@Composable
fun IntakeRoot(
    host: IntakeHost,
    modifier: Modifier = Modifier,
    viewModel: IntakeViewModel = koinViewModel(),
    connectivity: ConnectivityObserver = koinInject(),
    exercises: ExerciseRepository = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val online by connectivity.isOnline.collectAsStateWithLifecycle(initialValue = true)
    val names by produceState(emptyMap<String, String>(), exercises) {
        value = exercises.getAll().getOrNull().orEmpty().mapNotNull { e -> e.name?.let { e.id to it } }.toMap()
    }
    var saved by rememberSaveable { mutableStateOf(false) }
    val latestHost by rememberUpdatedState(host)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                IntakeEffect.Saved -> saved = true
                IntakeEffect.Skipped -> latestHost.onDone()
                IntakeEffect.Close -> latestHost.onClose()
            }
        }
    }
    IntakeScreen(
        state = state,
        isOnline = online,
        isOnlineFlow = connectivity.isOnline,
        exerciseNames = names,
        showSkip = host.continuesToBeforeStart,
        saved = saved,
        incomingExerciseId = host.selectedExerciseId,
        onIncomingConsume = host.onConsumeSelection,
        onEvent = viewModel::onEvent,
        onAddBest = host.onAddExercise,
        onClose = { if (saved) latestHost.onDone() else latestHost.onClose() },
        modifier = modifier,
    )
}

/**
 * "Para conocerte" on [AppEditorScreen]: the free text first ("Rellenar con mi texto" fills only the empty fields and
 * marks them), then the fields. Nothing is stored until "Guardar"; "Ahora no" ([showSkip]) leaves without saving.
 * [incomingExerciseId] is the exercise the picker returned: it opens the weight and repetitions sheet.
 */
@Suppress("LongParameterList", "LongMethod") // State, connectivity, the picker round trip and the ways out.
@Composable
fun IntakeScreen(
    state: IntakeUiState,
    isOnline: Boolean,
    isOnlineFlow: Flow<Boolean>,
    exerciseNames: Map<String, String>,
    showSkip: Boolean,
    saved: Boolean,
    incomingExerciseId: String?,
    onIncomingConsume: () -> Unit,
    onEvent: (IntakeEvent) -> Unit,
    onAddBest: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state !is IntakeUiState.Content) {
        IntakePlaceholder(state, isOnlineFlow, onEvent, onClose, modifier)
        return
    }
    var sheetExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    val latestConsume by rememberUpdatedState(onIncomingConsume)
    LaunchedEffect(incomingExerciseId) {
        if (incomingExerciseId != null) {
            sheetExerciseId = incomingExerciseId
            latestConsume()
        }
    }
    val saving = state.phase == IntakePhase.Saving
    val haptics = LocalAppHaptics.current
    LaunchedEffect(state.notice) {
        if (state.notice == IntakeNotice.SaveFailed || state.notice == IntakeNotice.SaveOffline) haptics.reject()
    }
    AppEditorScreen(
        title = stringResource(Res.string.intake_title),
        isDirty = state.dirty,
        isSaving = saving,
        isOnline = isOnline,
        onSave = { onEvent(IntakeEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        saved = saved,
        canSave = state.canSave,
        secondaryAction = if (showSkip) {
            EditorSecondaryAction(
                label = stringResource(Res.string.intake_skip),
                testTag = "intake_skip",
                enabled = !saving,
                onClick = { onEvent(IntakeEvent.Skip) },
            )
        } else {
            null
        },
        footerNotice = { SaveNotice(state.notice) },
        testTagPrefix = "intake",
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("intake_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        ) {
            FreeTextBlock(state, isOnline, !saving, onEvent)
            if (state.phase == IntakePhase.Extracting) {
                val message = stringResource(Res.string.intake_extracting)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StreamProgressState(message = message, stateDescription = message, testTag = "intake_progress")
                }
            } else {
                IntakeFields(state, exerciseNames, !saving, onEvent, onAddBest) { sheetExerciseId = it }
            }
        }
    }
    sheetExerciseId?.let { id ->
        BestSheetHost(id, state, exerciseNames, onEvent) { sheetExerciseId = null }
    }
}

/** Opens the weight and repetitions sheet of [exerciseId]: a new best is added, an existing one is replaced. */
@Composable
private fun BestSheetHost(
    exerciseId: String,
    state: IntakeUiState.Content,
    names: Map<String, String>,
    onEvent: (IntakeEvent) -> Unit,
    onClose: () -> Unit,
) {
    val current = state.form.bests.firstOrNull { it.exerciseId == exerciseId }
    BestSheet(
        exerciseName = names[exerciseId] ?: stringResource(Res.string.intake_best_unknown_exercise),
        current = current,
        onApply = { weight, reps ->
            val best = PreBreakBest(exerciseId, weight, reps)
            onEvent(if (current == null) IntakeEvent.AddBest(best) else IntakeEvent.EditBest(best))
            onClose()
        },
        onDismiss = onClose,
    )
}

@Composable
private fun IntakeFields(
    state: IntakeUiState.Content,
    names: Map<String, String>,
    enabled: Boolean,
    onEvent: (IntakeEvent) -> Unit,
    onAddBest: () -> Unit,
    onEditBest: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
        YearsField(state.form.years, state.extracted, enabled) { onEvent(IntakeEvent.EditYears(it)) }
        SituationField(
            state.form.situation,
            state.extracted,
            state.picker == IntakePicker.Situation,
            enabled,
            onEvent,
        )
        InjuredAreaField(
            state.form.injuredArea,
            state.extracted,
            state.picker == IntakePicker.InjuredArea,
            enabled,
            onEvent,
        )
        BestsField(state, names, enabled, onEvent, onAddBest, onEditBest)
        KnownCaloriesFields(state, enabled, onEvent)
    }
}

/** The free text, its counter, "Rellenar con mi texto" and the message of a refused or failed extraction. */
@Composable
private fun FreeTextBlock(
    state: IntakeUiState.Content,
    online: Boolean,
    enabled: Boolean,
    onEvent: (IntakeEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppTextField(
            value = state.form.freeText,
            onValueChange = { onEvent(IntakeEvent.EditFreeText(it)) },
            label = stringResource(Res.string.intake_free_text_label),
            maxLength = IntakeLimits.MAX_FREE_TEXT,
            testTagPrefix = "intake_free_text",
            imeAction = ImeAction.Default,
            singleLine = false,
            minLines = FREE_TEXT_MIN_LINES,
            enabled = enabled,
        )
        Text(
            text = stringResource(
                Res.string.intake_free_text_counter,
                state.form.freeText.length,
                IntakeLimits.MAX_FREE_TEXT,
            ),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().testTag("intake_free_text_counter"),
        )
        SecondaryButton(
            text = stringResource(Res.string.intake_extract),
            onClick = { onEvent(IntakeEvent.Extract) },
            modifier = Modifier.fillMaxWidth().testTag("intake_continue"),
            enabled = state.canExtract && online && enabled,
        )
        if (!online) OfflineHint(action = stringResource(Res.string.intake_extract_offline_action))
        when (val notice = state.notice) {
            is IntakeNotice.Limit -> CoachLimitMessage(notice.failure, testTag = "intake_limit_message")

            // Offline the hint above already says it: one offline message only (R-32).
            IntakeNotice.ExtractOffline -> if (online) {
                InlineNote(
                    text = stringResource(Res.string.intake_extract_offline),
                    testTag = "intake_limit_message",
                )
            }

            IntakeNotice.ExtractFailed -> InlineNote(
                text = stringResource(Res.string.intake_extract_failed),
                testTag = "intake_limit_message",
            )

            else -> Unit
        }
    }
}

@Composable
private fun SaveNotice(notice: IntakeNotice?) {
    when (notice) {
        IntakeNotice.SaveFailed -> InlineNote(
            text = stringResource(Res.string.intake_save_failed),
            testTag = "intake_save_error",
        )

        // Offline the pinned "Guardar" already carries the hint: one offline message only (R-32).
        else -> Unit
    }
}

/** Loading, offline and error before the form exists: a plain screen with back (nothing to discard yet). */
@Composable
private fun IntakePlaceholder(
    state: IntakeUiState,
    isOnline: Flow<Boolean>,
    onEvent: (IntakeEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.intake_title),
        modifier = modifier,
        onBack = onClose,
        testTagPrefix = "intake",
    ) {
        Box(Modifier.fillMaxSize().testTag("intake_screen")) {
            when (state) {
                IntakeUiState.Offline -> OfflineState(isOnline = isOnline, onReload = { onEvent(IntakeEvent.Retry) })

                IntakeUiState.Error -> ErrorState(
                    message = stringResource(Res.string.intake_load_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(IntakeEvent.Retry) },
                    testTagPrefix = "intake_error",
                    actionTestTag = "intake_error_retry",
                )

                else -> Column(
                    Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("intake_loading"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    repeat(SKELETON_FIELDS) {
                        SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget))
                    }
                }
            }
        }
    }
}
