package io.github.lopezjavier.appfitness.domain

/** Domain-level failure the data layer maps every low-level error to (planning/02-architecture.md §3). */
sealed interface AppError {
    data object Network : AppError

    data object Unauthorized : AppError

    data object NotFound : AppError

    data object Validation : AppError

    data object Unknown : AppError

    /** Starting a workout while another one is in progress (unique violation on the in-progress session). */
    data object SessionAlreadyInProgress : AppError

    /** A saved meal ("comida") with that name already exists (unique violation on the user's template names). */
    data object NameAlreadyExists : AppError

    /** A custom exercise cannot be deleted while [sets] logged sets or [routines] routine entries reference it. */
    data class InUse(val sets: Int, val routines: Int) : AppError
}

/** Throwable carrier of an [AppError], used as the failure of a [Result]. */
class AppException(val error: AppError, cause: Throwable? = null) : Exception(error.toString(), cause)

/** The [AppError] of a failed [Result], or null for a success or a foreign throwable. */
fun <T> Result<T>.appErrorOrNull(): AppError? = (exceptionOrNull() as? AppException)?.error
