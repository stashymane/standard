package dev.stashy.worker.ktor

import dev.stashy.worker.Worker
import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.coroutines.*

private val WorkerKey: AttributeKey<Worker> = AttributeKey("Worker")

/**
 * The worker installed by the [Workers] plugin.
 */
public val Application.workers: Worker
    get() = attributes.getOrNull(WorkerKey)
        ?: error("The Workers plugin is not installed on this application")

/**
 * Installs a worker bound to the Ktor application lifecycle.
 *
 * ```kotlin
 * install(Workers) {
 *     jobs(InMemoryJobQueue(Json)) {
 *         job<EmailJob> { context, job -> send(job) }
 *         prepared<ReportJob> { context, job ->
 *             val report by resource { buildReport(job) }.onDispose { it.close() }
 *             handler { publish(report) }
 *         }
 *     }
 *     config {
 *         pollInterval = 10.seconds
 *     }
 * }
 *
 * application.launch {
 *     application.workers.schedule("nightly-email", EmailJob(...)) {
 *         cron("0 0 3 ? * * *")
 *     }
 * }
 * ```
 */
public val Workers: ApplicationPlugin<WorkersConfig> =
    createApplicationPlugin("Workers", ::WorkersConfig) {
        val config = pluginConfig
        val worker = config.createWorker(
            defaultLogger = { KtorWorkerLogger(application.log) }
        )
        application.attributes.put(WorkerKey, worker)

        application.monitor.subscribe(ApplicationStarted) {
            val workerScope = CoroutineScope(
                application.coroutineContext + CoroutineExceptionHandler { _, error ->
                    application.stopAfterWorkerFailure(error)
                },
            )
            try {
                worker.start(workerScope)
            } catch (error: Throwable) {
                application.stopAfterWorkerFailure(error)
            }
        }

        application.monitor.subscribe(ApplicationStopping) {
            worker.requestStop()
        }
    }

private fun Application.stopAfterWorkerFailure(error: Throwable) {
    log.error("Worker failed; stopping the application", error)
    if (coroutineContext[Job]?.isActive != true) return
    CoroutineScope(Dispatchers.Default).launch {
        engine.stopSuspend()
    }
}
