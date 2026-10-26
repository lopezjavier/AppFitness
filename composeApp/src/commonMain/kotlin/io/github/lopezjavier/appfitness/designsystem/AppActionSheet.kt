@file:Suppress("ktlint:standard:property-naming") // PascalCase consts, like the other Defaults objects.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_close
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

/** One row of an [AppActionSheet]: decorative [icon], [title] and an optional one-line [hint]. */
@Immutable
data class AppSheetAction(
    val icon: DrawableResource,
    val title: String,
    val hint: String? = null,
    val onClick: () -> Unit,
)

/** Tags of [AppActionSheet]. */
object AppActionSheetDefaults {
    const val TestTag = "action_sheet"

    fun closeTag(prefix: String = TestTag): String = "${prefix}_close"

    fun rowTag(index: Int, prefix: String = TestTag): String = "${prefix}_row_$index"
}

/**
 * Actions archetype of the sheet family (R-07): an optional [title], an X "Cerrar" and one [ActionRow] per action.
 * Tapping a row runs its action and then closes the sheet (it animates out first); the X only closes.
 * [onDismiss] is called once the sheet is gone, also for a scrim tap, a drag and back.
 * Rows are tagged `<prefix>_row_<n>` (from 1), the X `<prefix>_close`.
 *
 * The other archetypes (edit, picker, info) and the top-only hairline belong to T-119.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionSheet(
    actions: List<AppSheetAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    testTagPrefix: String = AppActionSheetDefaults.TestTag,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    AppBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        testTagPrefix = testTagPrefix,
    ) {
        SheetHeader(title, testTagPrefix) { closeThen(scope, sheetState, onDismiss) {} }
        actions.forEachIndexed { index, action ->
            ActionRow(
                icon = action.icon,
                title = action.title,
                hint = action.hint,
                onClick = { closeThen(scope, sheetState, onDismiss, action.onClick) },
                testTag = AppActionSheetDefaults.rowTag(index + 1, testTagPrefix),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun closeThen(scope: CoroutineScope, sheetState: SheetState, onDismiss: () -> Unit, action: () -> Unit) {
    action()
    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
}

@Composable
private fun SheetHeader(title: String?, prefix: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.screenMargin, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        AppIconButton(
            contentDescription = stringResource(Res.string.action_close),
            icon = Res.drawable.ic_close,
            testTag = AppActionSheetDefaults.closeTag(prefix),
            onClick = onClose,
        )
    }
}
