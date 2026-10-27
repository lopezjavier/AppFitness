package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_add
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.action_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_items_header
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_title
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.FoodEntryRow
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import org.jetbrains.compose.resources.stringResource

private const val TAG = "template_details"

/**
 * The read-only details of a comida (US-21, US-53) on [AppScreen]: the comida name as the title, "Editar" and
 * "Eliminar" in the top bar (R-02, "Eliminar" is immediate, R-20), the totals card, the foods under "Alimentos en esta
 * comida" (a tap shows the food; nothing is editable or swipeable here) and "Añadir alimento", which starts editing.
 * The pinned primary action "Añadir" logs the foods as shown to the route's meal and day. [onEdit] switches to the
 * editor (`TemplateEditorScreen`). The offline banner is the shell's; writes are disabled offline (R-30).
 */
@Composable
fun TemplateDetailsScreen(
    state: TemplateDetailsUiState,
    onEvent: (TemplateDetailsEvent) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state as? TemplateDetailsUiState.Content
    // Hoisted above the sheet window: state declared inside a sheet is not restored.
    var viewedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    AppScreen(
        title = content?.title ?: stringResource(Res.string.template_details_title),
        modifier = modifier.testTag("template_details_screen"),
        onBack = onBack,
        actions = if (content == null) emptyList() else topBarActions(content, onEvent, onEdit),
        footer = content?.let { { DetailsFooter(it, onEvent) } },
        testTagPrefix = TAG,
    ) {
        when (state) {
            TemplateDetailsUiState.Loading -> DetailsLoading(TAG)

            TemplateDetailsUiState.Error -> DetailsLoadError(TAG) { onEvent(TemplateDetailsEvent.Retry) }

            is TemplateDetailsUiState.Content -> DetailsList(
                content = state,
                onOpen = { viewedIndex = it },
                onAddFood = {
                    onEdit()
                    onEvent(TemplateDetailsEvent.AddFoodClicked)
                },
            )
        }
    }
    content?.rows?.firstOrNull { it.index == viewedIndex }?.let { row ->
        TemplateItemInfoSheet(row = row, onDismiss = { viewedIndex = null })
    }
}

@Composable
private fun topBarActions(
    content: TemplateDetailsUiState.Content,
    onEvent: (TemplateDetailsEvent) -> Unit,
    onEdit: () -> Unit,
): List<AppTopBarAction> = listOf(
    AppTopBarAction(
        icon = Res.drawable.ic_edit,
        contentDescription = stringResource(Res.string.action_edit),
        onClick = onEdit,
        testTag = "${TAG}_edit",
    ),
    AppTopBarAction(
        icon = Res.drawable.ic_delete,
        contentDescription = stringResource(Res.string.action_delete),
        onClick = { if (!content.isOffline && !content.busy) onEvent(TemplateDetailsEvent.Delete) },
        testTag = "${TAG}_delete",
    ),
)

@Composable
private fun DetailsList(content: TemplateDetailsUiState.Content, onOpen: (Int) -> Unit, onAddFood: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("${TAG}_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "totals") { TotalsCard(content, TAG) }
        item(key = "header") {
            ListSectionHeader(
                title = stringResource(Res.string.template_details_items_header),
                modifier = Modifier.testTag("${TAG}_items_header"),
            )
        }
        items(content.rows, key = { it.index }) { row ->
            FoodEntryRow(
                model = row.toRowModel(),
                onClick = { onOpen(row.index) },
                tagPrefix = "${TAG}_item",
                modifier = appAnimateItem(),
            )
        }
        item(key = "add") {
            AddItemButton(
                objectLabel = "alimento",
                onClick = onAddFood,
                modifier = Modifier.testTag("${TAG}_add_item"),
                enabled = !content.busy,
            )
        }
    }
}

/** The pinned primary action "Añadir" and, offline, the one shared hint under it. */
@Composable
private fun DetailsFooter(content: TemplateDetailsUiState.Content, onEvent: (TemplateDetailsEvent) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PrimaryButton(
            text = stringResource(Res.string.action_add),
            onClick = { onEvent(TemplateDetailsEvent.Add) },
            modifier = Modifier.fillMaxWidth().testTag("${TAG}_primary"),
            enabled = content.canAdd,
            loading = content.adding,
        )
        if (content.isOffline) OfflineHint(action = "añadir")
    }
}
