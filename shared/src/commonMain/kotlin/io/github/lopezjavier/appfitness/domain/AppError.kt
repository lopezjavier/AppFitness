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

    /** A food's unit cannot change while a diary entry or a comida references it (check violation on the server). */
    data object FoodInUse : AppError

    /** The photo did not show a recognisable food or readable nutrition values (`food-photo` 422). */
    data object PhotoNotRecognized : AppError

    /** The free AI quota is used up for now (`food-photo` 429). */
    data object QuotaExceeded : AppError

    /** The AI service failed or is not configured (`food-photo` 500/502/503). */
    data object ServiceUnavailable : AppError

    /** A custom exercise cannot be deleted while [sets] logged sets or [routines] routine entries reference it. */
    data class InUse(val sets: Int, val routines: Int) : AppError
}

/** Throwable carrier of an [AppError], used as the failure of a [Result]. */
class AppException(val error: AppError, cause: Throwable? = null) : Exception(error.toString(), cause)

/** The [AppError] of a failed [Result], or null for a success or a foreign throwable. */
fun <T> Result<T>.appErrorOrNull(): AppError? = (exceptionOrNull() as? AppException)?.error
