@file:Suppress("MatchingDeclarationName") // The file holds AppIconButton and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_badge_overflow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Limits of [AppIconButton]'s count badge. */
object AppIconButtonDefaults {
    /** Counts above this show "9+" in the badge. */
    const val MAX_BADGE_COUNT = 9
}

/** The text of a count badge: the number, or [overflow] ("9+") past [AppIconButtonDefaults.MAX_BADGE_COUNT]. */
internal fun badgeLabel(count: Int, overflow: String): String =
    if (count > AppIconButtonDefaults.MAX_BADGE_COUNT) overflow else count.toString()

/** Tag of the count badge of an [AppIconButton]. */
const val ICON_BUTTON_BADGE_TAG = "icon_button_badge"

@Composable
private fun CountBadge(count: Int) {
    val text = badgeLabel(count, stringResource(Res.string.notifications_badge_overflow))
    Badge(
        modifier = Modifier.clearAndSetSemantics { testTag = ICON_BUTTON_BADGE_TAG },
        containerColor = AppTheme.colors.neonLime,
        contentColor = AppTheme.colors.onNeonLime,
    ) { Text(text, style = AppTheme.typography.bodySmall) }
}

/**
 * Round icon-only button with a 48 dp touch target. The description sits on the button; the icon is decorative.
 * A disabled button is inert and its icon is dimmed to `ghostData`. Long press shows the description as a tooltip.
 * [badgeCount] above zero draws a count badge on the icon ("9+" past [AppIconButtonDefaults.MAX_BADGE_COUNT]); the
 * caller puts the exact count in [contentDescription], the badge itself is hidden from accessibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconButton(
    contentDescription: String,
    icon: DrawableResource,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AppTheme.colors.textPrimary,
    badgeCount: Int = 0,
) {
    val button: @Composable () -> Unit = {
        Box(
            modifier
                .size(AppTopBarDefaults.NavigationTouchTarget)
                .clip(AppShapes.full)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription }
                .testTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            val glyph: @Composable () -> Unit = {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (enabled) tint else AppTheme.colors.ghostData,
                    modifier = Modifier.size(AppTopBarDefaults.NavigationIconSize),
                )
            }
            if (badgeCount > 0) {
                BadgedBox(badge = { CountBadge(badgeCount) }, content = { glyph() })
            } else {
                glyph()
            }
        }
    }
    if (!enabled) {
        button()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                modifier = Modifier.clearAndSetSemantics {
                    this.testTag = "${testTag}_tooltip"
                },
            ) { Text(contentDescription) }
        },
        state = rememberTooltipState(),
        content = button,
    )
}
