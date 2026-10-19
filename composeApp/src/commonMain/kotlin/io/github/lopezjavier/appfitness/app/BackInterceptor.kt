package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Lets a full-screen destination take over the back arrow of the shell's top bar (for example to ask before
 * discarding edits). The shell calls [intercept] first and only pops the destination when it returns false.
 */
@Stable
class BackInterceptor {
    private var handler: (() -> Unit)? = null

    internal fun register(handler: () -> Unit): () -> Unit {
        this.handler = handler
        return { if (this.handler === handler) this.handler = null }
    }

    /** True when a destination handled the back press (the shell must not pop). */
    fun intercept(): Boolean = handler?.also { it() } != null
}

/** The shell's [BackInterceptor]; null outside the shell (previews, isolated screen tests). */
val LocalBackInterceptor = compositionLocalOf<BackInterceptor?> { null }

/** While [enabled], the top bar's back arrow calls [onBack] instead of popping the destination. */
@Composable
fun InterceptTopBarBack(enabled: Boolean, onBack: () -> Unit) {
    val interceptor = LocalBackInterceptor.current
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(interceptor, enabled) {
        val unregister = if (enabled) interceptor?.register { currentOnBack() } else null
        onDispose { unregister?.invoke() }
    }
}
