package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.lopezjavier.appfitness.data.remote.NotificationKindDto
import io.github.lopezjavier.appfitness.data.remote.NotificationSettingDto
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.repository.NotificationSettingsRepository

/** `notification_settings` of the signed-in user (RLS owner policies, primary key `(user_id, kind)`). */
class SupabaseNotificationSettingsRepository(private val auth: Auth, private val postgrest: Postgrest) :
    NotificationSettingsRepository {
    override suspend fun getAll(): Result<Map<NotificationKind, Boolean>> = auth.ownRow {
        postgrest.from("notification_settings")
            .select(Columns.raw(NotificationSettingDto.COLUMNS))
            .decodeList<NotificationSettingDto>()
            .associate { it.kind.domain to it.enabled }
    }

    override suspend fun setEnabled(kind: NotificationKind, enabled: Boolean): Result<Unit> = auth.ownRow {
        // user_id takes its default (auth.uid()).
        postgrest.from("notification_settings").upsert(NotificationSettingDto(NotificationKindDto.of(kind), enabled)) {
            onConflict = "user_id,kind"
        }
    }.map {}
}
