package dev.stashy.standard.exposed.model

import dev.stashy.standard.model.Id
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.UuidColumnType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.core.statements.api.RowApi

public class IdColumnType<T> : ColumnType<Id<T>>() {
    private val uuid = UuidColumnType()

    override fun sqlType(): String = uuid.sqlType()

    override fun valueFromDB(value: Any): Id<T> = when (value) {
        is Id<*> -> @Suppress("UNCHECKED_CAST") (value as Id<T>)
        else -> Id(uuid.valueFromDB(value))
    }

    override fun notNullValueToDB(value: Id<T>): Any = uuid.notNullValueToDB(value.value)

    override fun nonNullValueToString(value: Id<T>): String = uuid.nonNullValueToString(value.value)

    override fun readObject(rs: RowApi, index: Int): Any? = uuid.readObject(rs, index)

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        uuid.setParameter(
            stmt,
            index,
            when (value) {
                is Id<*> -> value.value
                else -> value
            },
        )
    }
}
