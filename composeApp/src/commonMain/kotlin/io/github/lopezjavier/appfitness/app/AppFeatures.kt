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
@Suppress("CyclomaticComplexMethod") // One branch per route, on purpose.
fun AppFeatures.isAvailable(route: AppRoute): Boolean = when (route) {
    is TabRoute -> true
    CatalogRoute -> componentCatalog
    PreferencesRoute, is PreferencesSectionRoute, AccountRoute, AboutRoute, HealthDiagnosticsRoute -> true
    is ExercisePickerRoute, is ExerciseDetailRoute, is RoutineBuilderRoute, LiveSessionRoute -> true
    is RoutineDetailsRoute -> true
    HistoryRoute, is SessionDetailRoute -> true
    is EditWorkoutRoute, is PastWorkoutRoute -> true
    StrengthRoute, MuscleVolumeRoute, StatsRoute, is ExerciseProgressRoute -> true
    is FoodSearchRoute, is AddFoodRoute, is CreateFoodRoute, is EditFoodRoute, MealSlotsRoute, is EditEntryRoute -> true
    MealDefaultsRoute -> true
    is SaveTemplateRoute -> true
    is TemplateDetailsRoute -> true
    DiaryHistoryRoute, is DiaryDayRoute, is WeightRoute, WeightHistoryRoute -> true
    NotificationCenterRoute, CheckInRoute -> true
}
