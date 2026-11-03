package dev.stashy.standard.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * Typed identifier value class backed by UUID v7.
 * @see Uuid.generateV7
 */
@Serializable
@JvmInline
public value class Id<T>(public val value: Uuid) : Comparable<Id<T>> {
    override fun compareTo(other: Id<T>): Int = value.compareTo(other.value)
    override fun toString(): String = value.toHexDashString()

    public companion object {
        /**
         * Parses the [string] as an ID.
         *
         * @throws IllegalArgumentException if the uuidString is not in a supported uuid format
         * @see Uuid.parse
         */
        public fun <T> from(string: String): Id<T> = Id(Uuid.parse(string))

        /**
         * Creates a new ID for the current time based on [Clock.System][kotlin.time.Clock.System].
         *
         * @see Uuid.generateV7
         */
        public fun <T> new(): Id<T> = Id(Uuid.generateV7())

        /**
         * Returns a special ID that has all bits set to zero.
         * Intended as a placeholder before a record has a valid ID, e.g. a database record before it's inserted.
         *
         * @see Uuid.NIL
         */
        public fun <T> none(): Id<T> = Id(Uuid.NIL)
    }
}
