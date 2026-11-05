package dev.stashy.standard.exposed.util

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import kotlin.reflect.KClass

public fun <T : Enum<T>> Table.enumBySerialNameColumn(
    name: String,
    klass: KClass<T>,
    descriptor: SerialDescriptor,
    length: Int = EnumBySerialNameColumnType.DEFAULT_LENGTH,
): Column<T> = registerColumn(name, EnumBySerialNameColumnType(klass, descriptor, length))

/**
 * Creates an enum column that stores the serial name of each enum entry.
 *
 * [T] must be annotated with `@Serializable`.
 */
public inline fun <reified T : Enum<T>> Table.enumBySerialName(
    name: String,
    length: Int = EnumBySerialNameColumnType.DEFAULT_LENGTH,
): Column<T> = enumBySerialNameColumn(name, T::class, serializer<T>().descriptor, length)
