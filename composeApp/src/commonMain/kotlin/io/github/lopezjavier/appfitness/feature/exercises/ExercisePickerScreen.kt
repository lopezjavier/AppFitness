package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_attribution
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_button
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_empty_catalog
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_empty_hint
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_empty_no_results
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_empty_no_results_filters
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_filter_all
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_offline_hint
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_row_subtitle
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_search_clear_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_search_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_search_placeholder
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.ExerciseThumbnail
import io.github.lopezjavier.appfitness.designsystem.ExerciseThumbnailDefaults
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import org.jetbrains.compose.resources.painterResource
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
    viewModel: ExercisePickerViewModel = koinViewModel(parameters = { parametersOf(selectMode) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOpenDetail by rememberUpdatedState(onOpenDetail)
    val currentSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExercisePickerEffect.OpenDetail -> currentOpenDetail(effect.exerciseId)
                is ExercisePickerEffect.Selected -> currentSelect(effect.exerciseId)
            }
        }
    }
    ExercisePickerScreen(state, viewModel::onEvent, modifier)
}

/**
 * Exercise picker (US-09): search field and two filter chip rows on top, the list in the middle and, pinned at the
 * bottom, "Crear ejercicio" plus the RepDB attribution. From 600 dp the list has two columns in a centred, capped
 * column.
 */
@Composable
fun ExercisePickerScreen(
    state: ExercisePickerUiState,
    onEvent: (ExercisePickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.fillMaxSize().testTag("exercise_picker_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
        Column(Modifier.widthIn(max = ExerciseLayoutDefaults.ContentMaxWidth).fillMaxSize()) {
            if (state is ExercisePickerUiState.Content) PickerFilters(state, onEvent)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    ExercisePickerUiState.Loading -> PickerLoading()
                    ExercisePickerUiState.Error -> PickerError { onEvent(ExercisePickerEvent.Retry) }
                    is ExercisePickerUiState.Content -> PickerList(state, wide, onEvent)
                }
            }
            PickerFooter(state, onEvent)
        }
    }
    (state as? ExercisePickerUiState.Content)?.createForm?.let { CreateExerciseSheet(it, onEvent) }
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

/** Search field with a clear button; the IME action closes the keyboard (see [AppTextField]). */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        AppTextField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(Res.string.exercise_search_label),
            placeholder = stringResource(Res.string.exercise_search_placeholder),
            testTagPrefix = "exercise_picker_search",
        )
        if (query.isNotEmpty()) {
            val clearLabel = stringResource(Res.string.exercise_search_clear_cd)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(Spacing.minTouchTarget)
                    .clickable(role = Role.Button, onClickLabel = clearLabel) { onQueryChange("") }
                    .testTag("exercise_picker_search_clear"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = clearLabel,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(Spacing.xl),
                )
            }
        }
    }
}

@Composable
private fun PickerList(state: ExercisePickerUiState.Content, wide: Boolean, onEvent: (ExercisePickerEvent) -> Unit) {
    if (state.visible.isEmpty()) {
        PickerEmpty(state)
        return
    }
    val columns = if (wide) ExerciseLayoutDefaults.WIDE_COLUMNS else 1
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize().testTag("exercise_picker_list"),
        contentPadding =
        PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        items(state.visible, key = { it.id }) { item ->
            ExerciseRow(item) { onEvent(ExercisePickerEvent.OpenExercise(item.id)) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.size(Spacing.xs)) }
    }
}

@Composable
private fun ExerciseRow(item: CatalogExercise, onClick: () -> Unit) {
    val tag = "exercise_picker_row_${item.id}"
    val subtitle =
        stringResource(
            Res.string.exercise_row_subtitle,
            stringResource(item.exercise.muscleGroup.label),
            stringResource(item.exercise.equipment.label),
        )
    val key = item.exercise.key
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = ExerciseLayoutDefaults.RowMinHeight)
            .clip(AppShapes.md)
            .background(AppTheme.colors.surfaceRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.sm)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(
            imageUri = if (item.hasMedia && key != null) LocalExerciseMediaUris.current.start(key) else null,
            size = ExerciseThumbnailDefaults.RowSize,
            testTagPrefix = tag,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PickerEmpty(state: ExercisePickerUiState.Content) {
    val hint = stringResource(Res.string.exercise_empty_hint)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
            testTagPrefix = "exercise_picker_error",
        )
    }
}

/** Pinned bottom bar: "Crear ejercicio" (with the offline reason when disabled) and the attribution. */
@Composable
private fun PickerFooter(state: ExercisePickerUiState, onEvent: (ExercisePickerEvent) -> Unit) {
    val online = (state as? ExercisePickerUiState.Content)?.isOnline ?: true
    val showCreate = state is ExercisePickerUiState.Content
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (showCreate) {
            if (!online) {
                Text(
                    text = stringResource(Res.string.exercise_offline_hint),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("exercise_picker_offline_hint"),
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.exercise_create_button),
                onClick = { onEvent(ExercisePickerEvent.OpenCreate) },
                enabled = online,
                modifier = Modifier.fillMaxWidth().testTag("exercise_picker_create_button"),
            )
        }
        Text(
            text = stringResource(Res.string.exercise_attribution),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth().testTag("exercise_picker_attribution"),
        )
    }
}
