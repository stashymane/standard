package dev.stashy.worker.exposed.r2dbc

import dev.stashy.worker.exposed.tables.WorkerJobHistoryTable
import dev.stashy.worker.exposed.tables.WorkerJobsTable
import dev.stashy.worker.exposed.testing.JobHistoryTest
import dev.stashy.worker.exposed.testing.StoreFixture
import dev.stashy.worker.exposed.testing.TestJson
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.uuid.Uuid

@RunWith(JUnit4::class)
class ExposedJobHistoryTest : JobHistoryTest() {
    override suspend fun createStore(): StoreFixture {
        val database = R2dbcDatabase.connect(
            "r2dbc:h2:mem:///worker_history_${Uuid.random()}?options=DB_CLOSE_DELAY=-1",
        )
        database.transaction { SchemaUtils.create(WorkerJobsTable, WorkerJobHistoryTable) }
        return StoreFixture(
            queue = ExposedJobQueue(database, TestJson),
            history = ExposedJobHistory(database),
        )
    }
}
