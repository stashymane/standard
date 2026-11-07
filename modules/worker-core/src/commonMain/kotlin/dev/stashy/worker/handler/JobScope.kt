package dev.stashy.worker.handler

import dev.stashy.worker.JobsDsl

/**
 * Scope available to `job` blocks while they prepare.
 */
@JobsDsl
public class JobScope @PublishedApi internal constructor() : ResourceScope by ResourceScope() {
    private var declared = false

    /**
     * Registers the block that runs at the scheduled instant.
     *
     * This must be the returned expression of the enclosing `job` block.
     */
    public fun handler(
        block: suspend Handler.Context.() -> Unit,
    ): Handler.Builder {
        check(!declared) { "handler {} may only be declared once" }
        declared = true
        return Handler.Builder(run = block)
    }

    /**
     * Adds a finalizer to this handler.
     *
     * The block runs after the job has left the queue, while prepared resources are still available.
     */
    public infix fun Handler.Builder.finalizedBy(
        block: suspend Handler.Context.(TerminalOutcome) -> Unit,
    ): Handler.Builder = apply { onFinalize = block }
}
