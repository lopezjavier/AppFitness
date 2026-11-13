package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height of the fade that lets content slide under a pinned footer (C-27). */
val FooterFadeHeight: Dp = 16.dp

/**
 * The top edge of a pinned footer (C-27): a hairline on its top edge and a [FooterFadeHeight] fade over the content
 * above it, from transparent to [surface], drawn only while [visible] (the content can still scroll under the footer).
 * Draws outside the footer bounds, so apply it to the footer's own container.
 */
fun Modifier.footerEdge(hairline: Color, surface: Color, visible: Boolean = true): Modifier = if (!visible) {
    this
} else {
    drawBehind {
        val fade = FooterFadeHeight.toPx()
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, surface), startY = -fade, endY = 0f),
            topLeft = Offset(0f, -fade),
            size = Size(size.width, fade),
        )
        val stroke = AppBorder.thin.toPx()
        drawLine(hairline, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), strokeWidth = stroke)
    }
}
