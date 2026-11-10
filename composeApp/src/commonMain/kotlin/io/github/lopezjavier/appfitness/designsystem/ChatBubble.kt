@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

/** Sizes and colours of [ChatBubble]. */
object ChatBubbleDefaults {
    /** A bubble never takes more than this share of the row, so the two speakers stay visibly apart. */
    const val MAX_WIDTH_FRACTION = 0.85f

    val Shape = AppShapes.lg
    val Padding = Spacing.md

    /** The question sits on `surfaceOverlay`, the answer on the card surface. */
    fun container(appColors: AppColors, isQuestion: Boolean) =
        if (isQuestion) appColors.surfaceOverlay else appColors.surfaceRaised
}

/**
 * One message of a conversation as plain text. A question ([isQuestion]) is aligned to the end on `surfaceOverlay`;
 * an answer is aligned to the start on the card surface and its text is selectable so it can be copied. Both are at
 * most [ChatBubbleDefaults.MAX_WIDTH_FRACTION] of the available width. The [testTag] lands on the bubble.
 */
@Composable
fun ChatBubble(text: String, isQuestion: Boolean, modifier: Modifier = Modifier, testTag: String? = null) {
    val colors = AppTheme.colors
    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = if (isQuestion) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        val bubble = Modifier
            .widthIn(max = maxWidth * ChatBubbleDefaults.MAX_WIDTH_FRACTION)
            .let { if (testTag != null) it.semantics(mergeDescendants = true) {}.testTag(testTag) else it }
            .clip(ChatBubbleDefaults.Shape)
            .background(ChatBubbleDefaults.container(colors, isQuestion))
            .border(AppBorder.thin, colors.borderSubtle, ChatBubbleDefaults.Shape)
            .padding(ChatBubbleDefaults.Padding)
        Box(bubble) {
            val content: @Composable () -> Unit = {
                Text(text = text, style = AppTheme.typography.bodyLarge, color = colors.textPrimary)
            }
            if (isQuestion) content() else SelectionContainer { content() }
        }
    }
}

@Preview
@Composable
private fun ChatBubblePreview() {
    AppTheme {
        ChatBubble(text = "¿Cuántas series hago?", isQuestion = true)
    }
}
