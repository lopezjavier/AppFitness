@file:Suppress("MatchingDeclarationName", "ktlint:standard:property-naming") // Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/** Timing of the one-time fill of segments and bars (C-119, R-97). */
object FillMotionDefaults {
    const val DurationMillis: Int = 300
    const val StaggerMillis: Int = 40

    /** Easing (0.05, 0.7, 0.1, 1.0). */
    val Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/**
 * A 0..1 value that rises once on first composition (300 ms, 40 ms stagger per [staggerIndex]); later recompositions
 * of the same screen do not replay it. It jumps to 1 at once when decorative motion is off ("remove animations").
 * Read it inside a draw or layout lambda where possible.
 */
@Composable
fun rememberFillProgress(staggerIndex: Int = 0): State<Float> {
    val enabled = LocalAnimationsEnabled.current
    val animatable = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) {
            delay((staggerIndex * FillMotionDefaults.StaggerMillis).toLong())
            animatable.animateTo(1f, tween(FillMotionDefaults.DurationMillis, easing = FillMotionDefaults.Easing))
        }
    }
    return animatable.asState()
}
