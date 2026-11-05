package dev.stashy.standard.exposed.model

import dev.stashy.standard.model.Id
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(JUnit4::class)
class TypedIdTableTest {
    @Test
    fun `stores and reads typed ids`() {
        val database = Database.connect(
            "jdbc:h2:mem:typed_id_table_${Uuid.random()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        transaction(database) {
            SchemaUtils.create(TypedIdTestTable)
            val id = Id.new<TypedIdEntity>()

            TypedIdTestTable.insert {
                it[TypedIdTestTable.id] = id
                it[TypedIdTestTable.name] = "value"
            }

            val row = TypedIdTestTable.selectAll().single()
            assertEquals(id, row[TypedIdTestTable.id])
            assertEquals("value", row[TypedIdTestTable.name])
        }
    }

    @Test
    fun `supports a custom id column name`() {
        assertEquals("entity_id", CustomTypedIdTestTable.id.name)
        assertEquals(1, CustomTypedIdTestTable.primaryKey.columns.size)
        assertTrue(CustomTypedIdTestTable.id in CustomTypedIdTestTable.primaryKey.columns)
    }
}

private class TypedIdEntity

private object TypedIdTestTable : TypedIdTable<TypedIdEntity>("typed_id_test") {
    val name = varchar("name", 32)
}

private object CustomTypedIdTestTable : TypedIdTable<TypedIdEntity>(
    name = "custom_typed_id_test",
    columnName = "entity_id",
) {
    val name = varchar("name", 32)
}
