package dev.stashy.standard.exposed.model

import dev.stashy.standard.model.Id
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
import kotlin.uuid.Uuid

@RunWith(JUnit4::class)
class IdColumnTest {
    @Test
    fun `stores and reads ids`() {
        val database = Database.connect(
            "jdbc:h2:mem:exposed_model_${Uuid.random()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        transaction(database) {
            SchemaUtils.create(IdTestTable)
            val id = Id.new<TestEntity>()

            IdTestTable.insert {
                it[IdTestTable.id] = id
                it[IdTestTable.name] = "value"
            }

            val row = IdTestTable.selectAll().single()
            assertEquals(id, row[IdTestTable.id])
            assertEquals("value", row[IdTestTable.name])
        }
    }
}

private class TestEntity

private object IdTestTable : Table("id_test") {
    val id = typedId<TestEntity>("id")
    val name = varchar("name", 32)

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}
