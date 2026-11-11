package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.CoachIntakeDto
import io.github.lopezjavier.appfitness.data.remote.SaveIntakeParams
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository

/** The intake row (owner-select RLS) and its RPCs. */
class SupabaseCoachIntakeRepository(private val auth: Auth, private val postgrest: Postgrest) : CoachIntakeRepository {
    override suspend fun get(): Result<CoachIntake?> = auth.ownRow {
        postgrest.from("coach_user_intake").select().decodeList<CoachIntakeDto>().firstOrNull()?.toDomain()
    }

    override suspend fun save(update: IntakeUpdate): Result<CoachIntake> = auth.ownRow {
        postgrest.rpc("save_coach_intake", SaveIntakeParams.of(update)).decodeAs<CoachIntakeDto>().toDomain()
    }

    override suspend fun acknowledgeMaintenanceHandover(): Result<Unit> = auth.ownRow {
        postgrest.rpc("ack_maintenance_handover")
        Unit
    }
}
