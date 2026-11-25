package dev.stashy.worker.pool

import dev.stashy.worker.store.JobQueue

/**
 * Budget of concurrent occurrences shared by the jobs that reference it.
 *
 * A pool is a plain value, so the same instance may be passed to several `jobs` blocks, several
 * workers, or both. A granted permit is held for as long as the occurrence is claimed, including
 * its wait for the scheduled instant, and is returned through [release].
 */
public interface ConcurrencyPool {
    /**
     * Label used in diagnostics. Labels carry no identity, so several pools may share one.
     */
    public val label: String?

    /**
     * Maximum number of occurrences admitted at once. Claim requests are sized by it.
     */
    public val capacity: Int

    /**
     * Suspends until at least one occurrence may be admitted and returns how many were granted,
     * between 1 and [PoolRequest.want].
     */
    public suspend fun admit(request: PoolRequest): Int

    /**
     * Returns [count] permits granted by [admit].
     */
    public fun release(count: Int)
}

/**
 * A claim attempt against a [ConcurrencyPool].
 */
@ConsistentCopyVisibility
public data class PoolRequest internal constructor(
    /**
     * Number of occurrences the caller wants to claim now.
     */
    public val want: Int,

    /**
     * Handler ids the caller claims for.
     */
    public val handlerIds: Set<String>,

    /**
     * Queue the caller claims from.
     */
    public val queue: JobQueue<*>,
)

/**
 * Creates a [ConcurrencyPool] that admits up to [PoolConfig.capacity] occurrences at once.
 */
public fun pool(
    label: String? = null,
    configure: PoolConfig.() -> Unit = {},
): ConcurrencyPool {
    val config = PoolConfig().apply(configure)
    require(config.capacity > 0) { "Pool capacity must be positive" }
    return SemaphorePool(label, config.capacity)
}

internal fun ConcurrencyPool.describe(): String =
    label?.let { "pool '$it'" } ?: "unlabeled pool"
