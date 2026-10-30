package dev.stashy.worker.job

import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat

/**
 * Encodes typed handler configs to and from the payload representation a queue stores.
 */
public interface ConfigCodec<P : Any> {
    /**
     * Encodes [config] into a queue payload.
     */
    public fun <C : Any> encode(config: C, serializer: KSerializer<C>): P

    /**
     * Decodes a queue payload into its config.
     */
    public fun <C : Any> decode(payload: P, serializer: KSerializer<C>): C
}

/**
 * Stores configs as strings encoded with [format].
 */
public class StringConfigCodec(
    public val format: StringFormat,
) : ConfigCodec<String> {
    override fun <C : Any> encode(config: C, serializer: KSerializer<C>): String =
        format.encodeToString(serializer, config)

    override fun <C : Any> decode(payload: String, serializer: KSerializer<C>): C =
        format.decodeFromString(serializer, payload)
}

/**
 * Stores configs as-is, without serialization.
 *
 * The stored payload is the same instance that was passed in, so later mutations are visible to
 * the queue.
 */
public object DirectConfigCodec : ConfigCodec<Any> {
    override fun <C : Any> encode(config: C, serializer: KSerializer<C>): Any = config

    @Suppress("UNCHECKED_CAST")
    override fun <C : Any> decode(payload: Any, serializer: KSerializer<C>): C = payload as C
}
