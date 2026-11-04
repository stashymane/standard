package dev.stashy.worker.exposed.jdbc

import dev.stashy.worker.exposed.tables.WorkerJobHistoryTable
import dev.stashy.worker.exposed.tables.WorkerJobsTable
import dev.stashy.worker.exposed.testing.JobHistoryTest
import dev.stashy.worker.exposed.testing.StoreFixture
import dev.stashy.worker.exposed.testing.TestJson
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.uuid.Uuid

@RunWith(JUnit4::class)
class ExposedJobHistoryTest : JobHistoryTest() {
    override suspend fun createStore(): StoreFixture {
        val database = Database.connect(
            "jdbc:h2:mem:worker_history_${Uuid.random()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(database) { SchemaUtils.create(WorkerJobsTable, WorkerJobHistoryTable) }
        return StoreFixture(
            queue = ExposedJobQueue(database, TestJson),
            history = ExposedJobHistory(database),
        )
    }
}
