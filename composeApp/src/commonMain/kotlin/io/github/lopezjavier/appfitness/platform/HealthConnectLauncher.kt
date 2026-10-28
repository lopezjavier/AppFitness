package io.github.lopezjavier.appfitness.platform

import androidx.compose.runtime.Composable

/** The two Health Connect screens that need an Activity: the permission request and the Play Store offer. */
interface HealthConnectLauncher {
    fun requestPermissions()

    fun openInstall()

    companion object {
        /** Does nothing; the default for previews and tests that do not exercise the system screens. */
        val None = object : HealthConnectLauncher {
            override fun requestPermissions() = Unit

            override fun openInstall() = Unit
        }
    }
}

/**
 * Remembers a [HealthConnectLauncher]. [onResult] receives whether the weight read permission was granted once the
 * system permission screen closes.
 */
@Composable
expect fun rememberHealthConnectLauncher(onResult: (weightReadGranted: Boolean) -> Unit): HealthConnectLauncher
