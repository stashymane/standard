package dev.stashy.worker.exposed

/**
 * Marks APIs that support the Exposed worker stores but are not intended for public use.
 */
@RequiresOptIn(
    message = "This API is an implementation detail of the Exposed worker stores and may change without notice.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class WorkerExposedInternalApi
