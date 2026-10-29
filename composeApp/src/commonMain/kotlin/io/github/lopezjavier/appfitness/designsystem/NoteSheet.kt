package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.note_detail_optional
import io.github.lopezjavier.appfitness.composeapp.resources.note_detail_required
import io.github.lopezjavier.appfitness.composeapp.resources.note_too_long
import io.github.lopezjavier.appfitness.domain.model.Note
import io.github.lopezjavier.appfitness.domain.model.NoteKind
import io.github.lopezjavier.appfitness.domain.model.NoteText
import org.jetbrains.compose.resources.stringResource

/** Room for a paste: the field never cuts the text, it flags more than [NoteText.MAX] characters. */
private const val FIELD_LIMIT = 1000
private const val NOTE_LINES = 3

/** One choice of a [NoteSheet]: the [kind] it stores and the Spanish [label] it shows. */
@Immutable
data class NoteSheetOption<K : NoteKind>(val kind: K, val label: String)

/** The "?" of a [NoteSheet]: what each option means (R-32, R-46). */
@Immutable
data class NoteSheetHelp(val contentDescription: String, val title: String, val content: HelpContent)

/**
 * Note sheet (edit archetype, R-07): a single-choice list of [options], a "?" help sheet beside the title, an always
 * visible detail field (optional, required for the option that [NoteKind.requiresText]) and a pinned "Guardar".
 * [kind] and [draft] are the pair being edited, [originalKind] and [original] the saved note it started from. "Guardar"
 * is enabled when the pair is a valid note (the text fits in [NoteText.MAX]) that differs from the saved one;
 * tapping the chosen option again clears the choice (a saved note is removed by clearing it). Closing discards the
 * draft without a dialog. Tags: `<prefix>_option_<wire>`, `<prefix>_help`, `<prefix>_text`, `<prefix>_commit`,
 * `<prefix>_close`.
 *
 * The caller owns the draft (a ViewModel or the stateful overload) and follows the [AppEditSheet] contract: close and
 * fire the Confirm haptic after saving.
 */
@Suppress("LongParameterList") // Slots of the sheet contract.
@Composable
fun <K : NoteKind> NoteSheet(
    title: String,
    options: List<NoteSheetOption<K>>,
    kind: K?,
    draft: String,
    originalKind: K?,
    original: String?,
    help: NoteSheetHelp,
    onKindChange: (K?) -> Unit,
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
        commitEnabled = Note.isValid(kind, draft) && (kind != originalKind || NoteText.normalise(draft) != original),
        help = {
            HelpButton(
                contentDescription = help.contentDescription,
                title = help.title,
                content = help.content,
                testTag = "${testTagPrefix}_help",
            )
        },
        testTagPrefix = testTagPrefix,
    ) {
        Column(
            Modifier.selectableGroup().testTag("${testTagPrefix}_options"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            options.forEach { option ->
                val selected = option.kind == kind
                ScaleOptionRow(
                    label = option.label,
                    color = AppTheme.colors.athleticCyan,
                    selected = selected,
                    onClick = { onKindChange(if (selected) null else option.kind) },
                    testTag = "${testTagPrefix}_option_${option.kind.wire}",
                    contentDescription = option.label,
                )
            }
        }
        AppTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = stringResource(
                if (kind?.requiresText == true) Res.string.note_detail_required else Res.string.note_detail_optional,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            maxLength = FIELD_LIMIT,
            errorText = stringResource(Res.string.note_too_long).takeIf { tooLong },
            testTagPrefix = "${testTagPrefix}_text",
            imeAction = ImeAction.Default,
            singleLine = false,
            minLines = NOTE_LINES,
        )
    }
}

/**
 * [NoteSheet] that keeps its own draft (survives recreation, ADR-40), started from [initialKind] and [initialText];
 * [onSave] gets the normalised note, both null when it was cleared.
 */
@Suppress("LongParameterList") // Slots of the sheet contract.
@Composable
fun <K : NoteKind> NoteSheet(
    title: String,
    options: List<NoteSheetOption<K>>,
    initialKind: K?,
    initialText: String?,
    help: NoteSheetHelp,
    onSave: (K?, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "note_sheet",
) {
    // The kind is saved by its wire name (ADR-40).
    var wire by rememberSaveable { mutableStateOf(initialKind?.wire) }
    var draft by rememberSaveable { mutableStateOf(initialText.orEmpty()) }
    val kind = options.firstOrNull { it.kind.wire == wire }?.kind
    NoteSheet(
        title = title,
        options = options,
        kind = kind,
        draft = draft,
        originalKind = initialKind,
        original = NoteText.normalise(initialText),
        help = help,
        onKindChange = { wire = it?.wire },
        onDraftChange = { draft = it },
        onSave = {
            val note = Note.of(kind, draft)
            onSave(note?.kind, note?.text)
        },
        onDismiss = onDismiss,
        modifier = modifier,
        testTagPrefix = testTagPrefix,
    )
}
