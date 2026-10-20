package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
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
