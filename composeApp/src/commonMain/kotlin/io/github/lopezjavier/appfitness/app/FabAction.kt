package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.fab_action_add_food
import io.github.lopezjavier.appfitness.composeapp.resources.fab_action_log_weight
import io.github.lopezjavier.appfitness.composeapp.resources.fab_action_start_workout
import org.jetbrains.compose.resources.StringResource

/** What the contextual FAB does on each tab (US-04). [title] names the action in its sheet. */
enum class FabAction(val title: StringResource) {
    AddFood(Res.string.fab_action_add_food),
    StartWorkout(Res.string.fab_action_start_workout),
    LogWeight(Res.string.fab_action_log_weight),
}

/**
 * The only chrome to FAB mapping. Exhaustive on purpose: a new chrome or tab does not compile until
 * it is mapped. Full-screen destinations hide the FAB.
 */
fun ShellChrome.fabAction(): FabAction? = when (this) {
    is ShellChrome.Tabs ->
        when (tab) {
            TopLevelTab.Diary -> FabAction.AddFood
            TopLevelTab.Workout -> FabAction.StartWorkout
            TopLevelTab.Progress -> FabAction.LogWeight
        }

    is ShellChrome.FullScreen -> null
}
