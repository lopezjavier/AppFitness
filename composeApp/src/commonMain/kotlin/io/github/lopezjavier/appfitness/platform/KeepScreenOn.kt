package io.github.lopezjavier.appfitness.platform

import androidx.compose.runtime.Composable

/**
 * Keeps the screen on while this composable is in the composition and releases it when it leaves (never a wake lock).
 * Show it only while a workout is running.
 */
@Composable
expect fun KeepScreenOn()
