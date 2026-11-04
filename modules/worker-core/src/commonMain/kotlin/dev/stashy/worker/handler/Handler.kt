package dev.stashy.worker.handler

import dev.stashy.worker.Worker
import dev.stashy.worker.job.Job
import kotlinx.serialization.KSerializer
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A composable unit of work.
 */
public interface Handler<C : Any> {
    public val configSerializer: KSerializer<C>

    /**
     * Prepares a job handler as soon as the job is claimed by a [Worker], during the block's prepare window.
     *
     * This function is meant for initialization and should **never** modify state,
     * as it may run more than once for the same job.
     */
    public suspend fun prepare(context: Context, config: C): Prepared

    public typealias Finalizer = suspend (Context, TerminalOutcome) -> Unit

    /**
     * Information about the current occurrence.
     */
    public class Context(
        public val jobId: Job.Id,
        public val worker: Worker,
        public val attempt: Int,
        public val scheduledAt: Instant,
        public val clock: Clock,
    ) {
        public val runKey: String = "${jobId.value}@$scheduledAt"
    }

    /**
     * A prepared handler, ready to execute or finalize an occurrence.
     */
    public class Prepared(
        internal val run: suspend (Context) -> Unit,
        internal val dispose: suspend () -> Unit = {},
        internal val finalize: Finalizer = { _, _ -> },
    )

    /**
     * A builder for a [Prepared] execution.
     */
    public class Builder @PublishedApi internal constructor(
        @PublishedApi internal val run: suspend (Context) -> Unit,
        @PublishedApi internal var onFinalize: Finalizer = { _, _ -> },
    ) {
        @PublishedApi
        internal fun build(cleanup: suspend () -> Unit): Prepared =
            Prepared(run, cleanup, onFinalize)
    }
}
