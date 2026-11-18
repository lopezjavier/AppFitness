package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_arms
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_core
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_full_body
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_glutes
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_lower
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_pull
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_push
import io.github.lopezjavier.appfitness.composeapp.resources.ic_part_upper
import io.github.lopezjavier.appfitness.domain.model.RoutinePart
import org.jetbrains.compose.resources.DrawableResource

/** The single `RoutinePart -> drawable` mapping (R-125); no screen keeps its own `when`. */
object TrainingPartIcon {
    fun drawableFor(part: RoutinePart): DrawableResource = when (part) {
        RoutinePart.FullBody -> Res.drawable.ic_part_full_body
        RoutinePart.Upper -> Res.drawable.ic_part_upper
        RoutinePart.Lower -> Res.drawable.ic_part_lower
        RoutinePart.Push -> Res.drawable.ic_part_push
        RoutinePart.Pull -> Res.drawable.ic_part_pull
        RoutinePart.Arms -> Res.drawable.ic_part_arms
        RoutinePart.Glutes -> Res.drawable.ic_part_glutes
        RoutinePart.Core -> Res.drawable.ic_part_core
        RoutinePart.Cardio -> Res.drawable.ic_part_cardio
        RoutinePart.Generic -> Res.drawable.ic_fitness_center
    }
}

/** Decorative badge with the icon of a routine's training part: a 28 dp glyph in the 40 dp badge (R-125). */
@Composable
fun TrainingPartIcon(part: RoutinePart, isNext: Boolean, modifier: Modifier = Modifier) {
    IconBadge(
        icon = TrainingPartIcon.drawableFor(part),
        tint = if (isNext) AppTheme.colors.athleticCyan else AppTheme.colors.neonLime,
        modifier = modifier,
        size = IconBadgeDefaults.Size,
        iconSize = IconBadgeDefaults.LargeIconSize,
    )
}
