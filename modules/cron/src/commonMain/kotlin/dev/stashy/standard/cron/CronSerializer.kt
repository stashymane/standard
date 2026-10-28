package dev.stashy.standard.cron

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes a [Cron] as its expression string.
 */
public object CronSerializer : KSerializer<Cron> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.stashy.standard.cron.Cron", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Cron): Unit =
        encoder.encodeString(value.expression)

    override fun deserialize(decoder: Decoder): Cron =
        Cron.parse(decoder.decodeString())
}
