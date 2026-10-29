package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_note
import io.github.lopezjavier.appfitness.composeapp.resources.note_set_add_cd
import io.github.lopezjavier.appfitness.composeapp.resources.note_set_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.note_set_state_with_note
import org.jetbrains.compose.resources.stringResource

/**
 * The note action of a set row (R-49): a 48 dp icon button for the trailing slot of the row, never a line of its own.
 * It reads "Añadir nota a la serie N", or "Editar nota de la serie N" with the state "Con nota" when [hasNote].
 */
@Composable
fun SetNoteButton(
    setNumber: Int,
    hasNote: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val withNote = stringResource(Res.string.note_set_state_with_note)
    AppIconButton(
        contentDescription = stringResource(
            if (hasNote) Res.string.note_set_edit_cd else Res.string.note_set_add_cd,
            setNumber,
        ),
        icon = if (hasNote) Res.drawable.ic_edit else Res.drawable.ic_note,
        testTag = testTag,
        onClick = onClick,
        modifier = modifier.semantics { if (hasNote) stateDescription = withNote },
    )
}
