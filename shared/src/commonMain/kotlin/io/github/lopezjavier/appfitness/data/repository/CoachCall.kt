package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.lopezjavier.appfitness.data.remote.BlockSessionDto
import io.github.lopezjavier.appfitness.data.remote.CoachBlockPlanDto
import io.github.lopezjavier.appfitness.data.remote.blockOf
import io.github.lopezjavier.appfitness.domain.model.CoachBlock

/** The stored block of [planId] read through the owner-select RLS, or null when the plan has none. */
internal suspend fun loadStoredBlock(postgrest: Postgrest, planId: String): CoachBlock? {
    val plan = postgrest.from("coach_plans")
        .select { filter { eq("id", planId) } }
        .decodeList<CoachBlockPlanDto>()
        .firstOrNull() ?: return null
    val sessions = postgrest.from("coach_plan_sessions")
        .select {
            filter { eq("plan_id", planId) }
            order("week", Order.ASCENDING)
            order("session_index", Order.ASCENDING)
        }
        .decodeList<BlockSessionDto>()
    return blockOf(plan, sessions)
}
