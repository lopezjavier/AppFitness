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

    @SerialName("week_summary")
    WeekSummary(NotificationKind.WeekSummary),

    @SerialName("block_end")
    BlockEnd(NotificationKind.BlockEnd),
    ;

    companion object {
        fun of(kind: NotificationKind): NotificationKindDto = entries.first { it.domain == kind }

        /** The entry whose DB value is [value], or null for a value this app does not know. */
        fun fromDbValue(value: String): NotificationKindDto? = entries.firstOrNull { it.dbValue == value }
    }
}

/** The DB value of this kind: its `@SerialName`. */
private val NotificationKindDto.dbValue: String get() = NotificationKindDto.serializer().descriptor.getElementName(
    ordinal,
)

/** Row of `public.notification_settings`; only the columns the app reads (`updated_at` is not selected). */
@Serializable
internal data class NotificationSettingDto(val kind: NotificationKindDto, val enabled: Boolean) {
    companion object {
        const val COLUMNS = "kind,enabled"
    }
}

/**
 * A read row of `public.notification_settings` with [kind] kept as the raw DB value, so a kind this app does not
 * know (a retired one still stored, or a newer one) is skipped instead of failing the whole read.
 */
@Serializable
internal data class NotificationSettingRowDto(val kind: String, val enabled: Boolean)
