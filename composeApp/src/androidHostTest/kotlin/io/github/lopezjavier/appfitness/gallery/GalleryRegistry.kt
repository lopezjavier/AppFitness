package io.github.lopezjavier.appfitness.gallery

/** Every entry of the gallery, in index order. */
internal object GalleryRegistry {
    fun all(): List<GalleryEntry> =
        inicioEntries() + diarioEntries() + perfilEntries() + entrenoEntries() + trainerEntries() +
            designSystemEntries()

    /** Entreno entries (T-280 slice 1): routines, exercises, live session, history. */
    private fun entrenoEntries(): List<GalleryEntry> =
        entrenoRoutinesEntries() + entrenoExercisesEntries() + entrenoLiveEntries() + entrenoWorkoutFormEntries() +
            entrenoHistoryEntries()

    /** Trainer entries (T-280 slice 2): hub and boxes, plan and start, block and review, builder, folder, reports. */
    private fun trainerEntries(): List<GalleryEntry> =
        trainerRootEntries() + trainerPlanEntries() + trainerBlockEntries() + trainerBuilderEntries() +
            trainerReportEntries()
}
