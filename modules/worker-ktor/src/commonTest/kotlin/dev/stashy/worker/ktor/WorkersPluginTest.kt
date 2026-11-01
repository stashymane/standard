package dev.stashy.worker.ktor

import dev.stashy.worker.WorkerLogger
import dev.stashy.worker.store.JobQueue
import dev.stashy.worker.store.memory.InMemoryJobHistory
import dev.stashy.worker.store.memory.InMemoryJobQueue
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val testFormat: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private class FailingQueue : JobQueue<String> by InMemoryJobQueue(testFormat) {
    override suspend fun claim(
        request: JobQueue.ClaimRequest,
        limit: Int,
    ): List<JobQueue.Claimed<String>> = throw AssertionError("boom")
}

class WorkersPluginTest {
    @Test
    fun `plugin runs a configured job`() = testApplication {
        val queue = InMemoryJobQueue(testFormat)
        val executed = CompletableDeferred<Unit>()

        application {
            install(Workers) {
                id = "test-worker"
                jobs(queue) {
                    job<Unit> { _, _ -> executed.complete(Unit) }
                }
                config {
                    pollInterval = 10.milliseconds
                    prepareWindow = 20.milliseconds
                    leaseDuration = 1.seconds
                    heartbeatInterval = 300.milliseconds
                    shutdownGracePeriod = 100.milliseconds
                }
            }

            launch {
                workers.schedule("test-job", Unit) {
                    once(Clock.System.now())
                }
            }

            assertNotNull(workers)
        }

        startApplication()

        withTimeout(5.seconds) {
            executed.await()
        }
    }

    @Test
    fun `plugin reuses a queue across job blocks`() = testApplication {
        val queue = InMemoryJobQueue(testFormat)
        val executed = CompletableDeferred<String>()

        application {
            install(Workers) {
                id = "test-worker"
                jobs(queue) {
                    job<Unit> { _, _ -> }
                }
                jobs(queue) {
                    job<String> { _, config -> executed.complete(config) }
                }
                config {
                    pollInterval = 10.milliseconds
                    prepareWindow = 20.milliseconds
                    leaseDuration = 1.seconds
                    heartbeatInterval = 300.milliseconds
                    shutdownGracePeriod = 100.milliseconds
                }
            }

            launch {
                workers.schedule("unit-job", Unit) {
                    once(Clock.System.now())
                }
                workers.schedule("string-job", "reused") {
                    once(Clock.System.now())
                }
            }
        }

        startApplication()

        assertEquals("reused", withTimeout(5.seconds) { executed.await() })
    }

    @Test
    fun `plugin routes jobs to their registered queue`() = testApplication {
        val shared = InMemoryJobQueue(testFormat)
        val local = InMemoryJobQueue(testFormat)
        val sharedHistory = InMemoryJobHistory()
        val localHistory = InMemoryJobHistory()
        val executed = CompletableDeferred<String>()

        application {
            install(Workers) {
                id = "test-worker"
                jobs(shared, sharedHistory) {
                    job<Unit> { _, _ -> }
                }
                jobs(local, localHistory) {
                    job<String> { _, config -> executed.complete(config) }
                }
                config {
                    pollInterval = 10.milliseconds
                    prepareWindow = 20.milliseconds
                    leaseDuration = 1.seconds
                    heartbeatInterval = 300.milliseconds
                    shutdownGracePeriod = 100.milliseconds
                }
            }

            launch {
                workers.schedule("local-job", "local") {
                    once(Clock.System.now())
                }
            }
        }

        startApplication()

        assertEquals("local", withTimeout(5.seconds) { executed.await() })
        assertTrue(sharedHistory.list().isEmpty())
        assertEquals(listOf("kotlin.String"), localHistory.list().map { it.handlerId })
        assertTrue(shared.list().isEmpty())
        assertTrue(local.list().isEmpty())
    }

    @Test
    fun `plugin logs through the configured logger`() = testApplication {
        val logged = CompletableDeferred<String>()
        val stopPreparing = CompletableDeferred<Unit>()

        application {
            monitor.subscribe(ApplicationStopPreparing) { stopPreparing.complete(Unit) }
            install(Workers) {
                jobs(InMemoryJobQueue(testFormat)) {
                    job<Unit> { _, _ -> error("boom") }
                }
                config {
                    pollInterval = 10.milliseconds
                    prepareWindow = 20.milliseconds
                    leaseDuration = 1.seconds
                    heartbeatInterval = 300.milliseconds
                    shutdownGracePeriod = 100.milliseconds
                    logger = WorkerLogger { level, message, _ ->
                        if (level == WorkerLogger.Level.ERROR) logged.complete(message)
                    }
                }
            }

            launch {
                workers.schedule("failing-job", Unit) {
                    once(Clock.System.now())
                }
            }
        }

        startApplication()

        assertTrue(withTimeout(5.seconds) { logged.await() }.contains("failed"))
        assertFalse(stopPreparing.isCompleted)
    }

    @Test
    fun `plugin stops the application when the worker fails`() = testApplication {
        val stopPreparing = CompletableDeferred<Unit>()

        application {
            monitor.subscribe(ApplicationStopPreparing) { stopPreparing.complete(Unit) }
            install(Workers) {
                jobs(FailingQueue()) {
                    job<Unit> { _, _ -> }
                }
                config {
                    pollInterval = 10.milliseconds
                    prepareWindow = 20.milliseconds
                    leaseDuration = 1.seconds
                    heartbeatInterval = 300.milliseconds
                    shutdownGracePeriod = 100.milliseconds
                }
            }
        }

        startApplication()

        withTimeout(5.seconds) { stopPreparing.await() }
    }

    @Test
    fun `default logger is created only when no logger is configured`() {
        var created = 0
        val factory = {
            created += 1
            WorkerLogger.None
        }

        WorkersConfig().apply {
            config { logger = WorkerLogger { _, _, _ -> } }
        }.createWorker(factory)
        assertEquals(0, created)

        WorkersConfig().createWorker(factory)
        assertEquals(1, created)
    }

    @Test
    fun `plugin exposes the worker through the application`() = testApplication {
        application {
            install(Workers) {
                jobs(InMemoryJobQueue(testFormat)) { }
                config {
                    pollInterval = 10.milliseconds
                    prepareWindow = 20.milliseconds
                    leaseDuration = 1.seconds
                    heartbeatInterval = 300.milliseconds
                    shutdownGracePeriod = 100.milliseconds
                }
            }

            assertNotNull(workers)
        }
    }
}
