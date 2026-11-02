package io.github.lopezjavier.appfitness.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Tolerant wire format of [Equipment]: never throws on decode. The five legacy cardio values read as
 * [Equipment.CardioMachine]; any other unknown value reads as [Equipment.Other]. Only current values are written.
 */
object EquipmentSerializer : KSerializer<Equipment> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Equipment", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Equipment) = encoder.encodeString(value.wire)

    override fun deserialize(decoder: Decoder): Equipment =
        Equipment.fromWire(decoder.decodeString()) ?: Equipment.Other
}

/** A list of [Equipment] where unknown values are dropped instead of failing or inventing an "other". */
object EquipmentListSerializer : KSerializer<List<Equipment>> {
    private val strings = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = strings.descriptor

    override fun serialize(encoder: Encoder, value: List<Equipment>) = strings.serialize(encoder, value.map { it.wire })

    override fun deserialize(decoder: Decoder): List<Equipment> =
        strings.deserialize(decoder).mapNotNull(Equipment::fromWire)
}
