package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Sex

/** The interpreted metrics of planning/04-business-rules.md §3.8. */
enum class MetricKind { Bmi, BodyFat, WaistToHip }

/** How a status reads, for colouring; never the only cue (the status is also text). */
enum class MetricTone { Good, Caution, Alert }

/** Every status of every interpreted metric. */
enum class MetricStatus(val tone: MetricTone) {
    Underweight(MetricTone.Caution),
    Normal(MetricTone.Good),
    Overweight(MetricTone.Caution),
    Obese(MetricTone.Alert),
    BodyFatLow(MetricTone.Caution),
    BodyFatNormal(MetricTone.Good),
    BodyFatHigh(MetricTone.Caution),
    BodyFatVeryHigh(MetricTone.Alert),
    WhrLowRisk(MetricTone.Good),
    WhrHighRisk(MetricTone.Alert),
}

/** A metric [value] with its [status]; the status also selects the explanation text. */
data class MetricInterpretation(val metric: MetricKind, val value: Double, val status: MetricStatus)

/** Informative ranges for adults (§3.8). Edge values belong to the lower band. */
class InterpretMetric(private val bmi: Bmi = Bmi()) {
    fun bmi(value: Double): MetricInterpretation {
        val status = when (bmi.status(value)) {
            BmiStatus.Underweight -> MetricStatus.Underweight
            BmiStatus.Normal -> MetricStatus.Normal
            BmiStatus.Overweight -> MetricStatus.Overweight
            BmiStatus.Obese -> MetricStatus.Obese
        }
        return MetricInterpretation(MetricKind.Bmi, value, status)
    }

    /** Null without [sex]. */
    fun bodyFat(value: Double, sex: Sex?): MetricInterpretation? {
        val (lowBelow, normalUpTo, highUpTo) = when (sex) {
            Sex.Male -> MALE_FAT
            Sex.Female -> FEMALE_FAT
            null -> return null
        }
        val status = when {
            value < lowBelow -> MetricStatus.BodyFatLow
            value <= normalUpTo -> MetricStatus.BodyFatNormal
            value <= highUpTo -> MetricStatus.BodyFatHigh
            else -> MetricStatus.BodyFatVeryHigh
        }
        return MetricInterpretation(MetricKind.BodyFat, value, status)
    }

    /** Null without [sex]. */
    fun waistToHip(value: Double, sex: Sex?): MetricInterpretation? {
        val lowRiskUpTo = when (sex) {
            Sex.Male -> MALE_WHR_LOW_RISK
            Sex.Female -> FEMALE_WHR_LOW_RISK
            null -> return null
        }
        val status = if (value <= lowRiskUpTo) MetricStatus.WhrLowRisk else MetricStatus.WhrHighRisk
        return MetricInterpretation(MetricKind.WaistToHip, value, status)
    }

    private companion object {
        val MALE_FAT = Triple(10.0, 20.0, 25.0)
        val FEMALE_FAT = Triple(18.0, 28.0, 32.0)
        const val MALE_WHR_LOW_RISK = 0.90
        const val FEMALE_WHR_LOW_RISK = 0.85
    }
}
