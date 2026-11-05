package dev.stashy.standard.exposed.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.SQLiteDialect
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.*
import kotlin.uuid.Uuid

@RunWith(JUnit4::class)
class DatabaseGeneratedIdTest {
    @Test
    fun `uses a database UUIDv7 default`() {
        val database = Database.connect(
            "jdbc:h2:mem:database_generated_id_${Uuid.random()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        transaction(database) {
            SchemaUtils.create(DatabaseGeneratedIdTable)

            assertNull(DatabaseGeneratedIdTable.id.defaultValueFun)
            assertNotNull(DatabaseGeneratedIdTable.id.defaultValueInDb())

            DatabaseGeneratedIdTable.insert {
                it[DatabaseGeneratedIdTable.name] = "value"
            }

            val id = DatabaseGeneratedIdTable.selectAll().single()[DatabaseGeneratedIdTable.id]
            assertEquals('7', id.toString()[14])
        }
    }

    @Test
    fun `prefers the built-in resolver`() {
        val database = Database.connect(
            "jdbc:h2:mem:database_generated_fallback_${Uuid.random()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        transaction(database) {
            val expression = FallbackGeneratedIdTable.id.defaultValueInDb()
            assertNotNull(expression)
            assertEquals("RANDOM_UUID(7)", expression.toString())
        }
    }

    @Test
    fun `uses a fallback resolver when no built-in function exists`() {
        val database = Database.connect(
            "jdbc:sqlite::memory:",
            driver = "org.sqlite.JDBC",
        )

        transaction(database) {
            val expression = FallbackGeneratedIdTable.id.defaultValueInDb()
            assertNotNull(expression)
            assertEquals("uuid_v7()", expression.toString())
        }
    }

    @Test
    fun `fails when the database has no UUIDv7 function`() {
        val database = Database.connect(
            "jdbc:sqlite::memory:",
            driver = "org.sqlite.JDBC",
        )

        assertFailsWith<UnsupportedByDialectException> {
            transaction(database) { SchemaUtils.create(DatabaseGeneratedIdTable) }
        }
    }
}

private class DatabaseGeneratedEntity

private object DatabaseGeneratedIdTable : Table("database_generated_id") {
    val id = typedId<DatabaseGeneratedEntity>("id").defaultExpression(IdExpression()).databaseGenerated()
    val name = varchar("name", 32)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

private class FallbackGeneratedEntity

private object FallbackGeneratedIdTable : Table("fallback_generated_id") {
    val id = typedId<FallbackGeneratedEntity>("id")
        .defaultExpression(IdExpression { dialect, _ ->
            when (dialect) {
                is SQLiteDialect -> "uuid_v7()"
                is H2Dialect -> "custom_uuid_v7()"
                else -> null
            }
        }).databaseGenerated()

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}
