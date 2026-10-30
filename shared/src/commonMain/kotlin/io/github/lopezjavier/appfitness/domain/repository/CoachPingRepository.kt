package io.github.lopezjavier.appfitness.domain.repository

/** Dev-only smoke call to the deployed `coach` function (kind `ping`). */
interface CoachPingRepository {
    /**
     * Success when the stream ends with a `done` frame. A function refusal or error fails with
     * [io.github.lopezjavier.appfitness.domain.model.CoachException]; offline or signed out fail with an
     * [io.github.lopezjavier.appfitness.domain.AppException].
     */
    suspend fun ping(): Result<Unit>
}
