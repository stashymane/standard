package dev.stashy.standard.exposed.cursor

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

@RunWith(JUnit4::class)
class CursorAfterTest {
    @Test
    fun `ascending selects values after the cursor`() {
        transaction(database()) {
            insertValues(1, 2, 3)

            val values = CursorCoreTable.selectAll()
                .where { CursorCoreTable.id.cursorAfter(1, SortOrder.ASC) }
                .orderBy(CursorCoreTable.id)
                .map { it[CursorCoreTable.id] }

            assertEquals(listOf(2, 3), values)
        }
    }

    @Test
    fun `descending selects values before the cursor`() {
        transaction(database()) {
            insertValues(1, 2, 3)

            val values = CursorCoreTable.selectAll()
                .where { CursorCoreTable.id.cursorAfter(3, SortOrder.DESC) }
                .orderBy(CursorCoreTable.id)
                .map { it[CursorCoreTable.id] }

            assertEquals(listOf(1, 2), values)
        }
    }

    @Test
    fun `rejects columns marked nullable`() {
        assertFailsWith<IllegalArgumentException> {
            NullableTypedTable.id.cursorAfter(1, SortOrder.ASC)
        }
    }

    private fun database(): Database = Database.connect(
        "jdbc:h2:mem:cursor_core_${Uuid.random()};DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver",
    ).also { database ->
        transaction(database) { SchemaUtils.create(CursorCoreTable) }
    }

    private fun insertValues(vararg values: Int) {
        values.forEach { value ->
            CursorCoreTable.insert { it[CursorCoreTable.id] = value }
        }
    }
}

private object CursorCoreTable : Table("cursor_core") {
    val id = integer("id")

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

private object NullableTypedTable : Table("nullable_typed") {
    val id: Column<Int> = registerColumn("id", IntegerColumnType().apply { nullable = true })
}
