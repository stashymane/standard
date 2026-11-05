package dev.stashy.standard.exposed.cursor.r2dbc

import dev.stashy.standard.exposed.cursor.testing.CursorPagingTest
import dev.stashy.standard.exposed.cursor.testing.CursorTestTable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
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
class R2dbcCursorPagingTest : CursorPagingTest() {
    private lateinit var database: R2dbcDatabase

    @Before
    fun setup() {
        database = R2dbcDatabase.connect(
            "r2dbc:h2:mem:///cursor_r2dbc_${Uuid.random()}?options=DB_CLOSE_DELAY=-1",
        )
        runBlocking { suspendTransaction(db = database) { SchemaUtils.create(CursorTestTable) } }
    }

    override suspend fun reset() {
        suspendTransaction(db = database) { CursorTestTable.deleteAll() }
    }

    override suspend fun insert(id: Int, createdAt: Instant, group: String) {
        suspendTransaction(db = database) {
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
    ): List<Int> = suspendTransaction(db = database) {
        val query = CursorTestTable.selectAll()
        if (group != null) {
            query.where { CursorTestTable.group eq group }
        }

        query.applyCursor(CursorTestTable.id, cursor, order)
            .limit(limit)
            .map { it[CursorTestTable.id] }
            .toList()
    }

    @Test
    fun `mutates the query in place`() {
        runBlocking {
            suspendTransaction(db = database) {
                val query = CursorTestTable.selectAll()
                val result = query.applyCursor(CursorTestTable.id, null, SortOrder.ASC)

                assertSame(query, result)
                assertEquals(SortOrder.ASC, query.orderByExpressions.single().second)
            }
        }
    }

    @Test
    fun `rejects an existing order by clause`() {
        runBlocking {
            suspendTransaction(db = database) {
                assertFailsWith<IllegalArgumentException> {
                    CursorTestTable.selectAll()
                        .orderBy(CursorTestTable.id)
                        .applyCursor(CursorTestTable.id)
                }
            }
        }
    }
}
