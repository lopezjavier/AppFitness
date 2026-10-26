@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_photo_camera
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Sizes of an [ActionRow]. */
object ActionRowDefaults {
    val MinHeight: Dp = 48.dp
    val IconSize: Dp = 24.dp
    val IconTextGap: Dp = Spacing.lg
    val VerticalPadding: Dp = Spacing.sm
}

/**
 * Tappable row of a choice sheet: decorative [icon], [title] (`titleMedium`) and an optional one-line [hint]
 * (`bodyMedium`, `textSecondary`; null for a title-only row). At least 48 dp tall, `Role.Button`, and read as one
 * node ("title, hint"). [testTag] lands on the row.
 */
@Composable
fun ActionRow(
    icon: DrawableResource,
    title: String,
    hint: String?,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ActionRowDefaults.MinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.screenMargin, vertical = ActionRowDefaults.VerticalPadding)
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(ActionRowDefaults.IconTextGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = AppTheme.colors.textPrimary,
            modifier = Modifier.size(ActionRowDefaults.IconSize),
        )
        Column(Modifier.weight(1f)) {
            Text(text = title, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            if (hint != null) {
                Text(text = hint, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            }
        }
    }
}

@Preview
@Composable
private fun ActionRowPreview() {
    AppTheme {
        ActionRow(Res.drawable.ic_photo_camera, "Hacer foto", "Usa la cámara", onClick = {}, testTag = "preview")
    }
}
