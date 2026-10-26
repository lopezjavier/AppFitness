@file:Suppress("ktlint:standard:property-naming") // PascalCase consts, like the other Defaults objects.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.DrawableResource

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
 * Rows are tagged `<prefix>_row_<n>` (from 1), the X `<prefix>_close`. [notice] is an optional line (e.g. a failure
 * message) between the header and the rows.
 *
 * The other archetypes are [AppEditSheet], [AppPickerSheet] and [AppInfoSheet] (`AppSheets.kt`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionSheet(
    actions: List<AppSheetAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    testTagPrefix: String = AppActionSheetDefaults.TestTag,
    notice: (@Composable () -> Unit)? = null,
) {
    val sheetState = rememberAppSheetState()
    val scope = rememberCoroutineScope()
    AppBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        testTagPrefix = testTagPrefix,
    ) {
        AppSheetHeader(title, { closeThen(scope, sheetState, onDismiss) }, testTagPrefix, help = null)
        notice?.invoke()
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
