package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.action_done
import io.github.lopezjavier.appfitness.designsystem.AppDatePickerDialog
import io.github.lopezjavier.appfitness.domain.model.ActiveWeekExtension
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** The date picker of the week's last day: selectable from `minDay` to `maxDay`, starting on the current last day. */
@Composable
internal fun ExtendWeekPicker(extension: ActiveWeekExtension, extend: ExtendWeekUi) {
    AppDatePickerDialog(
        selected = extension.selected,
        onConfirm = extend.onConfirm,
        onDismiss = extend.onDismiss,
        confirmLabel = stringResource(Res.string.action_done),
        dismissLabel = stringResource(Res.string.action_cancel),
        maxDate = extension.maxDay,
        minDate = extension.minDay,
        testTagPrefix = "trainer_week_extend_picker",
    )
}

/**
 * State of the active week's last-day picker (US-134): [extension] is null when nothing can be extended; the picker
 * is [pickerOpen]; disabled offline or while [busy].
 */
internal data class ExtendWeekUi(
    val extension: ActiveWeekExtension? = null,
    val pickerOpen: Boolean = false,
    val busy: Boolean = false,
    val online: Boolean = true,
    val onOpen: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onConfirm: (LocalDate) -> Unit = {},
)
