package dev.stashy.standard.exposed.cursor.testing

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Instant

object CursorTestTable : Table("cursor_test") {
    val id = integer("id")
    val createdAt = timestamp("created_at")
    val group = varchar("group", 32)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

internal fun cursorTimestamp(id: Int): Instant = Instant.fromEpochSeconds(1_700_000_000L + id)
