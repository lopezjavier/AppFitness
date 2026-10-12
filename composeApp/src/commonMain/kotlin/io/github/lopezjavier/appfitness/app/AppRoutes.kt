package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_progress
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.nav_progress
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** Type-safe navigation routes (ADR-10). Every route must be mapped to a tab in [tab]. */
@Serializable
sealed interface AppRoute

@Serializable
data object DiaryGraph : AppRoute

@Serializable
data object WorkoutGraph : AppRoute

@Serializable
data object ProgressGraph : AppRoute

@Serializable
data object ProfileGraph : AppRoute

@Serializable
data object DiaryHome : AppRoute

@Serializable
data object WorkoutHome : AppRoute

@Serializable
data object ProgressHome : AppRoute

@Serializable
data object ProfileHome : AppRoute

/** Bottom bar tabs, in display order. Each one owns a nested graph (its own back stack). */
enum class TopLevelTab(
    val graph: AppRoute,
    val startRoute: AppRoute,
    val label: StringResource,
    val icon: DrawableResource,
    val testTag: String,
) {
    Diary(DiaryGraph, DiaryHome, Res.string.nav_diary, Res.drawable.ic_nav_diary, "nav_tab_diary"),
    Workout(WorkoutGraph, WorkoutHome, Res.string.nav_workout, Res.drawable.ic_nav_workout, "nav_tab_workout"),
    Progress(ProgressGraph, ProgressHome, Res.string.nav_progress, Res.drawable.ic_nav_progress, "nav_tab_progress"),
    Profile(ProfileGraph, ProfileHome, Res.string.nav_profile, Res.drawable.ic_nav_profile, "nav_tab_profile"),
    ;

    companion object {
        val Start = Diary

        /** Tab the Top App Bar avatar opens (ADR-10). */
        val AvatarTarget = Profile
    }
}

/** Tab that owns this route. Exhaustive on purpose: a new route does not compile until mapped. */
val AppRoute.tab: TopLevelTab
    get() =
        when (this) {
            DiaryGraph, DiaryHome -> TopLevelTab.Diary
            WorkoutGraph, WorkoutHome -> TopLevelTab.Workout
            ProgressGraph, ProgressHome -> TopLevelTab.Progress
            ProfileGraph, ProfileHome -> TopLevelTab.Profile
        }
