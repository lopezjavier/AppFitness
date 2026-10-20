package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Round icon-only button with a 48 dp touch target. The description sits on the button; the icon is decorative.
 * A disabled button is inert and its icon is dimmed to `ghostData`.
 */
@Composable
fun AppIconButton(
    contentDescription: String,
    icon: DrawableResource,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = AppTheme.colors.textPrimary,
) {
    Box(
        modifier
            .size(AppTopBarDefaults.NavigationTouchTarget)
            .clip(AppShapes.full)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) tint else AppTheme.colors.ghostData,
            modifier = Modifier.size(AppTopBarDefaults.NavigationIconSize),
        )
    }
}
