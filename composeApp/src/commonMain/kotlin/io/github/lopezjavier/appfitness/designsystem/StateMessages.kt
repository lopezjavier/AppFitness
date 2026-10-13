@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds EmptyState, ErrorState and their shared helpers.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_error
import io.github.lopezjavier.appfitness.composeapp.resources.ic_inbox
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The call to action of an [EmptyState] or [ErrorState]: its visible (and spoken) [label] and click handler. */
@Immutable
data class AppStateAction(val label: String, val onClick: () -> Unit)

/** Colours of the icon, title and message of a state block. */
@Immutable
data class StateMessageColors(val icon: Color, val title: Color, val message: Color)

private object StateMessageMetrics {
    val IconSize: Dp = 48.dp

    /** Keeps the block centred, not stretched, on tablets. */
    val MaxContentWidth: Dp = 360.dp
    val ContentPadding: Dp = Spacing.xl

    /** Icon, title and message are 12 dp apart. */
    val ItemSpacing: Dp = Spacing.md

    /** Extra gap before the button, so message to button is 24 dp (the block gap). */
    val ActionTopSpacing: Dp = Spacing.md

    fun titleStyle(typography: AppTypography): TextStyle = typography.titleMedium

    fun messageStyle(typography: AppTypography): TextStyle = typography.bodyMedium.copy(textAlign = TextAlign.Center)
}

/** Sizes, colours and text styles of [EmptyState]. */
object EmptyStateDefaults {
    val IconSize: Dp = StateMessageMetrics.IconSize
    val MaxContentWidth: Dp = StateMessageMetrics.MaxContentWidth
    val ContentPadding: Dp = StateMessageMetrics.ContentPadding
    val ItemSpacing: Dp = StateMessageMetrics.ItemSpacing
    val ActionTopSpacing: Dp = StateMessageMetrics.ActionTopSpacing

    fun colors(appColors: AppColors): StateMessageColors = StateMessageColors(
        icon = appColors.textSecondary,
        title = appColors.textPrimary,
        message = appColors.textSecondary,
    )

    fun titleStyle(typography: AppTypography): TextStyle = StateMessageMetrics.titleStyle(typography)

    fun messageStyle(typography: AppTypography): TextStyle = StateMessageMetrics.messageStyle(typography)
}

/** Sizes, colours and text styles of [ErrorState]; the error icon is the only difference from the empty block. */
object ErrorStateDefaults {
    val IconSize: Dp = StateMessageMetrics.IconSize
    val MaxContentWidth: Dp = StateMessageMetrics.MaxContentWidth
    val ContentPadding: Dp = StateMessageMetrics.ContentPadding
    val ItemSpacing: Dp = StateMessageMetrics.ItemSpacing
    val ActionTopSpacing: Dp = StateMessageMetrics.ActionTopSpacing

    /** The error colour (#FFB4AB) reads AA on base and raised; the glyph also differs (never colour-only). */
    fun colors(appColors: AppColors): StateMessageColors = StateMessageColors(
        icon = appColors.error,
        title = appColors.textPrimary,
        message = appColors.textSecondary,
    )

    fun titleStyle(typography: AppTypography): TextStyle = StateMessageMetrics.titleStyle(typography)

    fun messageStyle(typography: AppTypography): TextStyle = StateMessageMetrics.messageStyle(typography)
}

/**
 * Centred "nothing here yet" block: decorative [icon], optional [title], [message] and an optional [action]
 * (primary button). With [testTagPrefix] the root gets the prefix and the children `<prefix>_title`,
 * `<prefix>_message` and `<prefix>_action`. The caller's [modifier] lands on the root.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: AppStateAction? = null,
    icon: DrawableResource = Res.drawable.ic_inbox,
    testTagPrefix: String? = null,
) {
    StateMessage(
        message = message,
        title = title,
        action = action,
        icon = icon,
        colors = EmptyStateDefaults.colors(AppTheme.colors),
        isError = false,
        testTagPrefix = testTagPrefix,
        modifier = modifier,
    )
}

/**
 * Centred "something failed" block with a required retry [action] (a failed load always offers a way out).
 * The text is a polite live region, so a screen reader announces an error that appears without stealing focus.
 * Tags work as in [EmptyState]. The caller's [modifier] lands on the root.
 */
@Composable
fun ErrorState(
    message: String,
    action: AppStateAction,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: DrawableResource = Res.drawable.ic_error,
    testTagPrefix: String? = null,
) {
    StateMessage(
        message = message,
        title = title,
        action = action,
        icon = icon,
        colors = ErrorStateDefaults.colors(AppTheme.colors),
        isError = true,
        testTagPrefix = testTagPrefix,
        modifier = modifier,
    )
}

// Private shared implementation: the public wrappers own the defaults.
@Suppress("LongParameterList", "ModifierWithoutDefault")
@Composable
private fun StateMessage(
    message: String,
    title: String?,
    action: AppStateAction?,
    icon: DrawableResource,
    colors: StateMessageColors,
    isError: Boolean,
    testTagPrefix: String?,
    modifier: Modifier,
) {
    val rootModifier = testTagPrefix?.let { modifier.testTag(it) } ?: modifier
    Box(modifier = rootModifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
            Modifier
                .widthIn(max = StateMessageMetrics.MaxContentWidth)
                .padding(StateMessageMetrics.ContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StateMessageMetrics.ItemSpacing),
        ) {
            // Decorative: the text carries the meaning.
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.icon,
                modifier = Modifier.size(StateMessageMetrics.IconSize),
            )
            Column(
                modifier = if (isError) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(StateMessageMetrics.ItemSpacing),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = StateMessageMetrics.titleStyle(AppTheme.typography),
                        color = colors.title,
                        textAlign = TextAlign.Center,
                        modifier = tagged(testTagPrefix, "title").semantics { heading() },
                    )
                }
                Text(
                    text = message,
                    style = StateMessageMetrics.messageStyle(AppTheme.typography),
                    color = colors.message,
                    modifier = tagged(testTagPrefix, "message"),
                )
            }
            if (action != null) {
                PrimaryButton(
                    text = action.label,
                    onClick = action.onClick,
                    modifier = tagged(testTagPrefix, "action").padding(top = StateMessageMetrics.ActionTopSpacing),
                )
            }
        }
    }
}

private fun tagged(prefix: String?, part: String): Modifier =
    childTestTag(prefix, part)?.let { Modifier.testTag(it) } ?: Modifier

private val PreviewAction = AppStateAction("Añadir alimento") {}
private val PreviewRetry = AppStateAction("Reintentar") {}
private const val PREVIEW_MESSAGE = "Añade tu primer alimento para empezar a contar calorías."
private const val PREVIEW_ERROR_MESSAGE = "Comprueba tu conexión e inténtalo de nuevo."

@Preview
@Composable
private fun EmptyStatePreview() {
    AppTheme {
        Column(Modifier.background(AppTheme.colors.surfaceBase)) {
            EmptyState(PREVIEW_MESSAGE, title = "Sin alimentos hoy", action = PreviewAction)
            EmptyState("Todavía no hay datos para este periodo.")
        }
    }
}

@Preview
@Composable
private fun ErrorStatePreview() {
    AppTheme {
        Box(Modifier.background(AppTheme.colors.surfaceBase)) {
            ErrorState(PREVIEW_ERROR_MESSAGE, PreviewRetry, title = "No se pudo cargar")
        }
    }
}

@Preview
@Composable
private fun StateMessageLargeFontPreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        AppTheme {
            Box(Modifier.background(AppTheme.colors.surfaceBase)) {
                ErrorState(PREVIEW_ERROR_MESSAGE, PreviewRetry, title = "No se pudo cargar")
            }
        }
    }
}
