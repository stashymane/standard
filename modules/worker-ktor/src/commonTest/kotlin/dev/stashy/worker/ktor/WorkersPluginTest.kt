package dev.stashy.worker.ktor

import dev.stashy.worker.store.memory.InMemoryJobHistory
import dev.stashy.worker.store.memory.InMemoryJobQueue
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val testFormat: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
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
