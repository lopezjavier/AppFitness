package io.github.lopezjavier.appfitness.domain.model

import kotlin.time.Instant

/** Body sites of `public.circumference_site`, in display order. */
enum class CircumferenceSite { Neck, Chest, Waist, Hip, Arm, Thigh, Calf }

/** One logged circumference of [site] in centimetres; [createdAt] orders rows saved for the same instant. */
data class BodyCircumference(
    val id: String,
    val site: CircumferenceSite,
    val valueCm: Double,
    val measuredAt: Instant,
    val createdAt: Instant,
)

/** One site/value pair of a multi-site save. */
data class CircumferenceEntry(val site: CircumferenceSite, val valueCm: Double)
