package dev.stashy.standard.exposed.model

import dev.stashy.standard.model.Id
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table

/**
 * Creates a column storing [Id] values as UUIDs.
 *
 * IDs must be supplied explicitly unless the column is configured with [databaseGenerated].
 */
public fun <T> Table.typedId(name: String = "id"): Column<Id<T>> =
    registerColumn(name, IdColumnType())
