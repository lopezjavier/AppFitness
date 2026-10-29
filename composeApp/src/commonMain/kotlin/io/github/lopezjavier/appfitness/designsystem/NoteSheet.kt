package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.note_label
import io.github.lopezjavier.appfitness.composeapp.resources.note_too_long
import io.github.lopezjavier.appfitness.domain.model.NoteText
import org.jetbrains.compose.resources.stringResource

/** Room for a paste: the field never cuts the text, it flags more than [NoteText.MAX] characters. */
private const val FIELD_LIMIT = 1000
private const val NOTE_LINES = 3

/**
 * Note sheet (edit archetype, R-07): one multi-line "Nota" field and a pinned "Guardar". [draft] is the text being
 * typed, [original] the saved note it started from. "Guardar" is enabled when the draft fits in [NoteText.MAX]
 * characters and differs from [original] (clearing a saved note counts: it removes it). Over the limit the field shows
 * "Máximo 200 caracteres" under it. Closing discards the draft without a dialog. Tags: `<prefix>_field`,
 * `<prefix>_commit`, `<prefix>_close`.
 *
 * The caller owns the draft (a ViewModel or [NoteSheet]'s stateful overload) and follows the [AppEditSheet]
 * contract: close and fire the Confirm haptic after saving.
 */
@Suppress("LongParameterList") // Slots of the sheet contract.
@Composable
fun NoteSheet(
    title: String,
    draft: String,
    original: String?,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "note_sheet",
) {
    val tooLong = !NoteText.isValid(draft)
    AppEditSheet(
        title = title,
        commit = SheetCommit.Save,
        onCommit = onSave,
        onDismiss = onDismiss,
        modifier = modifier,
        commitEnabled = !tooLong && NoteText.normalise(draft) != original,
        testTagPrefix = testTagPrefix,
    ) {
        AppTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = stringResource(Res.string.note_label),
            modifier = Modifier.fillMaxWidth(),
            maxLength = FIELD_LIMIT,
            errorText = stringResource(Res.string.note_too_long).takeIf { tooLong },
            testTagPrefix = "${testTagPrefix}_field",
            imeAction = ImeAction.Default,
            singleLine = false,
            minLines = NOTE_LINES,
        )
    }
}

/**
 * [NoteSheet] that keeps its own draft (survives recreation, ADR-40), started from [initial]; [onSave] gets the
 * normalised note, null when it was cleared.
 */
@Composable
fun NoteSheet(
    title: String,
    initial: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "note_sheet",
) {
    var draft by rememberSaveable { mutableStateOf(initial.orEmpty()) }
    NoteSheet(
        title = title,
        draft = draft,
        original = initial,
        onDraftChange = { draft = it },
        onSave = { onSave(NoteText.normalise(draft)) },
        onDismiss = onDismiss,
        modifier = modifier,
        testTagPrefix = testTagPrefix,
    )
}
