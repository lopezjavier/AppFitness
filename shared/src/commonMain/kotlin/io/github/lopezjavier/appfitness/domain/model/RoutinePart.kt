package io.github.lopezjavier.appfitness.domain.model

/**
 * The training part a routine is about, which picks its icon (`07` "Routine part, for the routine icon"). [key] is
 * the part key of `07`, for logs and tests only. [Generic] means "nothing found": the UI keeps the dumbbell.
 */
enum class RoutinePart(val key: String) {
    FullBody("full_body"),
    Upper("upper"),
    Lower("lower"),
    Push("push"),
    Pull("pull"),
    Arms("arms"),
    Glutes("glutes"),
    Core("core"),
    Cardio("cardio"),
    Generic("generic"),
}
