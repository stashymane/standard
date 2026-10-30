package dev.stashy.worker.ktor

import dev.stashy.worker.job.InMemoryJobStore
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
        val store = InMemoryJobStore(testFormat)
        val executed = CompletableDeferred<Unit>()

        application {
            install(Workers) {
                id = "test-worker"
                jobs(store) {
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
    fun `plugin routes jobs to their registered store`() = testApplication {
        val shared = InMemoryJobStore(testFormat)
        val local = InMemoryJobStore(testFormat)
        val executed = CompletableDeferred<String>()

        application {
            install(Workers) {
                id = "test-worker"
                jobs(shared) {
                    job<Unit> { _, _ -> }
                }
                jobs(local) {
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
        assertTrue(shared.list().isEmpty())
        assertTrue(local.list().isNotEmpty())
    }

    @Test
    fun `plugin exposes the worker through the application`() = testApplication {
        application {
            install(Workers) {
                jobs(InMemoryJobStore(testFormat)) { }
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
