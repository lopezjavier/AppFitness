package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.CoachDerivedDto
import io.github.lopezjavier.appfitness.data.remote.CoachDerivedParams
import io.github.lopezjavier.appfitness.data.remote.ConfirmDerivedParams
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import kotlinx.datetime.LocalDate

/** Coach derived values of the signed-in user through the T-157 RPCs. */
class SupabaseCoachRepository(private val auth: Auth, private val postgrest: Postgrest) : CoachRepository {
    override suspend fun derivedValues(today: LocalDate): Result<CoachDerivedValues> = auth.ownRow {
        postgrest.rpc("coach_derived_estimates", CoachDerivedParams(today)).decodeAs<CoachDerivedDto>().toDomain()
    }

    override suspend fun confirm(today: LocalDate, activityLevel: ActivityLevel?): Result<CoachDerivedValues> =
        auth.ownRow {
            postgrest.rpc("confirm_derived_values", ConfirmDerivedParams.of(today, activityLevel))
                .decodeAs<CoachDerivedDto>().toDomain()
        }
}
