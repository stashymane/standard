package dev.stashy.worker.ktor

import dev.stashy.worker.Worker
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.log
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
 *     jobs(LocalJobStore()) {
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
        val worker = config.createWorker()
        application.attributes.put(WorkerKey, worker)

        application.monitor.subscribe(ApplicationStarted) {
            application.launch {
                try {
                    worker.start(application)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    application.log.error("Could not start the worker", error)
                    throw error
                }
            }
        }

        application.monitor.subscribe(ApplicationStopping) {
            worker.requestStop()
        }
    }
