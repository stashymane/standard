package dev.stashy.worker.ktor

import dev.stashy.worker.WorkerLogger
import io.ktor.util.logging.Logger

internal class KtorWorkerLogger(private val logger: Logger) : WorkerLogger {
    override fun log(level: WorkerLogger.Level, message: String, throwable: Throwable?): Unit =
        when (level) {
            WorkerLogger.Level.DEBUG -> if (throwable == null) logger.debug(message) else logger.debug(message, throwable)
            WorkerLogger.Level.INFO -> if (throwable == null) logger.info(message) else logger.info(message, throwable)
            WorkerLogger.Level.WARN -> if (throwable == null) logger.warn(message) else logger.warn(message, throwable)
            WorkerLogger.Level.ERROR -> if (throwable == null) logger.error(message) else logger.error(message, throwable)
        }
}
