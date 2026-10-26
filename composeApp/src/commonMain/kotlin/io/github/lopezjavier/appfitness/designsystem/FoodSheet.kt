package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_preview_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_fat
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_protein
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.food_sheet_details_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val EXPANDED_ROTATION = 180f

/** The favourite star of a [FoodSheet] header: state, the toggle and its spoken action. */
@Immutable
data class FoodSheetFavourite(val selected: Boolean, val contentDescription: String, val onToggle: () -> Unit)

/** Already formatted totals of the typed quantity (the caller owns the rounding rules). */
@Immutable
data class FoodSheetTotals(val kcal: String, val proteinG: String, val carbsG: String, val fatG: String)

/**
 * Footer of a [FoodSheet]. Only [Edit] exists today (T-121); the add and comida contexts join in T-122, so the
 * interface stays open for them.
 */
sealed interface FoodSheetContext {
    /**
     * Editing a logged food: "Guardar" (enabled by [canSave], busy while [saving]) and a destructive "Eliminar"
     * stacked under it (disabled while `!canDelete` or saving). [offlineHint] is the one shared hint under the footer.
     */
    @Immutable
    data class Edit(
        val canSave: Boolean,
        val saving: Boolean,
        val onSave: () -> Unit,
        val onDelete: () -> Unit,
        val canDelete: Boolean,
        val offlineHint: String?,
    ) : FoodSheetContext
}

/**
 * The food sheet (LST-1) on [AppEditSheet]: name as the title, [brand] under it, optional favourite star and
 * manual-food pencil in the header, then [quantity], the live [totals], collapsible [details] (expanded state saved
 * across recreation) and [extra] (the meal chips). The footer comes from [context]. Closing only calls [onDismiss].
 * Tags: `<prefix>_title`, `_brand`, `_totals` (+ `_kcal`), `_details_toggle`, `_details`, `_commit`, `_delete`,
 * `_commit_hint`, `_close`, `_favourite`, `_edit_food`.
 */
@Suppress("LongParameterList") // Slots fixed by the T-121 contract.
@Composable
fun FoodSheet(
    name: String,
    brand: String?,
    quantity: @Composable () -> Unit,
    totals: FoodSheetTotals?,
    details: (@Composable () -> Unit)?,
    extra: (@Composable () -> Unit)?,
    context: FoodSheetContext,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    favourite: FoodSheetFavourite? = null,
    onEditFood: (() -> Unit)? = null,
    testTagPrefix: String = "food_sheet",
) {
    val edit = context as FoodSheetContext.Edit
    // Hoisted above the sheet window: state declared inside a sheet is not restored.
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    AppEditSheet(
        title = name,
        commit = SheetCommit.Save,
        onCommit = edit.onSave,
        onDismiss = onDismiss,
        modifier = modifier,
        commitEnabled = edit.canSave,
        saving = edit.saving,
        commitHint = edit.offlineHint,
        help = headerActions(favourite, onEditFood, testTagPrefix),
        secondaryAction = {
            GhostButton(
                text = stringResource(Res.string.action_delete),
                onClick = edit.onDelete,
                enabled = edit.canDelete && !edit.saving,
                tone = GhostTone.Destructive,
                modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_delete"),
            )
        },
        testTagPrefix = testTagPrefix,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            if (!brand.isNullOrBlank()) {
                Text(
                    text = brand,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${testTagPrefix}_brand"),
                )
            }
            quantity()
            if (totals != null) Totals(totals, "${testTagPrefix}_totals")
            if (details != null) DetailsSection(details, detailsExpanded, { detailsExpanded = it }, testTagPrefix)
            extra?.invoke()
        }
    }
}

private fun headerActions(
    favourite: FoodSheetFavourite?,
    onEditFood: (() -> Unit)?,
    prefix: String,
): (@Composable () -> Unit)? {
    if (favourite == null && onEditFood == null) return null
    return {
        if (favourite != null) {
            FavouriteToggle(
                selected = favourite.selected,
                onToggle = favourite.onToggle,
                contentDescription = favourite.contentDescription,
                modifier = Modifier.testTag("${prefix}_favourite"),
            )
        }
        if (onEditFood != null) {
            AppIconButton(
                contentDescription = stringResource(Res.string.add_food_edit_cd),
                icon = Res.drawable.ic_edit,
                testTag = "${prefix}_edit_food",
                onClick = onEditFood,
                tint = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun Totals(totals: FoodSheetTotals, tag: String) {
    val colors = AppTheme.colors
    val style = AppTheme.typography.labelNumeric
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.add_food_preview_kcal, totals.kcal),
            style = style,
            color = colors.textPrimary,
            modifier = Modifier.testTag("${tag}_kcal"),
        )
        Text(
            stringResource(Res.string.diary_meal_protein, totals.proteinG),
            style = style,
            color = MacroDefaults.color(Macro.Protein, colors),
        )
        Text(
            stringResource(Res.string.diary_meal_carbs, totals.carbsG),
            style = style,
            color = MacroDefaults.color(Macro.Carbs, colors),
        )
        Text(
            stringResource(Res.string.diary_meal_fat, totals.fatG),
            style = style,
            color = MacroDefaults.color(Macro.Fat, colors),
        )
    }
}

@Composable
private fun DetailsSection(
    details: @Composable () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    prefix: String,
) {
    val stateText = stringResource(
        if (expanded) Res.string.diary_section_expanded else Res.string.diary_section_collapsed,
    )
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button) { onExpandedChange(!expanded) }
                .semantics { stateDescription = stateText }
                .testTag("${prefix}_details_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.food_sheet_details_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Icon(
                painter = painterResource(Res.drawable.ic_expand_more),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl).rotate(if (expanded) EXPANDED_ROTATION else 0f),
            )
        }
        if (expanded) Column(Modifier.testTag("${prefix}_details")) { details() }
    }
}
