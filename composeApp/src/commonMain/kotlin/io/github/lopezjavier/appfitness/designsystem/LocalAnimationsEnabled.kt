package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether decorative motion (looping animations) may run. The platform provides `false` when the user turned
 * animations off (reduced motion); previews and tests default to `true`.
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }
