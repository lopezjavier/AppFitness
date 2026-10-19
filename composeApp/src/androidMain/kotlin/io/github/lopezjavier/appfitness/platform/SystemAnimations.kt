package io.github.lopezjavier.appfitness.platform

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled

/** Animations are on unless the user set the system animator scale to 0 ("Remove animations"). */
internal fun animationsEnabledFor(animatorDurationScale: Float): Boolean = animatorDurationScale != 0f

/** Provides [LocalAnimationsEnabled] from `Settings.Global.ANIMATOR_DURATION_SCALE` (reduced motion). */
@Composable
fun ProvideSystemAnimations(content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val enabled =
        remember(resolver) {
            animationsEnabledFor(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
        }
    CompositionLocalProvider(LocalAnimationsEnabled provides enabled, content = content)
}
