package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_note_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_note_too_long
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_registered
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_resolve
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_resolve_offline
import io.github.lopezjavier.appfitness.composeapp.resources.live_discomfort_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_menu_discomfort_offline
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.rules.StrongPainTerms
import io.github.lopezjavier.appfitness.domain.usecase.LogDiscomfort
import org.jetbrains.compose.resources.stringResource

/** Room for a paste: the field never cuts the text, it flags more than [LogDiscomfort.MAX_NOTE] characters. */
private const val FIELD_LIMIT = 1000
private const val NOTE_LINES = 3

/**
 * "Registrar molestia" (edit archetype): an optional multi-line note and "Guardar", disabled over 200 characters or
 * offline. The draft survives recreation; closing without saving discards it.
 */
@Composable
internal fun DiscomfortSheet(position: Int, online: Boolean, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val tag = "live_exercise_${position}_discomfort"
    val tooLong = draft.trim().length > LogDiscomfort.MAX_NOTE
    AppEditSheet(
        title = stringResource(Res.string.live_discomfort_sheet_title),
        commit = SheetCommit.Save,
        onCommit = { onSave(draft.trim().ifEmpty { null }) },
        onDismiss = onDismiss,
        commitEnabled = online && !tooLong,
        commitHint = stringResource(Res.string.live_exercise_menu_discomfort_offline).takeIf { !online },
        testTagPrefix = "${tag}_sheet",
    ) {
        AppTextField(
            value = draft,
            onValueChange = { draft = it },
            label = stringResource(Res.string.live_discomfort_note_label),
            modifier = Modifier.fillMaxWidth(),
            maxLength = FIELD_LIMIT,
            errorText = stringResource(Res.string.live_discomfort_note_too_long).takeIf { tooLong },
            testTagPrefix = "${tag}_note",
            imeAction = ImeAction.Default,
            singleLine = false,
            minLines = NOTE_LINES,
        )
    }
}

/**
 * The open discomfort of an exercise under its heading: "Molestia registrada" with "Ya no me molesta" and, when the
 * stored note was a strong-pain one, the fixed health-professional message. Resolving needs the server.
 */
@Composable
internal fun DiscomfortBlock(
    position: Int,
    discomfort: LiveDiscomfort,
    online: Boolean,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tag = "live_exercise_${position}_discomfort"
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            InlineNote(
                text = stringResource(Res.string.live_discomfort_registered),
                modifier = Modifier.weight(1f),
                testTag = tag,
            )
            GhostButton(
                text = stringResource(Res.string.live_discomfort_resolve),
                onClick = onResolve,
                enabled = online,
                tone = GhostTone.Neutral,
                modifier = Modifier.testTag("${tag}_resolve"),
            )
        }
        if (!online) {
            Text(
                text = stringResource(Res.string.live_discomfort_resolve_offline),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("${tag}_resolve_hint"),
            )
        }
        if (discomfort.severe) InlineNote(text = StrongPainTerms.MESSAGE, testTag = "${tag}_severe")
    }
}
