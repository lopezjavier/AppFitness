@file:Suppress("TooManyFunctions") // One composable per block of the search screen.

package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.add_item
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
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_offline_state
import io.github.lopezjavier.appfitness.composeapp.resources.food_remote_error
import io.github.lopezjavier.appfitness.composeapp.resources.food_search_label
import io.github.lopezjavier.appfitness.composeapp.resources.food_search_photo_description
import io.github.lopezjavier.appfitness.composeapp.resources.food_search_placeholder
import io.github.lopezjavier.appfitness.composeapp.resources.food_search_scan_description
import io.github.lopezjavier.appfitness.composeapp.resources.food_tab_favourites
import io.github.lopezjavier.appfitness.composeapp.resources.food_tab_most_eaten
import io.github.lopezjavier.appfitness.composeapp.resources.food_tab_recent
import io.github.lopezjavier.appfitness.composeapp.resources.food_tab_templates
import io.github.lopezjavier.appfitness.composeapp.resources.ic_add
import io.github.lopezjavier.appfitness.composeapp.resources.ic_barcode_scanner
import io.github.lopezjavier.appfitness.composeapp.resources.ic_photo_camera
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppSearchField
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTabRow
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.FavouriteToggle
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.feature.diary.addObjectLabel
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 6
private const val NAME_FRACTION = 0.6f
private const val DETAIL_FRACTION = 0.35f
private const val TAG = "food_search"

/**
 * Food search (US-17, US-21) on [AppScreen] (R-01): titled with the destination, "Añadir al desayuno" (the label of
 * the button that opened it) or "Añadir alimento" when a food is chosen for a comida. The search field carries
 * the three actions
 * as icon buttons (scan, photo, "Crear alimento"), then the four views ("Recientes / Más comidos / Favoritos /
 * Guardadas", hidden while a query is typed) as tabs and the results: the user's own foods first, the Open Food Facts
 * ones below them (a skeleton while they load, an inline error with "Reintentar" if they failed). [scanFailed] shows
 * the generic "could not read the code" message under the field; [templateManageFailed] a refused delete of a comida.
 * Offline, the search and the actions that need the server are disabled.
 */
@Composable
fun FoodSearchScreen(
    state: FoodSearchUiState,
    onEvent: (FoodSearchEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scanFailed: Boolean = false,
    templateManageFailed: Boolean = false,
    onPhotoClick: () -> Unit = {},
) {
    AppScreen(
        title = titleOf(state),
        modifier = modifier.testTag("food_search_screen"),
        onBack = onBack,
        testTagPrefix = TAG,
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                SearchRow(state, onEvent, onPhotoClick)
                if (scanFailed) ScanError()
                state.notFoundBarcode?.let { NotFoundCard(it, onEvent) }
                if (state.query.isBlank()) Tabs(state, onEvent)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.query.isBlank() && state.tab == FoodSearchTab.Templates) {
                    TemplatesPane(state.templates, state.isOffline, templateManageFailed, onEvent)
                } else {
                    Results(state, onEvent)
                }
            }
        }
    }
}

/** "Añadir al desayuno" for the meal that opened the search, else the generic "Añadir alimento" (R-01). */
@Composable
private fun titleOf(state: FoodSearchUiState): String {
    val slot = state.mealSlot
    return if (state.pick || slot == null) {
        stringResource(Res.string.add_food_sheet_title)
    } else {
        stringResource(Res.string.add_item, addObjectLabel(slot))
    }
}

/**
 * The search field with the three server actions as trailing icon buttons (R-39): scan, photo and "Crear alimento".
 * The field label sits above the input (R-24), so the 48 dp icons align to the bottom of the row, the input box.
 * All three need the server, so they are disabled offline.
 */
@Composable
private fun SearchRow(
    state: FoodSearchUiState,
    onEvent: (FoodSearchEvent) -> Unit,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offlineState = stringResource(Res.string.food_photo_offline_state)
    val offline = if (state.isOffline) Modifier.semantics { stateDescription = offlineState } else Modifier
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        AppSearchField(
            value = state.query,
            onValueChange = { onEvent(FoodSearchEvent.QueryChanged(it)) },
            label = stringResource(Res.string.food_search_label),
            placeholder = stringResource(Res.string.food_search_placeholder),
            onSearch = {}, // The search is live as the query changes; the keyboard action only hides the keyboard.
            modifier = Modifier.weight(1f),
            enabled = !state.isOffline,
            testTagPrefix = "${TAG}_field",
            clearTestTag = "${TAG}_clear",
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.food_search_scan_description),
            icon = Res.drawable.ic_barcode_scanner,
            testTag = "${TAG}_action_scan",
            onClick = { onEvent(FoodSearchEvent.ScanClicked) },
            modifier = offline,
            enabled = !state.isOffline,
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.food_search_photo_description),
            icon = Res.drawable.ic_photo_camera,
            testTag = "${TAG}_action_photo",
            onClick = onPhotoClick,
            modifier = offline,
            enabled = !state.isOffline,
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.food_create_action),
            icon = Res.drawable.ic_add,
            testTag = "${TAG}_action_create",
            onClick = { onEvent(FoodSearchEvent.CreateFromEmpty) },
            modifier = offline,
            enabled = !state.isOffline,
        )
    }
}

/** The empty-box views as tabs (SEL-3); a comida has no "Guardadas" while choosing a food for one. */
@Composable
private fun Tabs(state: FoodSearchUiState, onEvent: (FoodSearchEvent) -> Unit) {
    val templatesTab = AppOption(stringResource(Res.string.food_tab_templates), "${TAG}_tab_templates")
    val options = listOf(
        AppOption(stringResource(Res.string.food_tab_recent), "${TAG}_tab_recent"),
        AppOption(stringResource(Res.string.food_tab_most_eaten), "${TAG}_tab_most_eaten"),
        AppOption(stringResource(Res.string.food_tab_favourites), "${TAG}_tab_favourites"),
    ) + listOfNotNull(templatesTab.takeUnless { state.pick })
    AppTabRow(
        options = options,
        selectedIndex = state.tab.ordinal.coerceAtMost(options.lastIndex),
        onSelect = { onEvent(FoodSearchEvent.TabSelected(FoodSearchTab.entries[it])) },
    )
}

@Composable
private fun Results(state: FoodSearchUiState, onEvent: (FoodSearchEvent) -> Unit) {
    when (val results = state.results) {
        FoodResults.Loading -> ResultsLoading()

        is FoodResults.Content -> ResultsList(results, state.favouriteIds, state.isOffline, onEvent)

        FoodResults.Empty -> EmptyResults(state, onEvent)

        FoodResults.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorState(
                message = stringResource(
                    if (state.isOffline) Res.string.food_offline_message else Res.string.food_error_message,
                ),
                action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(FoodSearchEvent.Retry) },
                testTagPrefix = "${TAG}_retry",
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
            testTagPrefix = "${TAG}_empty",
        )
        if (searching) {
            PrimaryButton(
                text = stringResource(Res.string.food_create_action),
                onClick = { onEvent(FoodSearchEvent.CreateFromEmpty) },
                modifier = Modifier.testTag("${TAG}_create_button"),
            )
        }
    }
}

@Composable
private fun ResultsLoading() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screenMargin).testTag("${TAG}_skeleton"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(SKELETON_ROWS) { SkeletonRow() }
    }
}

@Composable
private fun SkeletonRow() {
    Column(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).padding(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SkeletonBox(Modifier.fillMaxWidth(NAME_FRACTION).heightIn(min = Spacing.lg))
        SkeletonBox(Modifier.fillMaxWidth(DETAIL_FRACTION).heightIn(min = Spacing.md))
    }
}

/**
 * The foods: the manual ones first (never reordered), then the Open Food Facts ones. While those load a skeleton row
 * sits under the manual rows; if they failed an inline error with "Reintentar" does.
 */
@Composable
private fun ResultsList(
    results: FoodResults.Content,
    favouriteIds: Set<String>,
    isOffline: Boolean,
    onEvent: (FoodSearchEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("${TAG}_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(results.foods, key = { it.id }) { food ->
            FoodRow(
                food = food,
                favourite = food.id in favouriteIds,
                enabled = !isOffline,
                onClick = { onEvent(FoodSearchEvent.SelectFood(food)) },
                onToggleFavourite = { onEvent(FoodSearchEvent.ToggleFavourite(food.id)) },
                modifier = appAnimateItem(),
            )
        }
        if (results.remoteLoading) {
            item(key = "remote_loading") {
                Box(Modifier.testTag("${TAG}_remote_loading")) { SkeletonRow() }
            }
        }
        if (results.remoteFailed) {
            item(key = "remote_error") {
                ErrorState(
                    message = stringResource(Res.string.food_remote_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(FoodSearchEvent.Retry) },
                    testTagPrefix = "${TAG}_remote_error",
                    actionTestTag = "${TAG}_remote_retry",
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun FoodRow(
    food: Food,
    favourite: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val per100 = stringResource(
        if (food.baseUnit == "ml") Res.string.food_kcal_per_100_ml else Res.string.food_kcal_per_100_g,
        wholeHalfUp(food.nutrition.kcal),
    )
    val favouriteLabel = stringResource(
        if (favourite) Res.string.food_favourite_remove_cd else Res.string.food_favourite_add_cd,
    )
    val brand = food.brand?.takeIf { it.isNotBlank() }
    AppListRow(
        headline = food.name,
        modifier = modifier.testTag("${TAG}_row_${food.id}"),
        supporting = if (brand != null) "$brand · $per100" else per100,
        trailing = {
            FavouriteToggle(
                selected = favourite,
                onToggle = onToggleFavourite,
                contentDescription = favouriteLabel,
                modifier = Modifier.testTag("${TAG}_favourite_${food.id}"),
                enabled = enabled,
            )
        },
        onClick = if (enabled) onClick else null,
    )
}
