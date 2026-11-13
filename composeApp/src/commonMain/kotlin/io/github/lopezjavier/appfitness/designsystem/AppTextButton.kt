package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Tone of an [AppTextButton]: the neutral text action or the destructive one (R-70). */
enum class AppTextButtonTone { Neutral, Danger }

/**
 * Text-style action (no fill, no border), at least 48 dp high, label in `bodyLarge` W600. [AppTextButtonTone.Danger]
 * paints it with the error colour: the destructive action of an edit sheet (R-70) that must sit at least 16 dp under
 * the filled primary button. An optional 20 dp [leadingIcon] precedes the label.
 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: AppTextButtonTone = AppTextButtonTone.Neutral,
    leadingIcon: DrawableResource? = null,
) {
    val colors = AppTheme.colors
    val color: Color = when {
        !enabled -> colors.ghostData
        tone == AppTextButtonTone.Danger -> colors.error
        else -> colors.textPrimary
    }
    Row(
        modifier
            .defaultMinSize(minHeight = Spacing.minTouchTarget, minWidth = Spacing.minTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(painterResource(leadingIcon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(text, style = AppTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W600), color = color)
    }
}
