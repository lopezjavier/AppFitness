package io.github.lopezjavier.appfitness.gallery

/**
 * What the gallery deliberately leaves out (T-279). Names are route simple names, `RootRoute.X`, or overlay
 * composable names. See `planning/06-agent-workflow.md` section 4. There is no pending list: every route and overlay
 * needs an entry or an exemption with its reason.
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

    /**
     * Design system component name to reason: components of `designsystem/` that the catalog does not show yet. The
     * gaps are tracked in `tasks/todo.md`, T-280 "Recorded gaps", owned by the review rounds T-281 to T-284: each round
     * adds its area's components to the catalog and removes them from this map. The check fails on a stale exemption.
     */
    val designSystemExempt: Map<String, String> = mapOf(
        "AppTheme" to "Theme wrapper, not a component: the catalog itself runs inside it.",
    ) + listOf(
        "AppAvatar", "AppBottomSheet", "AppButtonChip", "AppChip", "AppIconButton", "AppLineChartSkeleton",
        "AppMessageHost", "AppSheetHeader", "AppSheetLayout", "AppSwitch", "AppSwitchRow", "CardioTimeCell",
        "ChecklistRow", "CoachChangeRow", "CoachProposalCard", "DayPickerSheet", "DiscStepperRow",
        "DiscardChangesDialog", "HelpTriggerButton", "InvalidTimeMessage", "ListRowSkeleton", "NavIcon", "NavLabel",
        "OfflineHint", "OptionPickerSheet", "PausedText", "PillContainer", "ProgressLine", "RestTimerAction",
        "RingArc", "RpeHelpButton", "RpeOptionRow", "RpePickerSheet", "ScaleOptionRow", "SetFieldInput",
        "SetNoteButton", "SetOrdinal", "SheetActionRow", "StatusChip", "StepScaffold", "StreamProgressState",
        "SupersetAccent", "TimeInput", "TimerBarLabel", "TimerBarSurface",
    ).associateWith {
        "Not in the component catalog yet: recorded gap in tasks/todo.md (T-280), owner review rounds T-281 to T-284."
    }
}
