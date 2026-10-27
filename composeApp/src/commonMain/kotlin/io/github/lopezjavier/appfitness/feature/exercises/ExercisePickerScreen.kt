package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_attribution
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_empty_catalog
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_empty_hint
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_empty_no_results
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_empty_no_results_filters
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_filter_all
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_picker_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_search_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_search_placeholder
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppSearchField
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.ExerciseThumbnailDefaults
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val SKELETON_ROWS = 7

/** Stateful picker: connects [ExercisePickerViewModel] and routes its one-off results to the caller. */
@Composable
fun ExercisePickerRoot(
    selectMode: Boolean,
    onOpenDetail: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ExercisePickerViewModel = koinViewModel(parameters = { parametersOf(selectMode) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOpenDetail by rememberUpdatedState(onOpenDetail)
    val currentSelect by rememberUpdatedState(onSelect)
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExercisePickerEffect.OpenDetail -> currentOpenDetail(effect.exerciseId)

                is ExercisePickerEffect.Selected -> currentSelect(effect.exerciseId)

                ExercisePickerEffect.CreateFailed -> {
                    haptics.reject()
                    messages.show(getString(Res.string.exercise_save_failed))
                }
            }
        }
    }
    // Reloads after an edit or a delete made in the detail (the session cache was invalidated).
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    ExercisePickerScreen(state, viewModel::onEvent, modifier, onBack, selectMode)
}

/**
 * Exercise picker (US-09) on [AppScreen]: search field and two filter chip rows on top, then the list. In
 * [selectMode] a row selects the exercise and its thumbnail opens the detail (R-17); otherwise the row opens the
 * detail. "Añadir ejercicio propio" and the RepDB attribution end the list. From 600 dp the list has two columns in a
 * centred, capped column.
 */
@Composable
fun ExercisePickerScreen(
    state: ExercisePickerUiState,
    onEvent: (ExercisePickerEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    selectMode: Boolean = false,
) {
    AppScreen(
        title = stringResource(Res.string.exercise_picker_title),
        modifier = modifier,
        onBack = onBack,
        maxContentWidth = ExerciseLayoutDefaults.ContentMaxWidth,
        testTagPrefix = "exercise_picker",
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("exercise_picker_screen")) {
            val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
            Column(Modifier.fillMaxSize()) {
                if (state is ExercisePickerUiState.Content) PickerFilters(state, onEvent)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state) {
                        ExercisePickerUiState.Loading -> PickerLoading()
                        ExercisePickerUiState.Error -> PickerError { onEvent(ExercisePickerEvent.Retry) }
                        is ExercisePickerUiState.Content -> PickerList(state, wide, selectMode, onEvent)
                    }
                }
            }
        }
    }
    (state as? ExercisePickerUiState.Content)?.createForm?.let { CreateExerciseSheet(it, state.isOnline, onEvent) }
}

@Composable
private fun PickerFilters(state: ExercisePickerUiState.Content, onEvent: (ExercisePickerEvent) -> Unit) {
    val all = stringResource(Res.string.exercise_filter_all)
    val muscleOptions =
        listOf(AppOption(all, "exercise_picker_muscle_chip_all")) +
            MuscleGroup.entries.map {
                AppOption(stringResource(it.label), "exercise_picker_muscle_chip_${it.tagName()}")
            }
    val equipmentOptions =
        listOf(AppOption(all, "exercise_picker_equipment_chip_all")) +
            Equipment.entries.map {
                AppOption(stringResource(it.label), "exercise_picker_equipment_chip_${it.tagName()}")
            }
    Column(
        Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SearchField(
            query = state.query,
            onQueryChange = { onEvent(ExercisePickerEvent.SetQuery(it)) },
            modifier = Modifier.padding(horizontal = Spacing.screenMargin),
        )
        FilterChipRow(
            options = muscleOptions,
            selectedIndex = state.muscleGroup?.let { it.ordinal + 1 } ?: 0,
            onSelect = { index ->
                onEvent(ExercisePickerEvent.SelectMuscleGroup(MuscleGroup.entries.getOrNull(index - 1)))
            },
            modifier = Modifier.padding(horizontal = Spacing.screenMargin).testTag("exercise_picker_muscle_chips"),
        )
        FilterChipRow(
            options = equipmentOptions,
            selectedIndex = state.equipment?.let { it.ordinal + 1 } ?: 0,
            onSelect = { index ->
                onEvent(ExercisePickerEvent.SelectEquipment(Equipment.entries.getOrNull(index - 1)))
            },
            modifier = Modifier.padding(horizontal = Spacing.screenMargin).testTag("exercise_picker_equipment_chips"),
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    AppSearchField(
        value = query,
        onValueChange = onQueryChange,
        label = stringResource(Res.string.exercise_search_label),
        placeholder = stringResource(Res.string.exercise_search_placeholder),
        onSearch = {}, // The list filters live as the query changes; the keyboard action only hides the keyboard.
        modifier = modifier.fillMaxWidth(),
        testTagPrefix = "exercise_picker_search",
    )
}

/**
 * The rows (or the empty state) followed by "Añadir ejercicio propio" and the RepDB attribution, all in one grid so
 * the button is the last element and stays visible when a search finds nothing.
 */
@Composable
private fun PickerList(
    state: ExercisePickerUiState.Content,
    wide: Boolean,
    selectMode: Boolean,
    onEvent: (ExercisePickerEvent) -> Unit,
) {
    val columns = if (wide) ExerciseLayoutDefaults.WIDE_COLUMNS else 1
    val mediaUris = LocalExerciseMediaUris.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().testTag("exercise_picker_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (state.visible.isEmpty()) {
            fullSpanItem("empty") { PickerEmpty(state) }
        } else {
            items(state.visible, key = { it.id }) { item ->
                PickerRow(item, selectMode, mediaUris, onEvent, appAnimateItem())
            }
        }
        fullSpanItem("create") { CreateOwnExercise(state.isOnline, onEvent) }
        fullSpanItem("attribution") {
            Text(
                text = stringResource(Res.string.exercise_attribution),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth().testTag("exercise_picker_attribution"),
            )
        }
    }
}

private fun LazyGridScope.fullSpanItem(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun PickerEmpty(state: ExercisePickerUiState.Content) {
    val hint = stringResource(Res.string.exercise_empty_hint)
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), contentAlignment = Alignment.Center) {
        when {
            state.totalCount == 0 ->
                EmptyState(
                    message = stringResource(Res.string.exercise_empty_catalog),
                    testTagPrefix = "exercise_picker_empty",
                )

            state.query.isBlank() ->
                EmptyState(
                    message = hint,
                    title = stringResource(Res.string.exercise_empty_no_results_filters),
                    testTagPrefix = "exercise_picker_empty",
                )

            else ->
                EmptyState(
                    message = hint,
                    title = stringResource(Res.string.exercise_empty_no_results, state.query.trim()),
                    testTagPrefix = "exercise_picker_empty",
                )
        }
    }
}

@Composable
private fun PickerLoading() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm).testTag(
            "exercise_picker_loading",
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_ROWS) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = ExerciseLayoutDefaults.RowMinHeight).padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(Modifier.size(ExerciseThumbnailDefaults.RowSize), AppShapes.md)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SkeletonBox(Modifier.fillMaxWidth(NAME_FRACTION).heightIn(min = Spacing.lg))
                    SkeletonBox(Modifier.fillMaxWidth(SUBTITLE_FRACTION).heightIn(min = Spacing.md))
                }
            }
        }
    }
}

private const val NAME_FRACTION = 0.7f
private const val SUBTITLE_FRACTION = 0.4f

@Composable
private fun PickerError(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.exercise_error_message),
            action = AppStateAction(stringResource(Res.string.action_retry), onRetry),
            testTagPrefix = "exercise_picker_error",
        )
    }
}
