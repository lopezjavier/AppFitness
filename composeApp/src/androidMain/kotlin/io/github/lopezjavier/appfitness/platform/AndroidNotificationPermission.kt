package io.github.lopezjavier.appfitness.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.lopezjavier.appfitness.domain.port.NotificationPermission
import kotlinx.coroutines.CompletableDeferred

/**
 * [NotificationPermission] for `POST_NOTIFICATIONS` (runtime permission on Android 13+, always granted below). The
 * Activity owns the result launcher and hands it over with [attach]; without an attached launcher a request answers
 * "not granted" instead of waiting.
 */
class AndroidNotificationPermission(private val context: Context) : NotificationPermission {
    private var launch: (() -> Unit)? = null
    private var pending: CompletableDeferred<Boolean>? = null

    override fun isGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override suspend fun request(): Boolean {
        val start = launch
        return when {
            isGranted() -> true

            start == null -> false

            else -> {
                val answer = CompletableDeferred<Boolean>().also { pending = it }
                start()
                answer.await()
            }
        }
    }

    /** Called by the Activity once its permission launcher exists; [launch] opens the system dialog. */
    fun attach(launch: () -> Unit) {
        this.launch = launch
    }

    /** Called by the Activity when it goes away: a request in flight answers "not granted". */
    fun detach() {
        launch = null
        onResult(false)
    }

    /** The system dialog answered. */
    fun onResult(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }
}
