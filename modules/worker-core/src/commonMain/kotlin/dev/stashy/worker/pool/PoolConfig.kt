package dev.stashy.worker.pool

/**
 * Settings of the pool created by [pool].
 */
public class PoolConfig internal constructor() {
    /**
     * Maximum occurrences in flight at once.
     */
    public var capacity: Int = Defaults.capacity

    @Suppress("ConstPropertyName")
    public object Defaults {
        public const val capacity: Int = 8
    }
}
