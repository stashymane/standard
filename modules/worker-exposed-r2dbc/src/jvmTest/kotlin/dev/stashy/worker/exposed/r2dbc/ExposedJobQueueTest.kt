package dev.stashy.worker.exposed.r2dbc

import dev.stashy.worker.exposed.tables.WorkerJobsTable
import dev.stashy.worker.exposed.testing.JobQueueTest
import dev.stashy.worker.exposed.testing.QueueFixture
import dev.stashy.worker.exposed.testing.TestJson
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.uuid.Uuid

@RunWith(JUnit4::class)
class ExposedJobQueueTest : JobQueueTest() {
    override suspend fun createQueue(): QueueFixture {
        val database = R2dbcDatabase.connect(
            "r2dbc:h2:mem:///worker_${Uuid.random()}?options=DB_CLOSE_DELAY=-1",
        )
        database.transaction { SchemaUtils.create(WorkerJobsTable) }
        return QueueFixture(ExposedJobQueue(database, TestJson))
    }
}
