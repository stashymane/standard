package dev.stashy.worker.handler

import dev.stashy.worker.job.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A composable unit of work, selected by the serial name of its config type.
 *
 * [prepare] runs as soon as the job is claimed. It may acquire resources and do any work that
 * should happen before the scheduled instant. The returned [Prepared] is executed only when the
 * job becomes due.
 *
 * [configSerializer] persists and restores each job's configuration.
 */
public interface Handler<C : Any> {
    public val configSerializer: KSerializer<C>

    public suspend fun prepare(context: Context, config: C): Prepared

    /**
     * Information about the current occurrence.
     *
     * [runKey] is stable across retries of the same occurrence and can be used for idempotency.
     */
    public class Context(
        public val jobId: Job.Id,
        public val workerId: String,
        public val attempt: Int,
        public val scheduledAt: Instant,
        public val clock: Clock,
    ) {
        public val runKey: String = "${jobId.value}@$scheduledAt"
    }

    /**
     * A prepared execution. [execute] runs at the scheduled instant; [dispose] releases any
     * resources acquired by [prepare] and runs on every path after preparation succeeded.
     */
    public class Prepared(
        private val run: suspend (Context) -> Unit,
        private val cleanup: suspend () -> Unit = {},
    ) {
        public suspend fun execute(context: Context): Unit = run(context)

        public suspend fun dispose(): Unit = cleanup()
    }

    /**
     * The execution block returned by `handler {}` inside a `prepared` block.
     */
    public class Execution @PublishedApi internal constructor(
        @PublishedApi internal val run: suspend (Context) -> Unit,
    )
}

/**
 * Creates a handler whose block runs at the scheduled instant.
 */
public inline fun <reified C : Any> job(
    noinline block: suspend HandlerScope.(Handler.Context, C) -> Unit,
): Handler<C> = object : Handler<C> {
    override val configSerializer: KSerializer<C> = serializer()

    override suspend fun prepare(context: Handler.Context, config: C): Handler.Prepared {
        var scope: HandlerScope? = null
        return Handler.Prepared(
            run = { executionContext ->
                val executionScope = HandlerScope()
                scope = executionScope
                executionScope.block(executionContext, config)
            },
            cleanup = { withContext(NonCancellable) { scope?.dispose() } },
        )
    }
}

/**
 * Creates a handler with a preparation stage.
 *
 * The block runs when the job is claimed. It must end by returning the result of `handler {}`;
 * that block runs at the scheduled instant.
 */
public inline fun <reified C : Any> prepared(
    noinline block: suspend PreparationScope.(Handler.Context, C) -> Handler.Execution,
): Handler<C> = object : Handler<C> {
    override val configSerializer: KSerializer<C> = serializer()

    override suspend fun prepare(context: Handler.Context, config: C): Handler.Prepared {
        val scope = PreparationScope()
        return try {
            val execution = scope.block(context, config)
            Handler.Prepared(
                run = { executionContext -> execution.run(executionContext) },
                cleanup = { withContext(NonCancellable) { scope.dispose() } },
            )
        } catch (error: Throwable) {
            withContext(NonCancellable) { scope.dispose() }
            throw error
        }
    }
}
