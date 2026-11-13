package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Sizes of the compact meal-card actions (R-75). */
object CompactActionsDefaults {
    /** Visible tonal circle of [CompactTonalIconButton]. */
    val CircleSize = 36.dp

    /** Icon inside the circle. */
    val IconSize = 20.dp

    /** Visible height of [CompactAddButton]. */
    val AddButtonHeight = 36.dp

    /** Maximum height of [CompactKcalPill]. */
    val PillMaxHeight = 28.dp
}

/**
 * Compact icon button (R-75, C-46): a 48 dp touch target around a 36 dp `surfaceControl` circle with a 20 dp icon.
 * The description sits on the button; long press shows it as a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTonalIconButton(
    contentDescription: String,
    icon: DrawableResource,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(modifier = Modifier.clearAndSetSemantics { this.testTag = "${testTag}_tooltip" }) {
                Text(contentDescription)
            }
        },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier
                .size(Spacing.minTouchTarget)
                .clip(AppShapes.full)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription }
                .testTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(CompactActionsDefaults.CircleSize).clip(AppShapes.full).background(colors.surfaceControl),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(CompactActionsDefaults.IconSize),
                )
            }
        }
    }
}

/**
 * Compact lime "+Añadir" button (R-75, C-46): a `neonLime` pill with `onNeonLime` text (AA), 36 dp visible inside a
 * 48 dp touch target. [contentDescription] names the object ("Añadir a Desayuno").
 */
@Composable
fun CompactAddButton(text: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier
            .defaultMinSize(minHeight = Spacing.minTouchTarget, minWidth = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = CompactActionsDefaults.AddButtonHeight)
                .clip(AppShapes.full)
                .background(colors.neonLime)
                .padding(horizontal = Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                color = colors.onNeonLime,
                maxLines = 1,
            )
        }
    }
}

/**
 * Compact kcal pill (R-75, C-47): `surfaceOverlay`, no border, at most 28 dp high, the figure in `bodyMedium` W600.
 * Read-only, merged into one node.
 */
@Composable
fun CompactKcalPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(max = CompactActionsDefaults.PillMaxHeight)
            .clip(AppShapes.full)
            .background(nestedContainerColor())
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
            color = AppTheme.colors.textPrimary,
            maxLines = 1,
        )
    }
}
