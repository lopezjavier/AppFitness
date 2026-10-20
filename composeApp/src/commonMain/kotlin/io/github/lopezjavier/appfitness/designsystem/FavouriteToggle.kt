@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_star
import io.github.lopezjavier.appfitness.composeapp.resources.ic_star_filled
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Sizes and colour mapping of [FavouriteToggle]. */
object FavouriteToggleDefaults {
    val TouchTarget = AppTopBarDefaults.NavigationTouchTarget
    val IconSize = AppTopBarDefaults.NavigationIconSize

    /** Lime when selected, secondary text when not (amber is reserved for fat). */
    fun tint(selected: Boolean, enabled: Boolean, appColors: AppColors): Color = when {
        !enabled -> appColors.ghostData
        selected -> appColors.neonLime
        else -> appColors.textSecondary
    }

    /** Filled star when selected, outline otherwise: the state never relies on colour alone. */
    fun icon(selected: Boolean): DrawableResource = if (selected) Res.drawable.ic_star_filled else Res.drawable.ic_star
}

/**
 * Star button that marks something as a favourite. [selected] is exposed as the toggle state and
 * [contentDescription] should say the action ("Añadir a favoritos" / "Quitar de favoritos"). 48 dp touch target.
 * The caller's [modifier] (tag included) lands on the toggleable node.
 */
@Composable
fun FavouriteToggle(
    selected: Boolean,
    onToggle: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(FavouriteToggleDefaults.TouchTarget)
            .clip(AppShapes.full)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onToggle() }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(FavouriteToggleDefaults.icon(selected)),
            contentDescription = null,
            tint = FavouriteToggleDefaults.tint(selected, enabled, AppTheme.colors),
            modifier = Modifier.size(FavouriteToggleDefaults.IconSize),
        )
    }
}

@Preview
@Composable
private fun FavouriteTogglePreview() {
    AppTheme {
        Row {
            FavouriteToggle(selected = false, onToggle = {}, contentDescription = "Añadir a favoritos")
            FavouriteToggle(selected = true, onToggle = {}, contentDescription = "Quitar de favoritos")
            FavouriteToggle(selected = false, onToggle = {}, contentDescription = "Añadir a favoritos", enabled = false)
        }
    }
}
