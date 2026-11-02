package io.github.lopezjavier.appfitness.domain.model

import kotlinx.serialization.Serializable

/**
 * DB values of `public.equipment`, in the display and candidate order of `07` (ADR-92). Decoding is tolerant, see
 * [EquipmentSerializer].
 */
@Serializable(with = EquipmentSerializer::class)
enum class Equipment(val wire: String) {
    Barbell("barbell"),
    EzBar("ez_bar"),
    Dumbbell("dumbbell"),
    Machine("machine"),
    Cable("cable"),
    Kettlebell("kettlebell"),
    Bodyweight("bodyweight"),
    Band("band"),
    BandLong("band_long"),
    CardioMachine("cardio_machine"),
    Other("other"),
    ;

    /** The cardio machine has its own group in "Mi entrenamiento" (ADR-85, ADR-92). */
    val isCardio: Boolean get() = this in cardio

    companion object {
        /** The single cardio item ("Maquina de cardio"). */
        val cardio: List<Equipment> = listOf(CardioMachine)

        /** Every non-cardio equipment, in display order. */
        val lifting: List<Equipment> by lazy { entries.filterNot { it.isCardio } }

        private val legacyCardio = setOf("cardio_bike", "treadmill", "elliptical", "rower", "cardio_other")

        /** Parses a wire value; legacy cardio values give [CardioMachine], unknown ones null. */
        fun fromWire(value: String): Equipment? =
            entries.firstOrNull { it.wire == value } ?: CardioMachine.takeIf { value in legacyCardio }
    }
}
