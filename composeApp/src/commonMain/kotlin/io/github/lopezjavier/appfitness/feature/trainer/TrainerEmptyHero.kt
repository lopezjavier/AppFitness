package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.DrawableResource

/**
 * The art of the Trainer empty states: a large lit icon badge, an optional [title], the [message] and the value
 * [bullets] (each with a lit check) that sell the feature. The caller puts the call to action below.
 */
@Composable
internal fun TrainerEmptyHero(
    icon: DrawableResource,
    message: String,
    bullets: List<String>,
    modifier: Modifier = Modifier,
    title: String? = null,
    tagPrefix: String? = null,
) {
    fun tagged(suffix: String): Modifier = tagPrefix?.let { Modifier.testTag("${it}_$suffix") } ?: Modifier
    Column(
        (tagPrefix?.let { modifier.testTag(it) } ?: modifier).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        IconBadge(
            icon,
            AppTheme.colors.neonLime,
            size = IconBadgeDefaults.LargeSize + Spacing.lg,
            iconSize = IconBadgeDefaults.LargeIconSize + Spacing.xs,
        )
        if (title != null) {
            Text(
                text = title,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = tagged("title").semantics { heading() },
            )
        }
        Text(
            text = message,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = tagged("message"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            bullets.forEach { bullet ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        Res.drawable.ic_check,
                        AppTheme.colors.electricGreen,
                        size = Spacing.xl,
                        iconSize = Spacing.md + Spacing.xs,
                    )
                    Text(text = bullet, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
                }
            }
        }
    }
}
