@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage

/** Timing and tags of [ExerciseAnimation]. */
object ExerciseAnimationDefaults {
    /** One full loop: start pose, fade to the peak pose, peak pose, fade back. */
    const val CYCLE_MILLIS = 3000

    /** Pause on each pose. */
    const val HOLD_MILLIS = 500

    /** Crossfade between the poses. */
    const val FADE_MILLIS = 1000

    fun animationTag(prefix: String): String = "${prefix}_animation"

    fun startTag(prefix: String): String = "${prefix}_start"

    fun peakTag(prefix: String): String = "${prefix}_peak"
}

/**
 * Looping demonstration of an exercise: the start pose crossfades to the [peakUri] pose and back, pausing on each.
 * With [animationsEnabled] false (reduced motion), or without a [peakUri] (single-image exercises), only the start
 * pose is drawn and nothing moves. The fade animates alpha in a graphics layer, so it never recomposes the screen.
 * Tags: `<prefix>_animation` (root, carries [contentDescription]), `_start` and `_peak`.
 */
@Composable
fun ExerciseAnimation(
    startUri: String,
    peakUri: String?,
    animationsEnabled: Boolean,
    testTagPrefix: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(AppShapes.md)
            .background(AppTheme.colors.surfaceOverlay)
            .testTag(ExerciseAnimationDefaults.animationTag(testTagPrefix))
            .semantics { this.contentDescription = contentDescription },
    ) {
        AsyncImage(
            model = startUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().testTag(ExerciseAnimationDefaults.startTag(testTagPrefix)),
        )
        if (animationsEnabled && peakUri != null) {
            val transition = rememberInfiniteTransition(label = "exercise_animation")
            val peakAlpha =
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 0f,
                    animationSpec =
                    infiniteRepeatable(
                        animation =
                        keyframes {
                            val hold = ExerciseAnimationDefaults.HOLD_MILLIS
                            val fade = ExerciseAnimationDefaults.FADE_MILLIS
                            durationMillis = ExerciseAnimationDefaults.CYCLE_MILLIS
                            0f at 0 using FastOutSlowInEasing
                            0f at hold using FastOutSlowInEasing
                            1f at hold + fade using FastOutSlowInEasing
                            1f at hold + fade + hold using FastOutSlowInEasing
                            0f at durationMillis using FastOutSlowInEasing
                        },
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "peak_alpha",
                )
            AsyncImage(
                model = peakUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = peakAlpha.value }
                    .testTag(ExerciseAnimationDefaults.peakTag(testTagPrefix)),
            )
        }
    }
}
