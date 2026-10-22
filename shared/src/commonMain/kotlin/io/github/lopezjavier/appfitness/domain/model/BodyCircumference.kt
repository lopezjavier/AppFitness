package io.github.lopezjavier.appfitness.domain.model

import kotlin.time.Instant

/** Body sites of `public.circumference_site`, in display order. */
enum class CircumferenceSite { Neck, Chest, Waist, Hip, Arm, Thigh, Calf }

/** One logged circumference of [site] in centimetres. */
data class BodyCircumference(val id: String, val site: CircumferenceSite, val valueCm: Double, val measuredAt: Instant)
