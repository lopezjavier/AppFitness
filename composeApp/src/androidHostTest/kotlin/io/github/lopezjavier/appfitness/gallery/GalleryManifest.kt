package io.github.lopezjavier.appfitness.gallery

/**
 * What the gallery deliberately leaves out (T-279). Names are route simple names, `RootRoute.X`, or overlay
 * composable names. See `planning/06-agent-workflow.md` section 4.
 */
internal object GalleryManifest {
    /** Name to reason. Names that are not in the source (system UIs) are listed for the index only. */
    val exempt: Map<String, String> = mapOf(
        "DiaryGraph" to "Navigation graph, no UI of its own.",
        "WorkoutGraph" to "Navigation graph, no UI of its own.",
        "ProfileGraph" to "Navigation graph, no UI of its own.",
        "TrainerGraph" to "Navigation graph, no UI of its own.",
        "CatalogRoute" to "Debug component catalog, local flavor only (everything in feature/catalog).",
        "RootRoute.Main" to "The shell: covered by the tab roots and the Compartido entries.",
        "Credential Manager sheet" to "System UI of Google sign-in, not drawn by the app.",
        "Camera and photo picker" to "System UI, not drawn by the app.",
        "ML Kit barcode scanner" to "System UI of Google Play services, not drawn by the app.",
        "Permission dialogs" to "System UI, not drawn by the app.",
        "Rest alert notification" to "Android notification, not a composable; its texts are in strings.xml.",
    )

    /** Name to owner task: screens and overlays that a later task adds. The list only shrinks. */
    val pending: Map<String, String> = linkedMapOf<String, String>().apply {
        // T-280: Entreno and Trainer routes.
        listOf(
            "WorkoutHome", "TrainerHome", "PlanProposalRoute", "BeforeStartRoute", "YourDataRoute", "TrainerPlanRoute",
            "TrainerBlockRoute", "BlockPreviewRoute", "BlockBuilderRoute", "FolderBlockRoute",
            "FolderReviewResultRoute", "ExercisePickerRoute", "ExerciseDetailRoute", "RoutineBuilderRoute",
            "RoutineDetailsRoute", "LiveSessionRoute", "HistoryRoute", "SessionDetailRoute", "EditWorkoutRoute",
            "PastWorkoutRoute", "WeekSummaryRoute", "BlockReviewRoute", "ReportsRoute", "PastReportRoute",
        ).forEach { put(it, "T-280") }
        // T-280: overlays declared in feature/{workout,routines,trainer,exercises}. None of them is opened from
        // Diario, Perfil or Inicio (callers grepped), so none belongs to T-279.
        listOf(
            "ExerciseFormSheet", "MovementPatternField", "FolderSheet", "BuilderRowMenu", "BuilderSheets",
            "RoutineMenuSheet", "FolderMenuSheet", "MoveSheet", "ActivityLevelSheet", "CardioMinutesSheet",
            "BlockBuilderContent", "BlockReplaceSheet", "BlockStartSheets", "BuilderCreateExerciseSheet",
            "BuilderSlotPickerSheet", "DayTypeSheet", "ExerciseReasonSheet", "FolderBlockPickers", "ExercisePicker",
            "FolderBlockScreen", "YearsSheet", "MaintenanceSheet", "ExtendWeekPicker", "SavedBlockActions",
            "SwapPickerSheet", "DiscLoaderSheet", "DiscomfortSheet", "ExerciseMenuSheet", "ExerciseRestSheet",
            "FinishSessionSheet", "LiveTypePicker", "LiveRpePicker", "LiveDialogs", "PastWorkoutChooserSheet",
            "PastWorkoutScreen", "ProgressionHelpSheet", "SaveAsRoutineSheet", "UpdateRoutineBottomSheet",
            "OptionSheet", "WarmUpCalculatorSheet", "WorkoutEntryHost", "FormPickers",
        ).forEach { put(it, "T-280") }
    }
}
