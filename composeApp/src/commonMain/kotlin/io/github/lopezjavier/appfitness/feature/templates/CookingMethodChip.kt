package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.cooking_chip_cd
import io.github.lopezjavier.appfitness.designsystem.AppButtonChip
import io.github.lopezjavier.appfitness.designsystem.labelRes
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import org.jetbrains.compose.resources.stringResource

/**
 * The compact chip with the cooking [method] of a food ("Crudo", "A la plancha"...). A button: it opens the selector
 * of that food. Highlighted when the food is cooked; spoken as "Cocinado: A la plancha. Cambiar".
 */
@Composable
internal fun CookingMethodChip(method: CookingMethod, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(method.labelRes())
    AppButtonChip(
        label = label,
        onClick = onClick,
        modifier = modifier,
        highlighted = method != CookingMethod.Raw,
        contentDescription = stringResource(Res.string.cooking_chip_cd, label),
    )
}
