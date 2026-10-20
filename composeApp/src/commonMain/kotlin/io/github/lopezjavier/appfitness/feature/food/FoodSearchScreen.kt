package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.food_create_action
import io.github.lopezjavier.appfitness.composeapp.resources.food_empty_favourites
import io.github.lopezjavier.appfitness.composeapp.resources.food_empty_most_eaten
import io.github.lopezjavier.appfitness.composeapp.resources.food_empty_recent
import io.github.lopezjavier.appfitness.composeapp.resources.food_empty_search
import io.github.lopezjavier.appfitness.composeapp.resources.food_empty_search_hint
import io.github.lopezjavier.appfitness.composeapp.resources.food_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.food_favourite_add_cd
import io.github.lopezjavier.appfitness.composeapp.resources.food_favourite_remove_cd
import io.github.lopezjavier.appfitness.composeapp.resources.food_kcal_per_100_g
import io.github.lopezjavier.appfitness.composeapp.resources.food_kcal_per_100_ml
import io.github.lopezjavier.appfitness.composeapp.resources.food_offline_message
import io.github.lopezjavier.appfitness.composeapp.resources.food_scan_cd
import io.github.lopezjavier.appfitness.composeapp.resources.food_search_clear_cd
import io.github.lopezjavier.appfitness.composeapp.resources.food_search_label
import io.github.lopezjavier.appfitness.composeapp.resources.food_search_placeholder
import io.github.lopezjavier.appfitness.composeapp.resources.food_tab_favourites
import io.github.lopezjavier.appfitness.composeapp.resources.food_tab_most_eaten
import io.github.lopezjavier.appfitness.composeapp.resources.food_tab_recent
import io.github.lopezjavier.appfitness.composeapp.resources.ic_barcode_scanner
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.FavouriteToggle
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 6
private const val NAME_FRACTION = 0.6f
private const val DETAIL_FRACTION = 0.35f

/**
 * Food search (US-17): the search box, the three empty-box tabs (hidden while a query is typed) and the results. The
 * top bar belongs to the shell. [scanFailed] shows the generic "could not read the code" message under the box; the
 * not-found card of a scanned [FoodSearchUiState.notFoundBarcode] and the "Crear alimento" button of the empty result
 * emit their events.
 */
@Composable
fun FoodSearchScreen(
    state: FoodSearchUiState,
    onEvent: (FoodSearchEvent) -> Unit,
    modifier: Modifier = Modifier,
    scanFailed: Boolean = false,
) {
    Box(modifier.fillMaxSize().testTag("food_search_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            Column(
                Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                SearchField(state, onEvent)
                if (scanFailed) ScanError()
                state.notFoundBarcode?.let { NotFoundCard(it, onEvent) }
                if (state.query.isBlank()) Tabs(state, onEvent)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { Results(state, onEvent) }
        }
    }
}

/** Search box with a clear button and the scan button; both are disabled offline because they need the server. */
@Composable
private fun SearchField(state: FoodSearchUiState, onEvent: (FoodSearchEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        SearchBox(state, onEvent, Modifier.weight(1f))
        AppIconButton(
            contentDescription = stringResource(Res.string.food_scan_cd),
            icon = Res.drawable.ic_barcode_scanner,
            testTag = "food_search_scan_button",
            onClick = { onEvent(FoodSearchEvent.ScanClicked) },
            enabled = !state.isOffline,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
    }
}

@Composable
private fun SearchBox(state: FoodSearchUiState, onEvent: (FoodSearchEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        AppTextField(
            value = state.query,
            onValueChange = { onEvent(FoodSearchEvent.QueryChanged(it)) },
            label = stringResource(Res.string.food_search_label),
            placeholder = stringResource(Res.string.food_search_placeholder),
            enabled = !state.isOffline,
            testTagPrefix = "food_search_input",
        )
        if (state.query.isNotEmpty() && !state.isOffline) {
            val clearLabel = stringResource(Res.string.food_search_clear_cd)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(Spacing.minTouchTarget)
                    .clickable(role = Role.Button, onClickLabel = clearLabel) {
                        onEvent(FoodSearchEvent.QueryChanged(""))
                    }
                    .testTag("food_search_clear"),
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
private fun Tabs(state: FoodSearchUiState, onEvent: (FoodSearchEvent) -> Unit) {
    val options = listOf(
        AppOption(stringResource(Res.string.food_tab_recent), "food_search_tab_recent"),
        AppOption(stringResource(Res.string.food_tab_most_eaten), "food_search_tab_most_eaten"),
        AppOption(stringResource(Res.string.food_tab_favourites), "food_search_tab_favourites"),
    )
    SegmentedSelector(
        options = options,
        selectedIndex = state.tab.ordinal,
        onSelect = { onEvent(FoodSearchEvent.TabSelected(FoodSearchTab.entries[it])) },
        enabled = !state.isOffline,
    )
}

@Composable
private fun Results(state: FoodSearchUiState, onEvent: (FoodSearchEvent) -> Unit) {
    when (val results = state.results) {
        FoodResults.Loading -> ResultsLoading()

        is FoodResults.Content -> ResultsList(results.foods, state.favouriteIds, state.isOffline, onEvent)

        FoodResults.Empty -> EmptyResults(state, onEvent)

        FoodResults.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorState(
                message = stringResource(
                    if (state.isOffline) Res.string.food_offline_message else Res.string.food_error_message,
                ),
                action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(FoodSearchEvent.Retry) },
                testTagPrefix = "food_search_retry",
            )
        }
    }
}

@Composable
private fun EmptyResults(state: FoodSearchUiState, onEvent: (FoodSearchEvent) -> Unit) {
    val searching = state.query.isNotBlank()
    val message = stringResource(
        when {
            searching -> Res.string.food_empty_search
            state.tab == FoodSearchTab.Recent -> Res.string.food_empty_recent
            state.tab == FoodSearchTab.MostEaten -> Res.string.food_empty_most_eaten
            else -> Res.string.food_empty_favourites
        },
    )
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            message = if (searching) stringResource(Res.string.food_empty_search_hint) else message,
            title = if (searching) message else null,
            testTagPrefix = "food_search_empty",
        )
        if (searching) {
            PrimaryButton(
                text = stringResource(Res.string.food_create_action),
                onClick = { onEvent(FoodSearchEvent.CreateFromEmpty) },
                modifier = Modifier.testTag("food_search_create_button"),
            )
        }
    }
}

@Composable
private fun ResultsLoading() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screenMargin).testTag("food_search_skeleton"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(SKELETON_ROWS) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).padding(vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SkeletonBox(Modifier.fillMaxWidth(NAME_FRACTION).heightIn(min = Spacing.lg))
                SkeletonBox(Modifier.fillMaxWidth(DETAIL_FRACTION).heightIn(min = Spacing.md))
            }
        }
    }
}

@Composable
private fun ResultsList(
    foods: List<Food>,
    favouriteIds: Set<String>,
    isOffline: Boolean,
    onEvent: (FoodSearchEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("food_search_list"),
        contentPadding = PaddingValues(
            horizontal = Spacing.screenMargin,
            vertical = Spacing.sm,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(foods, key = { it.id }) { food ->
            FoodRow(
                food = food,
                favourite = food.id in favouriteIds,
                enabled = !isOffline,
                onClick = { onEvent(FoodSearchEvent.SelectFood(food)) },
                onToggleFavourite = { onEvent(FoodSearchEvent.ToggleFavourite(food.id)) },
            )
        }
    }
}

@Composable
private fun FoodRow(
    food: Food,
    favourite: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    val per100 = stringResource(
        if (food.baseUnit == "ml") Res.string.food_kcal_per_100_ml else Res.string.food_kcal_per_100_g,
        wholeHalfUp(food.nutrition.kcal),
    )
    val favouriteLabel = stringResource(
        if (favourite) Res.string.food_favourite_remove_cd else Res.string.food_favourite_add_cd,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clip(AppShapes.md)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("food_search_result_${food.id}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(
                text = food.name,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            food.brand?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = per100,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
        FavouriteToggle(
            selected = favourite,
            onToggle = onToggleFavourite,
            contentDescription = favouriteLabel,
            modifier = Modifier.testTag("food_search_favourite_${food.id}"),
            enabled = enabled,
        )
    }
}
