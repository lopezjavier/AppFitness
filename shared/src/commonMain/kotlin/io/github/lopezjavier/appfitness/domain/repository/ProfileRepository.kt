package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.ProfileSummary

interface ProfileRepository {
    /** The signed-in user's profile. A failure carries an [io.github.lopezjavier.appfitness.domain.AppException]. */
    suspend fun getOwnProfile(): Result<ProfileSummary>
}
