package dev.stashy.worker.handler

import dev.stashy.worker.JobsDsl

/**
 * Scope available to `job` blocks while they prepare.
 */
@JobsDsl
public class JobScope @PublishedApi internal constructor() : HandlerScope() {
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
        return Handler.Builder(run = { context -> context.block() })
    }
}
