package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_item
import org.jetbrains.compose.resources.stringResource

/**
 * The "add" action at the end of a list (R-19): a full-width [SecondaryButton] reading "Añadir <objectLabel>"
 * ("Añadir comida", "Añadir serie"). [objectLabel] is the lower-case noun; the caller owns the tag via [modifier].
 */
@Composable
fun AddItemButton(objectLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    SecondaryButton(
        text = stringResource(Res.string.add_item, objectLabel),
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
    )
}
