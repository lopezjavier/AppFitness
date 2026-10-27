package io.github.lopezjavier.appfitness.platform

import androidx.compose.runtime.Composable

/** Opens the system-provided list of open-source licences (an external screen, not themed by the app). */
fun interface OpenSourceLicensesLauncher {
    fun open()

    companion object {
        /** Does nothing; the default for previews and tests that do not exercise the system screen. */
        val None = OpenSourceLicensesLauncher { }
    }
}

/** Remembers an [OpenSourceLicensesLauncher]; the list's title is the "Licencias de código abierto" label. */
@Composable
expect fun rememberOpenSourceLicensesLauncher(): OpenSourceLicensesLauncher
