package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_add
import io.github.lopezjavier.appfitness.composeapp.resources.action_close
import io.github.lopezjavier.appfitness.composeapp.resources.action_done
import io.github.lopezjavier.appfitness.composeapp.resources.action_save
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * What the footer button of an [AppEditSheet] does (R-12): "Guardar" persists, "Listo" only applies to a draft and
 * "Añadir" creates something new (same caller contract as [Save]).
 */
enum class SheetCommit { Save, Apply, Add }

/** Tags of the sheet archetypes: `<prefix>_close`, `<prefix>_commit` and `<prefix>_commit_hint`. */
object AppSheetsDefaults {
    fun closeTag(prefix: String): String = "${prefix}_close"

    fun titleTag(prefix: String): String = "${prefix}_title"

    fun commitTag(prefix: String): String = "${prefix}_commit"

    fun commitHintTag(prefix: String): String = "${prefix}_commit_hint"
}

/**
 * Edit archetype of the sheet family (R-07): [AppSheetHeader] (title, optional "?" [help], X "Cerrar"), a body that
 * scrolls and a full-width footer button pinned above the keyboard (R-09). The footer reads "Guardar" for
 * [SheetCommit.Save], "Listo" for [SheetCommit.Apply] (R-12) and "Añadir" for [SheetCommit.Add], is tagged
 * `<prefix>_commit` and is disabled while
 * `!commitEnabled` or [saving] (so a second tap or a keyboard action cannot fire twice). [commitHint] is a short
 * line under the button, typically `offlineHint("guardar")` (R-30), tagged `<prefix>_commit_hint`.
 * [secondaryAction] is an optional slot between the button and the hint (e.g. a destructive "Eliminar").
 *
 * Closing (X, back, scrim, swipe) only calls [onDismiss] and never [onCommit]: an edit is never applied silently
 * (R-10, no dialog, R-08). The sheet draws no state of its own; the caller owns the draft.
 *
 * Caller contract (the sheet neither hides itself on commit nor fires a haptic; R-13, R-29):
 * - [SheetCommit.Save]: set [saving] while the write runs. On success remove the sheet, perform
 *   `HapticEvent.Confirm` through `LocalAppHaptics` and show a message only when the change is not visible.
 *   On failure keep the sheet open, perform `HapticEvent.Reject`, show the error message (R-37) and clear [saving].
 * - [SheetCommit.Apply]: remove the sheet at once and fire no haptic; the screen underneath owns the "Guardar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Slots and flags fixed by the T-119 contract.
@Composable
fun AppEditSheet(
    title: String,
    commit: SheetCommit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    commitEnabled: Boolean = true,
    saving: Boolean = false,
    commitHint: String? = null,
    help: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
    testTagPrefix: String = "edit_sheet",
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val label = stringResource(
        when (commit) {
            SheetCommit.Save -> Res.string.action_save
            SheetCommit.Apply -> Res.string.action_done
            SheetCommit.Add -> Res.string.action_add
        },
    )
    AppBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        testTagPrefix = testTagPrefix,
    ) {
        AppSheetLayout(
            header = {
                AppSheetHeader(title, { closeThen(scope, sheetState, onDismiss) }, testTagPrefix, help)
            },
            footer = {
                PrimaryButton(
                    text = label,
                    onClick = { if (commitEnabled && !saving) onCommit() },
                    modifier = Modifier.fillMaxWidth().testTag(AppSheetsDefaults.commitTag(testTagPrefix)),
                    enabled = commitEnabled && !saving,
                    loading = saving,
                )
                secondaryAction?.let { action ->
                    Box(Modifier.fillMaxWidth().padding(top = Spacing.xs), contentAlignment = Alignment.Center) {
                        action()
                    }
                }
                if (commitHint != null) {
                    Text(
                        text = commitHint,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier
                            .padding(top = Spacing.xs)
                            .testTag(AppSheetsDefaults.commitHintTag(testTagPrefix)),
                    )
                }
            },
            body = content,
        )
    }
}

/**
 * Picker archetype (R-07): header and a scrolling body without a footer. The content applies on tap and the caller
 * removes the sheet; closing only calls [onDismiss]. [help] draws a "?" left of the X.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    help: (@Composable () -> Unit)? = null,
    testTagPrefix: String = "picker_sheet",
    content: @Composable ColumnScope.() -> Unit,
) = ReadOnlySheet(title, onDismiss, help, testTagPrefix, modifier, content)

/** Info archetype (R-07): header and a scrolling read-only body without a footer (help, explanations). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "info_sheet",
    content: @Composable ColumnScope.() -> Unit,
) = ReadOnlySheet(title, onDismiss, null, testTagPrefix, modifier, content)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // Shared body of the picker and info archetypes.
@Composable
private fun ReadOnlySheet(
    title: String,
    onDismiss: () -> Unit,
    help: (@Composable () -> Unit)?,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    AppBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        testTagPrefix = testTagPrefix,
    ) {
        AppSheetLayout(
            header = { AppSheetHeader(title, { closeThen(scope, sheetState, onDismiss) }, testTagPrefix, help) },
            footer = null,
            body = content,
        )
    }
}

/** [closeThen] with nothing to run in between (the X button). */
@OptIn(ExperimentalMaterial3Api::class)
internal fun closeThen(scope: CoroutineScope, sheetState: SheetState, onDismiss: () -> Unit) =
    closeThen(scope, sheetState, onDismiss) {}

/** Hides the sheet first so it animates out, runs [action], then calls [onDismiss]. */
@OptIn(ExperimentalMaterial3Api::class)
internal fun closeThen(scope: CoroutineScope, sheetState: SheetState, onDismiss: () -> Unit, action: () -> Unit) {
    action()
    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
}

/**
 * Shared header of every archetype: a `titleLarge` heading (omitted when [title] is null), the optional "?" [help]
 * slot and the X "Cerrar" (`<prefix>_close`), each a 48 dp target that keeps its size at large font scales.
 */
@Composable
internal fun AppSheetHeader(title: String?, onClose: () -> Unit, prefix: String, help: (@Composable () -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.screenMargin, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).testTag(AppSheetsDefaults.titleTag(prefix)).semantics { heading() },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        help?.invoke()
        AppIconButton(
            contentDescription = stringResource(Res.string.action_close),
            icon = Res.drawable.ic_close,
            testTag = AppSheetsDefaults.closeTag(prefix),
            onClick = onClose,
        )
    }
}

/**
 * Header, a body that scrolls and an optional [footer] outside the scroll, lifted above the keyboard and the
 * navigation bar, so the primary action stays visible with the IME open (R-09). Content is capped to the form
 * width and centred on wide windows. Window-free so a host test can drive it with a fixed height.
 */
@Composable
internal fun AppSheetLayout(
    header: @Composable () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)?,
    body: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxWidth()) {
            header()
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenMargin),
                content = body,
            )
            if (footer != null) {
                Column(
                    Modifier
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.screenMargin)
                        .padding(top = Spacing.md, bottom = Spacing.lg),
                    content = footer,
                )
            } else {
                Spacer(Modifier.navigationBarsPadding().height(Spacing.lg))
            }
        }
    }
}
