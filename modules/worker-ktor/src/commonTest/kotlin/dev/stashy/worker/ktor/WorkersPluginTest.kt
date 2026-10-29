package dev.stashy.worker.ktor

import dev.stashy.worker.job.InMemoryJobStore
import io.ktor.server.application.install
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
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
                this.store = store
                id = "test-worker"
                jobs {
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
    fun `plugin exposes the worker through the application`() = testApplication {
        application {
            install(Workers) {
                store = InMemoryJobStore(testFormat)
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
