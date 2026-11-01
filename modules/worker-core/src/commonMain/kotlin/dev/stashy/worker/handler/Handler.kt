package dev.stashy.worker.handler

import dev.stashy.worker.Worker
import dev.stashy.worker.job.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
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
        private val run: suspend (Context) -> Unit,
        private val cleanup: suspend () -> Unit = {},
        private val onFinalize: Finalizer = { _, _ -> },
    ) {
        /**
         * Executes the prepared job.
         */
        public suspend fun execute(context: Context): Unit = run(context)

        /**
         * Releases all resources acquired during the preparation phase.
         */
        public suspend fun dispose(): Unit = cleanup()

        /**
         * Completes the job after it's left the queue with the given [outcome].
         */
        public suspend fun finalize(context: Context, outcome: TerminalOutcome): Unit =
            onFinalize(context, outcome)
    }

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

/**
 * Creates a job handler.
 */
public inline fun <reified C : Any> job(
    noinline block: suspend JobScope.(Handler.Context, C) -> Handler.Builder,
): Handler<C> = object : Handler<C> {
    override val configSerializer: KSerializer<C> = serializer()

    override suspend fun prepare(context: Handler.Context, config: C): Handler.Prepared {
        val scope = JobScope()
        return try {
            scope.block(context, config).build(cleanup = {
                withContext(NonCancellable) { scope.dispose() }
            })
        } catch (error: Throwable) {
            withContext(NonCancellable) { scope.dispose() }
            throw error
        }
    }
}

/**
 * Adds a finalizer to this handler.
 *
 * The block runs after the job has left the queue, while prepared resources are still available.
 */
public infix fun Handler.Builder.finalizedBy(
    block: suspend Handler.Context.(TerminalOutcome) -> Unit,
): Handler.Builder = apply { onFinalize = block }
