package io.github.lopezjavier.appfitness.domain.model

/** The unit a [BodyMetric] is measured in. */
enum class MetricUnit { Kg, Percent, Kcal, Cm }

/** The body metrics the trend chart can show (US-27), in selector order. */
enum class BodyMetric(val unit: MetricUnit) {
    Weight(MetricUnit.Kg),
    BodyFat(MetricUnit.Percent),
    LeanMass(MetricUnit.Kg),
    Water(MetricUnit.Kg),
    Bone(MetricUnit.Kg),
    Bmr(MetricUnit.Kcal),
    Waist(MetricUnit.Cm),
    Hip(MetricUnit.Cm),
    ;

    /** True for the metrics read from `body_circumferences` instead of `body_measurements`. */
    val isCircumference: Boolean get() = this == Waist || this == Hip
}
