package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.Immutable
import io.github.lopezjavier.appfitness.config.AppConfig

/**
 * Feature gates resolved from [AppConfig] (ADR-19). The default is everything off, so previews
 * and tests fail closed.
 */
@Immutable
data class AppFeatures(val componentCatalog: Boolean = false) {
    companion object {
        fun from(config: AppConfig): AppFeatures = AppFeatures(componentCatalog = config.isLocal)
    }
}

/** Whether [route] can be reached. Exhaustive on purpose: a new route must declare its availability. */
fun AppFeatures.isAvailable(route: AppRoute): Boolean = when (route) {
    is TabRoute -> true
    ProfileRoute -> true
    CatalogRoute -> componentCatalog
}
