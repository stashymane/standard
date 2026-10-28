package dev.stashy.worker

import dev.stashy.worker.job.Job
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A composable unit of work, selected by the serial name of its config type.
 *
 * [configSerializer] persists and restores each job's configuration.
 */
public interface Handler<C : Any> {
    public val configSerializer: KSerializer<C>

    public suspend fun execute(context: Context, config: C)

    /**
     * Information about the current execution.
     *
     * [runKey] is stable across retries of the same occurrence and can be used for idempotency.
     */
    public class Context(
        public val jobId: Job.Id,
        public val workerId: String,
        public val attempt: Int,
        public val scheduledAt: Instant,
        public val startedAt: Instant,
        public val clock: Clock,
    ) {
        public val runKey: String = "${jobId.value}@$scheduledAt"
    }
}

/**
 * Creates a handler from a suspend function.
 */
public inline fun <reified C : Any> handler(
    noinline block: suspend (Handler.Context, C) -> Unit,
): Handler<C> = object : Handler<C> {
    override val configSerializer: KSerializer<C> = serializer()

    override suspend fun execute(context: Handler.Context, config: C): Unit = block(context, config)
}
