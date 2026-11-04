package dev.stashy.worker.exposed.jdbc

import dev.stashy.worker.exposed.tables.WorkerJobsTable
import dev.stashy.worker.exposed.testing.JobQueueTest
import dev.stashy.worker.exposed.testing.QueueFixture
import dev.stashy.worker.exposed.testing.TestJson
import dev.stashy.worker.exposed.testing.create
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(JUnit4::class)
class ExposedJobQueueTest : JobQueueTest() {
    override suspend fun createQueue(): QueueFixture {
        val database = Database.connect(
            "jdbc:h2:mem:worker_${Uuid.random()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(database) { SchemaUtils.create(WorkerJobsTable) }
        return QueueFixture(ExposedJobQueue(database, TestJson))
    }

    @Test
    fun `sqlite supports the basic job lifecycle`() = runTest {
        sqliteQueue().use { fixture ->
            val definition = definition("sqlite")
            assertTrue(fixture.queue.create(definition, "value", serializer(), now))

            val claim = assertNotNull(fixture.queue.claim(request(now)))
            assertEquals("value", fixture.queue.codec.decode(claim.job.payload, serializer()))
            assertTrue(fixture.queue.complete(claim, null))
            assertNull(fixture.queue.get(definition.id))
        }
    }

    private fun sqliteQueue(): QueueFixture {
        val path = Files.createTempFile("worker-exposed-", ".sqlite")
        val database = Database.connect(
            "jdbc:sqlite:${path.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )
        transaction(database) { SchemaUtils.create(WorkerJobsTable) }
        return QueueFixture(ExposedJobQueue(database, TestJson)) {
            Files.deleteIfExists(path)
        }
    }
}
