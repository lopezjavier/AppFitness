package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flame_outlined
import io.github.lopezjavier.appfitness.composeapp.resources.ic_intention_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.ic_intention_muscle
import io.github.lopezjavier.appfitness.composeapp.resources.ic_intention_recomposition
import io.github.lopezjavier.appfitness.composeapp.resources.ic_intention_strength
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Stroke-only Outlined glyph that identifies this training intention (05 §6.0j). */
internal val TrainingIntention.icon: DrawableResource
    get() = when (this) {
        TrainingIntention.Strength -> Res.drawable.ic_intention_strength
        TrainingIntention.Muscle -> Res.drawable.ic_intention_muscle
        TrainingIntention.FatLoss -> Res.drawable.ic_flame_outlined
        TrainingIntention.Recomposition -> Res.drawable.ic_intention_recomposition
        TrainingIntention.Maintenance -> Res.drawable.ic_intention_maintenance
    }

/** The 24 dp intention glyph: `neonLime` when [selected] (R-64), `textSecondary` otherwise. */
@Composable
internal fun IntentionIcon(intention: TrainingIntention, selected: Boolean) {
    Icon(
        painter = painterResource(intention.icon),
        contentDescription = null,
        tint = if (selected) AppTheme.colors.neonLime else AppTheme.colors.textSecondary,
        modifier = Modifier.size(Spacing.xl),
    )
}
