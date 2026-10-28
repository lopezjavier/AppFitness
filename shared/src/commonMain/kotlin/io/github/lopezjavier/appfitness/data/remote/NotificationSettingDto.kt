package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DB values of `public.notification_kind`. */
@Serializable
internal enum class NotificationKindDto(val domain: NotificationKind) {
    @SerialName("weight")
    Weight(NotificationKind.Weight),

    @SerialName("circumference")
    Circumference(NotificationKind.Circumference),

    @SerialName("weekly_check_in")
    WeeklyCheckIn(NotificationKind.WeeklyCheckIn),
    ;

    companion object {
        fun of(kind: NotificationKind): NotificationKindDto = entries.first { it.domain == kind }
    }
}

/** Row of `public.notification_settings`; only the columns the app reads (`updated_at` is not selected). */
@Serializable
internal data class NotificationSettingDto(val kind: NotificationKindDto, val enabled: Boolean) {
    companion object {
        const val COLUMNS = "kind,enabled"
    }
}
