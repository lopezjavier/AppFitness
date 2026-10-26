package io.github.lopezjavier.appfitness.platform

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import io.github.lopezjavier.appfitness.designsystem.AppHaptics
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics

/** [AppHaptics] on `View.performHapticFeedback`: respects the system touch-feedback setting. */
class AndroidAppHaptics(private val view: View, private val sdkInt: Int = Build.VERSION.SDK_INT) : AppHaptics {
    override fun armed() = perform(HapticEvent.Armed)

    override fun disarmed() = perform(HapticEvent.Disarmed)

    override fun confirm() = perform(HapticEvent.Confirm)

    override fun reject() = perform(HapticEvent.Reject)

    override fun tick() = perform(HapticEvent.Tick)

    private fun perform(event: HapticEvent) {
        view.performHapticFeedback(AppHapticsPlan.constantFor(event, sdkInt))
    }
}

/** Provides [LocalAppHaptics] backed by an [AndroidAppHaptics] bound to the current window's view. */
@Composable
fun ProvideAppHaptics(content: @Composable () -> Unit) {
    val view = LocalView.current
    val haptics = remember(view) { AndroidAppHaptics(view) }
    CompositionLocalProvider(LocalAppHaptics provides haptics, content = content)
}
