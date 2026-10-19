package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.Immutable
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials

/**
 * Feature gates resolved from [AppConfig] (ADR-19). The default is everything off, so previews
 * and tests fail closed. [testLogin] (the Welcome "Test login" button) is non-null only in the `local` flavor;
 * it is not a route, so [isAvailable] ignores it.
 */
@Immutable
data class AppFeatures(val componentCatalog: Boolean = false, val testLogin: TestLoginCredentials? = null) {
    companion object {
        fun from(config: AppConfig): AppFeatures =
            AppFeatures(componentCatalog = config.isLocal, testLogin = config.testLogin)
    }
}

/** Whether [route] can be reached. Exhaustive on purpose: a new route must declare its availability. */
fun AppFeatures.isAvailable(route: AppRoute): Boolean = when (route) {
    is TabRoute -> true
    CatalogRoute -> componentCatalog
    PreferencesRoute -> true
    is ExercisePickerRoute, is ExerciseDetailRoute, is RoutineBuilderRoute, LiveSessionRoute -> true
    HistoryRoute, is SessionDetailRoute -> true
}
