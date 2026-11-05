package dev.stashy.standard.exposed.model

import dev.stashy.standard.model.Id
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table

/**
 * Base table whose primary key is a typed [Id].
 *
 * @param name Table name. By default, this will be resolved from the class name with a "Table" suffix removed.
 * @param columnName Name of the identity column. By default, "id" is used.
 */
public abstract class TypedIdTable<T>(
    name: String = "",
    columnName: String = "id",
) : Table(name) {
    public val id: Column<Id<T>> = typedId<T>(columnName).defaultExpression(IdExpression()).databaseGenerated()

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}
