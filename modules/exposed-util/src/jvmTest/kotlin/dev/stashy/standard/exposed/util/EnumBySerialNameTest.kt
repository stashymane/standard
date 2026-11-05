package dev.stashy.standard.exposed.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

@RunWith(JUnit4::class)
class EnumBySerialNameTest {
    @Test
    fun `stores and reads enum serial names`() {
        val database = Database.connect(
            "jdbc:h2:mem:enum_by_serial_name_${Uuid.random()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )

        transaction(database) {
            SchemaUtils.create(EnumBySerialNameTable)
            EnumBySerialNameTable.insert { it[status] = Status.IN_PROGRESS }
            EnumBySerialNameTable.insert { it[status] = Status.DONE }

            val stored = exec("SELECT status FROM enum_by_serial_name") { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.getString("status"))
                    }
                }
            }

            assertNotNull(stored)
            assertEquals(setOf("in-progress", "DONE"), stored.toSet())
            assertEquals(
                setOf(Status.IN_PROGRESS, Status.DONE),
                EnumBySerialNameTable.selectAll().map { it[EnumBySerialNameTable.status] }.toSet(),
            )
        }
    }
}

@Serializable
private enum class Status {
    @SerialName("in-progress")
    IN_PROGRESS,
    DONE,
}

private object EnumBySerialNameTable : Table("enum_by_serial_name") {
    val status = enumBySerialName<Status>("status", length = 32)
}
