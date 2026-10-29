package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_note
import io.github.lopezjavier.appfitness.composeapp.resources.note_edit
import io.github.lopezjavier.appfitness.composeapp.resources.note_set_add_cd
import io.github.lopezjavier.appfitness.composeapp.resources.note_set_edit_cd
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The note of a set under its row: a 48 dp icon button at the end ("Añadir nota a la serie N", or the pencil
 * "Editar nota de la serie N" when there is a note) and, when there is a note, the tappable [InlineNote] that opens
 * the same editor. [noteTag] lands on the shown note, [actionTag] on the icon.
 */
@Composable
internal fun SetNoteLine(
    setIndex: Int,
    notes: String?,
    onEdit: () -> Unit,
    noteTag: String,
    actionTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (notes != null) {
            InlineNote(
                text = notes,
                modifier = Modifier.weight(1f),
                testTag = noteTag,
                onClick = onEdit,
                onClickLabel = stringResource(Res.string.note_edit),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        AppIconButton(
            contentDescription = stringResource(
                if (notes == null) Res.string.note_set_add_cd else Res.string.note_set_edit_cd,
                setIndex,
            ),
            icon = if (notes == null) Res.drawable.ic_note else Res.drawable.ic_edit,
            testTag = actionTag,
            onClick = onEdit,
        )
    }
}
