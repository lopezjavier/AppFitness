package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DB values of `public.meal_type`. */
@Serializable
internal enum class MealCodeDto(val domain: MealCode) {
    @SerialName("breakfast")
    Breakfast(MealCode.Breakfast),

    @SerialName("lunch")
    Lunch(MealCode.Lunch),

    @SerialName("snack")
    Snack(MealCode.Snack),

    @SerialName("dinner")
    Dinner(MealCode.Dinner),
}

/** Row of `public.meal_slots` as read. */
@Serializable
internal data class MealSlotDto(
    val id: String,
    val code: MealCodeDto? = null,
    val label: String,
    val position: Int,
    val enabled: Boolean = true,
) {
    fun toDomain(): MealSlot = MealSlot(id, code?.domain, label, position, enabled)

    companion object {
        const val COLUMNS = "id,code,label,position,enabled"
    }
}

/** One element of the `p_slots` array of `save_meal_slots`; a null [id] is a new custom meal. */
@Serializable
internal data class MealSlotDraftDto(val id: String?, val label: String, val enabled: Boolean) {
    companion object {
        fun from(draft: MealSlotDraft) = MealSlotDraftDto(draft.id, draft.label, draft.enabled)
    }
}

/** Arguments of the `save_meal_slots` RPC. */
@Serializable
internal data class SaveMealSlotsParams(
    @SerialName("p_slots") val slots: List<MealSlotDraftDto>,
    @SerialName("p_viewed_day") val viewedDay: LocalDate,
)
