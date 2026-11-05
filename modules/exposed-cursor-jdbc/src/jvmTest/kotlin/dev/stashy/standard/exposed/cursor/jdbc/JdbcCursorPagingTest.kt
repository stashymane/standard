package dev.stashy.standard.exposed.cursor.jdbc

import dev.stashy.standard.exposed.cursor.testing.CursorPagingTest
import dev.stashy.standard.exposed.cursor.testing.CursorTestTable
import dev.stashy.standard.exposed.model.idColumn
import dev.stashy.standard.model.Id
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlin.uuid.Uuid

@RunWith(JUnit4::class)
class JdbcCursorPagingTest : CursorPagingTest() {
    private lateinit var database: Database

    @Before
    fun setup() {
        database = Database.connect(
            "jdbc:h2:mem:cursor_jdbc_${Uuid.random()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(database) { SchemaUtils.create(CursorTestTable) }
    }

    override suspend fun reset() {
        transaction(database) { CursorTestTable.deleteAll() }
    }

    override suspend fun insert(id: Int, createdAt: Instant, group: String) {
        transaction(database) {
            CursorTestTable.insert {
                it[CursorTestTable.id] = id
                it[CursorTestTable.createdAt] = createdAt
                it[CursorTestTable.group] = group
            }
        }
    }

    override suspend fun page(
        cursor: Int?,
        order: SortOrder,
        limit: Int,
        group: String?,
    ): List<Int> = transaction(database) {
        val query = CursorTestTable.selectAll()
        if (group != null) {
            query.where { CursorTestTable.group eq group }
        }

        query.applyCursor(CursorTestTable.id, cursor, order)
            .limit(limit)
            .map { it[CursorTestTable.id] }
    }

    @Test
    fun `mutates the query in place`() {
        transaction(database) {
            val query = CursorTestTable.selectAll()
            val result = query.applyCursor(CursorTestTable.id, null, SortOrder.ASC)

            assertSame(query, result)
            assertEquals(SortOrder.ASC, query.orderByExpressions.single().second)
        }
    }

    @Test
    fun `rejects an existing order by clause`() {
        transaction(database) {
            assertFailsWith<IllegalArgumentException> {
                CursorTestTable.selectAll()
                    .orderBy(CursorTestTable.id)
                    .applyCursor(CursorTestTable.id)
            }
        }
    }

    @Test
    fun `pages by timestamp`() {
        transaction(database) {
            (1..5).forEach { id ->
                CursorTestTable.insert {
                    it[CursorTestTable.id] = id
                    it[CursorTestTable.createdAt] = Instant.fromEpochSeconds(1_700_000_000L + id)
                    it[CursorTestTable.group] = "all"
                }
            }

            val page = CursorTestTable.selectAll()
                .applyCursor(
                    column = CursorTestTable.createdAt,
                    cursor = Instant.fromEpochSeconds(1_700_000_000L + 3),
                    order = SortOrder.DESC,
                )
                .limit(2)
                .map { it[CursorTestTable.id] }

            assertEquals(listOf(2, 1), page)
        }
    }

    @Test
    fun `pages by model id`() {
        transaction(database) {
            SchemaUtils.create(IdCursorTestTable)
            val ids = (1..5).map { index ->
                Id.from<IdCursorEntity>("00000000-0000-0000-0000-00000000000$index")
            }

            ids.forEach { id ->
                IdCursorTestTable.insert { it[IdCursorTestTable.id] = id }
            }

            val page = IdCursorTestTable.selectAll()
                .applyCursor(IdCursorTestTable.id, ids[2], SortOrder.DESC)
                .limit(2)
                .map { it[IdCursorTestTable.id] }

            assertEquals(listOf(ids[1], ids[0]), page)
        }
    }
}

private class IdCursorEntity

private object IdCursorTestTable : Table("id_cursor_test") {
    val id = idColumn<IdCursorEntity>("id")

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}
