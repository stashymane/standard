package dev.stashy.worker

/**
 * Logging boundary for the worker.
 */
public fun interface WorkerLogger {
    public fun log(level: Level, message: String, throwable: Throwable?)

    public enum class Level {
        DEBUG,
        INFO,
        WARN,
        ERROR,
    }

    public companion object {
        public val None: WorkerLogger = WorkerLogger { _, _, _ -> }
    }
}

internal fun WorkerLogger.log(level: WorkerLogger.Level, message: String): Unit =
    log(level, message, null)
