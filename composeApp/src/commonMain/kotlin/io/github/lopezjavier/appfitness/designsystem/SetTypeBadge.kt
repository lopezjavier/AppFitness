@file:Suppress("MatchingDeclarationName") // The file holds the component and its defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_drop
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_failure
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_letter_drop
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_letter_failure
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_letter_warm_up
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_normal
import io.github.lopezjavier.appfitness.composeapp.resources.set_type_warm_up
import io.github.lopezjavier.appfitness.domain.model.SetType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Colours and text sources of the [SetTypeBadge]; neutral and semantic tokens only (never the macro colours). */
object SetTypeBadgeDefaults {
    /** The letter of a type, or null for Normal (it shows the set number). */
    fun letterResource(type: SetType): StringResource? = when (type) {
        SetType.Working -> null
        SetType.WarmUp -> Res.string.set_type_letter_warm_up
        SetType.Failure -> Res.string.set_type_letter_failure
        SetType.Drop -> Res.string.set_type_letter_drop
    }

    fun labelResource(type: SetType): StringResource = when (type) {
        SetType.Working -> Res.string.set_type_normal
        SetType.WarmUp -> Res.string.set_type_warm_up
        SetType.Failure -> Res.string.set_type_failure
        SetType.Drop -> Res.string.set_type_drop
    }

    /** Container and content colour: tone plus letter tell the types apart. */
    fun colors(type: SetType, appColors: AppColors): Pair<Color, Color> = when (type) {
        SetType.Working -> appColors.surfaceOverlay to appColors.textSecondary
        SetType.WarmUp -> appColors.borderStrong to appColors.textPrimary
        SetType.Failure -> appColors.errorContainer to appColors.onErrorContainer
        SetType.Drop -> appColors.textPrimary to appColors.surfaceBase
    }
}

/**
 * Index slot of a set row. Normal shows the set [number]; Calentamiento, Fallo and Drop show "C", "F" and "D" with
 * their own tone, and the full label as description. With [onClick] the badge is a 48 dp button (the builder opens
 * the type picker); without it, it is display only. The glyph box is tagged `set_type_badge`.
 */
@Composable
fun SetTypeBadge(
    type: SetType,
    number: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
) {
    val label = stringResource(SetTypeBadgeDefaults.labelResource(type))
    val letter = SetTypeBadgeDefaults.letterResource(type)?.let { stringResource(it) } ?: number
    val (container, content) = SetTypeBadgeDefaults.colors(type, AppTheme.colors)
    val glyph: @Composable () -> Unit = {
        Box(
            Modifier
                .size(SetRowDefaults.IndexBadgeSize)
                .clip(AppShapes.sm)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                style = AppTheme.typography.labelTechnical.copy(fontWeight = FontWeight.W700),
                color = content,
                maxLines = 1,
                modifier = Modifier.testTag("set_type_badge"),
            )
        }
    }
    if (onClick == null) {
        Box(
            modifier.then(
                if (type == SetType.Working) Modifier else Modifier.semantics { contentDescription = label },
            ),
            contentAlignment = Alignment.Center,
        ) { glyph() }
    } else {
        Box(
            modifier
                .widthIn(min = Spacing.minTouchTarget)
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
                .semantics { contentDescription = clickLabel ?: label },
            contentAlignment = Alignment.Center,
        ) { glyph() }
    }
}

@Preview
@Composable
private fun SetTypeBadgePreview() {
    AppTheme {
        Box {
            SetTypeBadge(SetType.WarmUp, "1")
        }
    }
}
