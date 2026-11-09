package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_clearly_wrong
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_empty_body
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_fact
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_gap_over
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_gap_under
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_help_compare
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_help_nothing_changes
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_help_save_anyway
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_row_edit
import io.github.lopezjavier.appfitness.composeapp.resources.food_audit_title
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.designsystem.helpTopBarAction
import io.github.lopezjavier.appfitness.domain.model.FoodAuditRow
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private const val TAG = "food_audit"
private const val SKELETON_ROWS = 6
private const val NAME_FRACTION = 0.6f
private const val DETAIL_FRACTION = 0.8f

/** The key under which the editor leaves "a food was saved" in the audit's back-stack entry. */
const val FOOD_AUDIT_EDITED_KEY = "food_audit_edited"

/**
 * Food audit (US-117): the user's own foods whose kcal and macros do not match, worst first, as given. A row opens the
 * food's editor (the exception to R-18); there is no swipe and no delete. [isOnline] retries an offline load when the
 * connection returns.
 */
@Composable
fun FoodAuditScreen(
    state: FoodAuditUiState,
    isOnline: Flow<Boolean>,
    onEvent: (FoodAuditEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    AppScreen(
        title = stringResource(Res.string.food_audit_title),
        modifier = modifier.testTag("${TAG}_screen"),
        onBack = onBack,
        actions = listOf(
            helpTopBarAction(stringResource(Res.string.food_audit_help_cd), "${TAG}_help") { helpOpen = true },
        ),
        testTagPrefix = TAG,
    ) {
        AuditBody(state, isOnline, onEvent)
    }
    if (helpOpen) {
        AppInfoSheet(
            title = stringResource(Res.string.food_audit_title),
            onDismiss = { helpOpen = false },
            testTagPrefix = "${TAG}_help_sheet",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    Res.string.food_audit_help_compare,
                    Res.string.food_audit_help_nothing_changes,
                    Res.string.food_audit_help_save_anyway,
                ).forEachIndexed { index, bullet ->
                    Text(
                        text = "• " + stringResource(bullet),
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("${TAG}_help_sheet_bullet_${index + 1}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditBody(state: FoodAuditUiState, isOnline: Flow<Boolean>, onEvent: (FoodAuditEvent) -> Unit) {
    when (state) {
        FoodAuditUiState.Loading -> Skeleton()

        is FoodAuditUiState.Content -> AuditList(state.rows, onEvent)

        FoodAuditUiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                message = stringResource(Res.string.food_audit_empty_body),
                title = stringResource(Res.string.food_audit_empty_title),
                testTagPrefix = "${TAG}_empty",
            )
        }

        FoodAuditUiState.Offline -> Box(
            Modifier.fillMaxSize().testTag("${TAG}_offline"),
            contentAlignment = Alignment.Center,
        ) {
            OfflineState(isOnline = isOnline, onReload = { onEvent(FoodAuditEvent.Retry) })
        }

        FoodAuditUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorState(
                message = stringResource(Res.string.food_audit_error_message),
                action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(FoodAuditEvent.Retry) },
                testTagPrefix = "${TAG}_error",
                actionTestTag = "${TAG}_retry",
            )
        }
    }
}

@Composable
private fun AuditList(rows: List<FoodAuditRow>, onEvent: (FoodAuditEvent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("${TAG}_list"),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(rows, key = { it.foodId }) { row ->
            AuditRow(row, onClick = { onEvent(FoodAuditEvent.OpenFood(row.foodId)) }, modifier = appAnimateItem())
        }
    }
}

@Composable
private fun AuditRow(row: FoodAuditRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val clearlyWrong = row.level == EnergyCheckLevel.ClearlyWrong
    val fact = stringResource(
        Res.string.food_audit_fact,
        AppNumberFormat.decimal(row.declaredWhole.toDouble(), maxDecimals = 0),
        AppNumberFormat.decimal(row.macrosWhole.toDouble(), maxDecimals = 0),
    )
    val gapValue = AppNumberFormat.decimal(abs(row.gapWhole).toDouble(), maxDecimals = 0)
    val gap = stringResource(
        if (row.gapWhole < 0) Res.string.food_audit_gap_under else Res.string.food_audit_gap_over,
        gapValue,
    )
    AppListRow(
        headline = row.name,
        modifier = modifier.testTag("${TAG}_row_${row.foodId}"),
        supporting = listOfNotNull(row.brand?.takeIf { it.isNotBlank() }, fact).joinToString("\n"),
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = gap,
                    style = AppTheme.typography.bodyMedium,
                    color = if (clearlyWrong) AppTheme.colors.energyAmber else AppTheme.colors.textPrimary,
                )
                if (clearlyWrong) {
                    Text(
                        text = stringResource(Res.string.food_audit_clearly_wrong),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.energyAmber,
                    )
                }
            }
        },
        onClick = onClick,
        onClickLabel = stringResource(Res.string.food_audit_row_edit, row.name),
    )
}

@Composable
private fun Skeleton() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screenMargin).testTag("${TAG}_loading"),
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
