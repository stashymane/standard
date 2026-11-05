package dev.stashy.standard.exposed.util

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import kotlin.reflect.KClass

/**
 * Column type that provides values based on their [SerialName][kotlinx.serialization.SerialName].
 */
public class EnumBySerialNameColumnType<T : Enum<T>>(
    private val klass: KClass<T>,
    private val descriptor: SerialDescriptor,
    private val colLength: Int = DEFAULT_LENGTH,
) : ColumnType<T>() {
    public companion object {
        public const val DEFAULT_LENGTH: Int = 255
    }

    private val strings = VarCharColumnType(colLength)
    private val valuesBySerialName: Map<String, T>

    init {
        require(descriptor.kind == SerialKind.ENUM) {
            "${klass.qualifiedName} does not use an enum serializer."
        }

        val values = klass.java.enumConstants ?: error("${klass.qualifiedName} is not an enum.")
        valuesBySerialName = values.associateBy(::serialNameOf)

        require(valuesBySerialName.size == values.size) {
            "Enum ${klass.qualifiedName} has duplicate serial names."
        }
    }

    override fun sqlType(): String = strings.sqlType()

    override fun valueFromDB(value: Any): T = when (value) {
        is String -> valuesBySerialName[value]
            ?: error("$value can't be associated with any serial name of enum ${klass.qualifiedName}")

        is Enum<*> -> @Suppress("UNCHECKED_CAST") (value as T)
        else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.qualifiedName}")
    }

    override fun notNullValueToDB(value: T): Any = serialNameOf(value)

    override fun nonNullValueToString(value: T): String = strings.valueToString(serialNameOf(value))

    override fun readObject(rs: RowApi, index: Int): Any? = strings.readObject(rs, index)

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        strings.setParameter(stmt, index, value)
    }

    override fun validateValueBeforeUpdate(value: T?) {
        if (value != null) {
            val serialName = serialNameOf(value)
            val valueLength = serialName.codePointCount(0, serialName.length)
            require(valueLength <= colLength) {
                "Value can't be stored to database column because exceeds length ($valueLength > $colLength)"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as EnumBySerialNameColumnType<*>

        return klass == other.klass && colLength == other.colLength
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + klass.hashCode()
        result = 31 * result + colLength
        return result
    }

    private fun serialNameOf(value: T): String = descriptor.getElementName(value.ordinal)
}
